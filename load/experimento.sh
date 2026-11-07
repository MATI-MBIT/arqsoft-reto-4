#!/usr/bin/env bash
# ==============================================================================
# El único orquestador del experimento de Orquídea (ASR-D01, ASR-L04, ASR-S06).
#
# QUÉ se corre es dato (load/plan.tsv); CÓMO se corre es este archivo. Cada
# ejecución sigue el mismo ciclo:
#
#   1. preparar   topología arriba con las variables de la fila, carriles drenados,
#                 Toxiproxy sin fallas, base, custodia y pendientes vacíos
#   2. calentar   la carga de pequeños corre WARMUP_S; nada de esto entra a la medición
#   3. medir      DURACION_S de carga; en los segundos que fija la fila: la caída o la
#                 lentitud del tercero, el restablecimiento, los masivos, el catálogo
#                 criptográfico y los reenvíos
#   4. resolver   espera a los masivos, corre las ventanas nocturnas (abajo/arriba),
#                 repite eventos ya procesados (D-5) y deja drenar los carriles
#   5. cerrar     GRACIA_S, auditoría de la evidencia (S-3)
#   6. veredicto  analisis/<asr>.sql sobre el registro de eventos, CSV crudo, enlaces
#                 al tablero fijados en la ventana de la ejecución y chequeo de validez
#
# Uso:  ./load/experimento.sh humo            # el humo (~12 min)
#       ./load/experimento.sh d01 l04 s06     # los grupos de decisión
#       PLAN=otro.tsv ./load/experimento.sh d01
# ==============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/deploy/docker-compose.yml")
PLAN="${PLAN:-$ROOT/load/plan.tsv}"
STAMP="${STAMP:-$(date +%Y%m%d-%H%M%S)}"
RESULTADOS="$ROOT/analisis/resultados"
RESUMEN="$RESULTADOS/resumen-$STAMP.tsv"
mkdir -p "$RESULTADOS"

export K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://localhost:9090/api/v1/write}"
export K6_PROMETHEUS_RW_TREND_STATS="${K6_PROMETHEUS_RW_TREND_STATS:-p(50),p(95),p(99),max}"
export K6_PROMETHEUS_RW_PUSH_INTERVAL="${K6_PROMETHEUS_RW_PUSH_INTERVAL:-5s}"
K6_OUT=(-o experimental-prometheus-rw)

# Una ejecución durante la cual el equipo se suspende queda contaminada: la VM de
# Docker se congela y los relojes saltan (pasó el 2026-11-07 con un masivo, que
# midió 10 min en vez de 105 s). caffeinate evita la suspensión por inactividad,
# pero no con batería: con batería no se arranca, salvo FORZAR_BATERIA=1.
if command -v pmset >/dev/null && pmset -g batt | grep -q "Battery Power" && [ "${FORZAR_BATERIA:-0}" != 1 ]; then
  echo "El equipo está con batería y macOS lo suspenderá a mitad de la ejecución." >&2
  echo "Conéctalo a la corriente, o corre con FORZAR_BATERIA=1 bajo tu propio riesgo." >&2
  exit 1
fi
command -v caffeinate >/dev/null && caffeinate -dims -w $$ &

# Hueco máximo admitido entre dos archivos recibidos de la carga de fondo (~6,7
# por segundo). Uno mayor solo sale si la máquina se congeló.
HUECO_MAX_S="${HUECO_MAX_S:-30}"
PUERTOS="8081 8082 8083 8084 8085 8090 8091 8092"

log() { printf '\033[1m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"; }

sql() { "${COMPOSE[@]}" exec -T postgres psql -U reto4 -d reto4 -qtA -v ON_ERROR_STOP=1 "$@"; }

esperar_salud() { # puerto
  for _ in $(seq 1 120); do
    curl -fs -m 1 "http://localhost:$1/actuator/health" >/dev/null && return 0
    sleep 1
  done
  log "el servicio del puerto $1 no respondió en 120 s"; return 1
}

# Archivos en proceso en los carriles y el reprocesador, y mensajes sin consumir.
en_curso() {
  local s=0 p v
  for p in 8082 8083 8085; do
    v=$(curl -s -m 2 "localhost:$p/actuator/prometheus" | awk '/^orquidea_procesamiento_en_curso/ {s += $2} END {print s + 0}')
    s=$(awk -v a="$s" -v b="${v:-0}" 'BEGIN {print a + b}')
  done
  echo "$s"
}

rezago() {
  local total=0 g v
  for g in carril-a carril-b; do
    v=$(docker exec reto4-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
          --describe --group "$g" 2>/dev/null | awk '$6 ~ /^[0-9]+$/ {s += $6} END {print s + 0}')
    total=$((total + ${v:-0}))
  done
  echo "$total"
}

esperar_drenado() { # segundos-máximos
  local t=0
  while [ "$t" -lt "$1" ]; do
    if [ "$(en_curso)" = 0 ] && [ "$(rezago)" = 0 ]; then return 0; fi
    sleep 5; t=$((t + 5))
  done
  log "  los carriles no drenaron en $1 s (en curso: $(en_curso), rezago: $(rezago))"
  return 1
}

esperar_monitor() { # tercero estado
  for _ in $(seq 1 60); do
    curl -s -m 1 localhost:8084/estado | grep -q "\"$1\":\"$2\"" && return 0
    sleep 1
  done
  log "  el monitor no marcó $1 como $2 en 60 s"; return 1
}

preparar() {
  # Las variables de la fila que cambian la topología: compose recrea solo los
  # contenedores cuyo entorno cambió.
  export RUTEO COSTO_REGLA_US DIAS_REINTENTO="$DIAS"
  "${COMPOSE[@]}" up -d >/dev/null 2>&1
  for p in $PUERTOS; do esperar_salud "$p" || return 1; done
  curl -fs -X POST localhost:8474/reset >/dev/null
  esperar_drenado 1200 || return 1
  sql -c "SELECT operacion.reiniciar()" >/dev/null
  curl -fs -X POST localhost:8081/admin/reiniciar >/dev/null
  curl -fs -X POST localhost:8085/admin/reiniciar >/dev/null
  esperar_monitor rnic DISPONIBLE && esperar_monitor ecd DISPONIBLE
}

falla() { # dir
  if [ "$MODO" = lentitud ]; then
    log "  falla: el $TERCERO responde con ${LATENCIA_MS} ms de latencia"
    docker exec reto4-inyector inyectar lentitud "$TERCERO" "$LATENCIA_MS" >>"$1/inyector.log" 2>&1
  else
    log "  falla: se corta el $TERCERO"
    docker exec reto4-inyector inyectar cortar "$TERCERO" >>"$1/inyector.log" 2>&1
  fi
  if [ "$VENCER" = 1 ]; then
    curl -fs -X POST localhost:8081/admin/revocacion/vencer >/dev/null
    log "  la copia local de la CRL queda vencida"
  fi
  touch "$1/.falla-activa"
}

restablecer() { # dir
  [ -f "$1/.falla-activa" ] || return 0
  log "  se restablece el $TERCERO"
  docker exec reto4-inyector inyectar restablecer "$TERCERO" >>"$1/inyector.log" 2>&1
  rm -f "$1/.falla-activa"
}

catalogo() { # dir corrida
  local casos="${CASOS:-firma-alterada,resumen-recalculado,firma-envuelta,firmante-distinto,revocado,ca-no-aceptada,estampa-desfasada,estampa-vencida,entidad-externa}"
  log "  catálogo criptográfico: $CATALOGO por caso ($casos)"
  for c in $(echo "$casos" | tr ',' ' '); do
    printf '%s ' "$c" >>"$1/catalogo.txt"
    curl -s -m 120 -X POST "localhost:8090/enviar-lote?n=$CATALOGO&caso=$c&corrida=$2" >>"$1/catalogo.txt"
    echo >>"$1/catalogo.txt"
  done
}

reenviar() { # dir corrida
  log "  D-5: se reenvían $REENVIAR pendientes con la misma llave y $NUEVOS archivos nuevos"
  { printf 'reenvios '; curl -s -m 120 -X POST "localhost:8090/reenviar?n=$REENVIAR"; echo
    printf 'nuevos '; curl -s -m 120 -X POST "localhost:8090/enviar-lote?n=$NUEVOS&corrida=$2"; echo; } >>"$1/reenvios.txt"
}

masivos() { # dir corrida
  log "  se lanzan $MASIVOS masivos"
  (cd "$ROOT/load/k6" && k6 run -q "${K6_OUT[@]}" --tag corrida="$2" --tag guion=masivos \
      -e CORRIDA="$2" -e MASIVOS="$MASIVOS" masivos.js >"$1/k6-masivos.txt" 2>&1)
}

ventana() { # dir n tipo
  log "  ventana nocturna $2 ($3)"
  curl -fs -X POST localhost:8085/ventanas >/dev/null
  for _ in $(seq 1 1200); do
    if curl -s -m 2 localhost:8085/ventanas/actual | grep -q '"TERMINADA"'; then
      curl -s localhost:8085/ventanas/actual >>"$1/ventanas.txt"; echo >>"$1/ventanas.txt"
      return 0
    fi
    sleep 1
  done
  log "  la ventana $2 no terminó en 20 min"; return 1
}

correr() { # grupo id asr escenario criterio vars pregunta
  local grupo="$1" id="$2" asr="$3" escenario="$4" criterio="$5" vars="$6" pregunta="$7"

  WARMUP_S=60 DURACION_S=600 TASA=400 GRACIA_S=30 TERCERO=rnic CAIDA_EN_S=-1 MODO=corte LATENCIA_MS=3000
  RESTABLECER_EN_S=-1 VENCER=0 VENTANAS="" REENVIAR=0 NUEVOS=0 REENVIAR_EN_S=-1 REPETIR=0
  MASIVOS=0 MASIVOS_EN_S=120 RUTEO=tamano CATALOGO=0 CATALOGO_EN_S=60 CASOS="" DIAS=2 COSTO_REGLA_US=1000
  if [ "$vars" != "-" ]; then
    IFS=';' read -ra pares <<<"$vars"
    for kv in "${pares[@]}"; do printf -v "${kv%%=*}" '%s' "${kv#*=}"; done
  fi

  local corrida="$id-$STAMP" dir="$RESULTADOS/$id-$STAMP"
  mkdir -p "$dir"
  log "== $corrida · ASR-$asr $escenario · $pregunta"
  preparar || { log "no se pudo preparar la topología"; return 1; }

  local variables
  variables=$(printf '{"escenario":"%s","dias":%s,"ventanas":"%s","masivos":%s,"ruteo":"%s","tasa":%s,"tercero":"%s","modo":"%s","costo_regla_us":%s,"vars":"%s"}' \
    "$escenario" "$DIAS" "$VENTANAS" "$MASIVOS" "$RUTEO" "$TASA" "$TERCERO" "$MODO" "$COSTO_REGLA_US" "$vars")
  sql -c "INSERT INTO registro.corrida (id, asr, escenario, variables, arranque)
          VALUES ('$corrida', '$asr', '$escenario', '$variables'::jsonb, clock_timestamp())"

  # La carga de fondo del Ambiente A corre durante el calentamiento y la medición.
  (cd "$ROOT/load/k6" && exec k6 run "${K6_OUT[@]}" --tag corrida="$corrida" --tag guion=pequenos \
      -e CORRIDA="$corrida" -e TASA="$TASA" -e DURACION="$((WARMUP_S + DURACION_S))s" pequenos.js \
      >"$dir/k6-pequenos.txt" 2>&1) &
  local carga=$!

  log "  calentamiento: ${WARMUP_S} s"
  sleep "$WARMUP_S"
  sql -c "UPDATE registro.corrida SET inicio = clock_timestamp() WHERE id = '$corrida'"
  log "  medición: ${DURACION_S} s"

  # Lo que pasa en mitad de la medición, en el segundo que fija la fila.
  local tareas=""
  if [ "$CAIDA_EN_S" -ge 0 ]; then (sleep "$CAIDA_EN_S"; falla "$dir") & tareas="$tareas $!"; fi
  if [ "$RESTABLECER_EN_S" -ge 0 ]; then (sleep "$RESTABLECER_EN_S"; restablecer "$dir") & tareas="$tareas $!"; fi
  if [ "$MASIVOS" -gt 0 ]; then (sleep "$MASIVOS_EN_S"; masivos "$dir" "$corrida") & tareas="$tareas $!"; fi
  if [ "$CATALOGO" -gt 0 ]; then (sleep "$CATALOGO_EN_S"; catalogo "$dir" "$corrida") & tareas="$tareas $!"; fi
  if [ "$REENVIAR_EN_S" -ge 0 ]; then (sleep "$REENVIAR_EN_S"; reenviar "$dir" "$corrida") & tareas="$tareas $!"; fi

  wait "$carga"
  sql -c "UPDATE registro.corrida SET fin = clock_timestamp() WHERE id = '$corrida'"
  for t in $tareas; do wait "$t"; done

  if [ "$MASIVOS" -gt 0 ]; then
    log "  esperando el resultado de los masivos"
    for _ in $(seq 1 240); do
      [ "$(sql -c "SELECT count(*) FROM operacion.archivo WHERE registros > 50 AND resultado IS NULL")" = 0 ] && break
      sleep 5
    done
  fi

  # Las ventanas: «abajo» con el tercero todavía caído, «arriba» con él de vuelta.
  local n=0
  for v in $(echo "$VENTANAS" | tr ',' ' '); do
    n=$((n + 1))
    if [ "$v" = arriba ]; then
      restablecer "$dir"
      esperar_monitor "$TERCERO" DISPONIBLE
    fi
    sleep 2   # el monitor cliente de cada servicio consulta cada 200 ms
    ventana "$dir" "$n" "$v"
  done
  restablecer "$dir"
  esperar_drenado 1200

  if [ "$REPETIR" -gt 0 ]; then
    log "  D-5: se repiten $REPETIR eventos de archivos ya procesados"
    docker exec reto4-inyector inyectar repetir "$REPETIR" >>"$dir/inyector.log" 2>&1
    sleep 5
    esperar_drenado 600
  fi

  log "  gracia: ${GRACIA_S} s"
  sleep "$GRACIA_S"
  curl -s -X POST localhost:8081/evidencia/auditar >"$dir/evidencia.json"
  sleep 1   # el registro de cada servicio se vacía cada 200 ms
  sql -c "UPDATE registro.corrida SET cierre = clock_timestamp() WHERE id = '$corrida'"

  local script; script=$(echo "$asr" | tr 'A-Z' 'a-z')
  "${COMPOSE[@]}" exec -T postgres psql -U reto4 -d reto4 -v corrida="$corrida" \
      -f "/analisis/$script.sql" >"$dir/veredicto.txt" 2>&1
  sql -c "\copy (SELECT e.* FROM registro.evento e, registro.corrida c WHERE c.id = '$corrida' AND e.ts BETWEEN c.arranque AND c.cierre ORDER BY e.ts) TO STDOUT WITH CSV HEADER" >"$dir/eventos-$corrida.csv"
  cp "$PLAN" "$dir/plan.tsv"
  rm -f "$dir/.falla-activa"

  # Enlaces a los tableros fijados en la ventana de la ejecución: la evidencia visual
  # se puede volver a abrir mientras Prometheus conserve la serie (15 días).
  local desde hasta
  desde=$(sql -c "SELECT (extract(epoch FROM arranque) * 1000)::bigint FROM registro.corrida WHERE id = '$corrida'")
  hasta=$(sql -c "SELECT (extract(epoch FROM cierre) * 1000)::bigint FROM registro.corrida WHERE id = '$corrida'")
  printf 'tablero  http://localhost:3000/d/%s?from=%s&to=%s\nkiosco   http://localhost:3000/d/%s?from=%s&to=%s&kiosk\n' \
    "$script" "$desde" "$hasta" "$script" "$desde" "$hasta" >"$dir/tablero.txt"

  # Validez: un hueco en la carga, una suspensión del equipo o más de 1 % de
  # solicitudes fallidas en k6 dejan la ejecución contaminada.
  local hueco suspensiones=0 fallidas epoch_arranque
  hueco=$(sql -c "SELECT coalesce(round(extract(epoch FROM max(h))::numeric, 1), 9999) FROM (
                    SELECT e.ts - lag(e.ts) OVER (ORDER BY e.ts) AS h FROM registro.evento e, registro.corrida c
                    WHERE c.id = '$corrida' AND e.tipo = 'archivo.recibido' AND e.ts BETWEEN c.inicio AND c.fin) x")
  [ -n "$hueco" ] || hueco=9999
  epoch_arranque=$(sql -c "SELECT extract(epoch FROM arranque)::bigint FROM registro.corrida WHERE id = '$corrida'")
  if command -v pmset >/dev/null && [ -n "$epoch_arranque" ]; then
    suspensiones=$(pmset -g log | awk -v a="$(date -r "$epoch_arranque" '+%Y-%m-%d %H:%M:%S')" -v b="$(date '+%Y-%m-%d %H:%M:%S')" \
      'substr($0, 1, 19) >= a && substr($0, 1, 19) <= b && /Entering Sleep/' | wc -l | tr -d ' ')
  fi
  fallidas=$(grep -h 'http_req_failed' "$dir/k6-pequenos.txt" 2>/dev/null \
    | awk '{ for (i = 1; i <= NF; i++) if ($i ~ /%$/) { gsub("%", "", $i); v = $i + 0; if (v > m) m = v } } END { print m + 0 }')
  if [ "$suspensiones" -gt 0 ] || awk -v h="$hueco" -v m="$HUECO_MAX_S" -v f="$fallidas" 'BEGIN { exit !(h > m || f > 1) }'; then
    sql -c "UPDATE registro.corrida SET valida = false, motivo = 'hueco de $hueco s en la carga, $suspensiones suspensiones, $fallidas % de solicitudes fallidas' WHERE id = '$corrida'"
    mv "$dir" "$dir-contaminada"
    log "  CONTAMINADA: hueco de ${hueco} s, $suspensiones suspensiones, ${fallidas} % fallidas. Se descarta: ${dir#"$ROOT"/}-contaminada"
    return 2
  fi
  echo "validez: hueco máximo ${hueco} s · ${suspensiones} suspensiones · ${fallidas} % de solicitudes fallidas" >>"$dir/veredicto.txt"

  grep -E '\| (PASA|FALLA|OBSERVA|NO APLICA) *$' "$dir/veredicto.txt" | while IFS= read -r linea; do
    printf '%s\t%s\t%s\t%s\n' "$corrida" "$criterio" "$escenario" "$linea" >>"$RESUMEN"
  done
  log "  veredicto en ${dir#"$ROOT"/}/veredicto.txt"
  grep -E '\| (PASA|FALLA|OBSERVA|NO APLICA) *$' "$dir/veredicto.txt" | sed 's/^/     /'
}

GRUPOS="$*"
pertenece() {
  [ -z "$GRUPOS" ] && { [ "$1" != humo ] && [ "$1" != rapido ]; return; }
  for g in $GRUPOS; do [ "$g" = "$1" ] && return 0; done
  return 1
}

# Una ejecución contaminada se repite una vez, sola, con un sello nuevo.
log "resultados en analisis/resultados/ · resumen en ${RESUMEN#"$ROOT"/}"
while IFS=$'\t' read -r -u 3 grupo id asr escenario criterio vars pregunta; do
  [[ -z "$grupo" || "$grupo" == \#* ]] && continue
  pertenece "$grupo" || continue
  correr "$grupo" "$id" "$asr" "$escenario" "$criterio" "$vars" "$pregunta"
  if [ $? -eq 2 ]; then
    STAMP="$STAMP-r1"
    correr "$grupo" "$id" "$asr" "$escenario" "$criterio" "$vars" "$pregunta"
    STAMP="${STAMP%-r1}"
  fi
done 3< <(grep -v '^#' "$PLAN")

log "listo. Resumen:"
[ -f "$RESUMEN" ] && column -t -s$'\t' "$RESUMEN"
