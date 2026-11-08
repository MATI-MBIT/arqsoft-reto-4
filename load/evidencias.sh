#!/usr/bin/env bash
# ==============================================================================
# Casos de demostración para grabar la evidencia en video. Cada caso prepara la
# topología como el orquestador, corre una línea de tiempo corta (~75 s) con la
# carga de pequeños de fondo y deja en evidencias.log la hora de cada estímulo.
# Mientras corre, la pantalla de evidencia del ASR (ev-d01, ev-l04, ev-s06) se
# graba en el navegador. Es una demostración, no una medición: las cifras de
# docs/results.md salen de las ejecuciones de decisión de load/plan.tsv.
#
#   load/evidencias.sh preparar [tamano|unico]   # topología lista y vacía
#   load/evidencias.sh caida                     # ASR-D01: el RNIC cae, pendientes, ventanas, regreso
#   load/evidencias.sh masivos                   # ASR-L04: 5 masivos con la carga de pequeños
#   load/evidencias.sh ecd                       # ASR-S06: catálogo y ECD caída con la CRL vencida
#   load/evidencias.sh grabar <caso> [ruteo]     # prepara, corre el caso y arma su clip en
#                                                # docs/assets/resultados/clips/<caso>[-unico].mp4
#
# El clip es un time-lapse: el renderizador de Grafana toma la pantalla de evidencia
# cada 2,5 s en el servidor (no depende de un navegador abierto) y
# analisis/evidencia/video.swift la arma con una franja que dice el segundo del caso
# y el último estímulo.
# ==============================================================================
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/deploy/docker-compose.yml")
LOG="${EVIDENCIAS_LOG:-$ROOT/analisis/evidencia/evidencias.log}"
mkdir -p "$(dirname "$LOG")"

t0=0
marca() { # texto
  local s=$(( $(date +%s) - t0 ))
  printf '[%s] +%3ss  %s\n' "$(date +%H:%M:%S)" "$s" "$1" | tee -a "$LOG"
}
sql() { "${COMPOSE[@]}" exec -T postgres psql -U reto4 -d reto4 -qtA -c "$1"; }
inyectar() { docker exec reto4-inyector inyectar "$@" >/dev/null; }
esperar() { # segundo-relativo
  while [ $(( $(date +%s) - t0 )) -lt "$1" ]; do sleep 0.5; done
}
esperar_monitor() { # tercero estado
  for _ in $(seq 1 30); do curl -s -m 1 localhost:8084/estado | grep -q "\"$1\":\"$2\"" && return 0; sleep 1; done
}
ventana() { # descripción
  marca "abre la ventana nocturna ($1)"
  curl -fs -X POST localhost:8085/ventanas >/dev/null
  for _ in $(seq 1 120); do curl -s localhost:8085/ventanas/actual | grep -q TERMINADA && break; sleep 0.5; done
  marca "$(curl -s localhost:8085/ventanas/actual | python3 -c 'import sys,json; d=json.load(sys.stdin); print("la ventana %s cierra: %s resueltos · %s reprogramados · %s liberados · %s rechazados" % (d["numero"], d["resueltos"], d["reprogramados"], d["liberados"], d["rechazados"]))')"
}
carga() { # segundos corrida
  (cd "$ROOT/load/k6" && k6 run -q -e CORRIDA="$2" -e TASA=400 -e DURACION="${1}s" pequenos.js >/dev/null 2>&1) &
}

preparar() { # ruteo
  export RUTEO="${1:-tamano}"
  "${COMPOSE[@]}" up -d >/dev/null 2>&1
  for p in 8081 8082 8083 8084 8085 8090; do
    for _ in $(seq 1 120); do curl -fs -m 1 "localhost:$p/actuator/health" >/dev/null && break; sleep 1; done
  done
  curl -fs -X POST localhost:8474/reset >/dev/null
  # Que no quede trabajo de un caso anterior (un masivo dura más de un minuto).
  for _ in $(seq 1 600); do
    [ "$(sql "SELECT count(*) FROM operacion.archivo WHERE resultado IS NULL AND estado = 'ACUSADO'")" = 0 ] && break; sleep 1
  done
  sql "SELECT operacion.reiniciar()" >/dev/null
  curl -fs -X POST localhost:8081/admin/reiniciar >/dev/null
  curl -fs -X POST localhost:8085/admin/reiniciar >/dev/null
  esperar_monitor rnic DISPONIBLE; esperar_monitor ecd DISPONIBLE
  echo "listo · ruteo $RUTEO"
}


grabar() { # caso ruteo
  local caso="$1" ruteo="${2:-tamano}" uid titulo
  case "$caso" in
    caida) uid=ev-d01; titulo="ASR-D01 · el RNIC cae y nada se rechaza" ;;
    masivos) uid=ev-l04; titulo="ASR-L04 · 5 masivos, ruteo $ruteo" ;;
    ecd) uid=ev-s06; titulo="ASR-S06 · catálogo y ECD caída" ;;
    *) echo "caso desconocido: $caso"; exit 2 ;;
  esac
  local nombre="$caso"; [ "$ruteo" = unico ] && nombre="$caso-sin-carriles"
  local cuadros; cuadros=$(mktemp -d)
  local log="$ROOT/analisis/evidencia/$nombre.log"
  rm -f "$log"
  preparar "$ruteo"
  # Que la ventana de 90 s de la pantalla no muestre nada de un caso anterior.
  until [ "$(sql "SELECT coalesce(extract(epoch FROM now() - max(ts))::int > 95, true) FROM registro.evento")" = t ]; do sleep 3; done
  local url="localhost:3000/render/d/$uid?from=now-90s&to=now&width=1600&height=1000&kiosk=true&tz=America%2FBogota&timeout=30&_dash.hideTimePicker&_dash.hideVariables&_dash.hideLinks"
  ( while true; do
      ( curl -s -m 40 -o "$cuadros/$(python3 -c 'import time; print(int(time.time()*1000))').png" "$url" ) &
      sleep 2.5
    done ) &
  local capturador=$!
  sleep 2
  EVIDENCIAS_LOG="$log" RUTEO="$ruteo" "$0" "$caso"
  sleep 6
  kill "$capturador" 2>/dev/null; wait "$capturador" 2>/dev/null; sleep 8
  find "$cuadros" -name '*.png' -size -20k -delete   # cuadros incompletos
  local t0; t0=$(grep -o 't0=[0-9]*' "$log" | head -1 | cut -d= -f2)
  mkdir -p "$ROOT/docs/assets/resultados/clips"
  swift "$ROOT/analisis/evidencia/video.swift" "$cuadros" "$log" "$t0" "$titulo" \
    "$ROOT/docs/assets/resultados/clips/$nombre.mp4" 1 2>&1 | grep -E '^clip|error'
  echo "cuadros en $cuadros"
}

caso="${1:-}"
corrida="evidencia-$caso-$(date +%H%M%S)"
case "$caso" in
  preparar) preparar "${2:-tamano}"; exit 0 ;;
  grabar) grabar "${2:-}" "${3:-tamano}"; exit 0 ;;
  caida)
    t0=$(date +%s); echo "== $corrida · el RNIC cae y vuelve · t0=$t0" | tee -a "$LOG"
    carga 75 "$corrida"; marca "arranca la carga de pequeños: 400 por minuto"
    esperar 8;  marca "⚡ se corta el RNIC"; inyectar cortar rnic
    esperar 33; ventana "el RNIC sigue caído"
    esperar 42; marca "✔ se restablece el RNIC"; inyectar restablecer rnic; esperar_monitor rnic DISPONIBLE
    esperar 48; ventana "el RNIC está de vuelta"
    esperar 75; marca "fin del caso" ;;
  masivos)
    t0=$(date +%s); echo "== $corrida · 5 masivos con ruteo ${RUTEO:-tamano} · t0=$t0" | tee -a "$LOG"
    carga 75 "$corrida"; marca "arranca la carga de pequeños: 400 por minuto"
    esperar 8; marca "⚡ se lanzan 5 masivos de 49 MB"
    (cd "$ROOT/load/k6" && k6 run -q -e CORRIDA="$corrida" -e MASIVOS=5 masivos.js >/dev/null 2>&1) &
    esperar 75; marca "fin del caso" ;;
  ecd)
    t0=$(date +%s); echo "== $corrida · catálogo y ECD caída · t0=$t0" | tee -a "$LOG"
    carga 80 "$corrida"; marca "arranca la carga de pequeños: 400 por minuto"
    esperar 6; marca "⚡ se envía el catálogo criptográfico: un archivo por caso"
    for c in firma-alterada resumen-recalculado firma-envuelta firmante-distinto ca-no-aceptada estampa-desfasada estampa-vencida entidad-externa revocado; do
      curl -s -m 10 -X POST "localhost:8090/enviar?caso=$c&corrida=$corrida&registros=5" >/dev/null
    done
    esperar 20; marca "⚡ se corta la ECD y se vence la copia local de la CRL"
    inyectar cortar ecd; curl -fs -X POST localhost:8081/admin/revocacion/vencer >/dev/null
    esperar 30; marca "⚡ llega un archivo con certificado revocado mientras la ECD está caída"
    curl -s -m 10 -X POST "localhost:8090/enviar?caso=revocado&corrida=$corrida&registros=5" >/dev/null
    esperar 45; ventana "la ECD sigue caída"
    esperar 52; marca "✔ se restablece la ECD"; inyectar restablecer ecd
    esperar 56; ventana "la ECD está de vuelta"
    esperar 80; marca "fin del caso" ;;
  *) sed -n '2,15p' "$0"; exit 2 ;;
esac
wait
