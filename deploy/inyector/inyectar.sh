#!/usr/bin/env bash
# ==============================================================================
# Inyector de fallas del experimento. Corre dentro del contenedor `inyector`, que
# alcanza a Toxiproxy, a la base y al socket de Docker. El orquestador lo invoca:
#
#   docker exec inyector inyectar cortar <tercero>             # D-1, D-2, D-3, D-5, S-2
#   docker exec inyector inyectar lentitud <tercero> <ms>      # D-4
#   docker exec inyector inyectar restablecer <tercero>
#   docker exec inyector inyectar repetir <n>                  # D-5: repite n eventos ya procesados
#
# Cada falla se anota en registro.evento justo antes de aplicarla: es el t0 de la
# detección. Todo reloj que entra al veredicto es el de un contenedor.
# ==============================================================================
set -euo pipefail
TOXI="${TOXIPROXY:-http://toxiproxy:8474}"

anotar() { # tipo datos-json
  psql -qtA -c "INSERT INTO registro.evento (ts, componente, tipo, datos)
                VALUES (clock_timestamp(), 'inyector', '$1', '$2'::jsonb)" >/dev/null
}

tercero_valido() {
  case "$1" in rnic|ecd) ;; *) echo "tercero desconocido: $1" >&2; exit 2 ;; esac
}

case "${1:-}" in
  cortar)
    t="$2"; tercero_valido "$t"
    anotar falla.inyectada "{\"tipo\":\"corte\",\"tercero\":\"$t\"}"
    curl -fs -X POST "$TOXI/proxies/$t" -H 'Content-Type: application/json' -d '{"enabled":false}' >/dev/null
    ;;
  lentitud)
    t="$2"; ms="${3:-3000}"; tercero_valido "$t"
    anotar falla.inyectada "{\"tipo\":\"lentitud\",\"tercero\":\"$t\",\"latenciaMs\":$ms}"
    curl -fs -X POST "$TOXI/proxies/$t/toxics" -H 'Content-Type: application/json' \
      -d "{\"name\":\"lento\",\"type\":\"latency\",\"stream\":\"downstream\",\"toxicity\":1.0,\"attributes\":{\"latency\":$ms,\"jitter\":0}}" >/dev/null
    ;;
  restablecer)
    # Se anota antes de aplicar, como la falla: el regreso que ve el monitor nunca
    # puede quedar antes de su t0 (en D-4 del 2026-11-07 quedó 8 ms antes).
    t="$2"; tercero_valido "$t"
    anotar falla.restablecida "{\"tercero\":\"$t\"}"
    curl -fs -X POST "$TOXI/proxies/$t" -H 'Content-Type: application/json' -d '{"enabled":true}' >/dev/null
    curl -fs -X DELETE "$TOXI/proxies/$t/toxics/lento" >/dev/null 2>&1 || true
    ;;
  repetir)
    # Vuelve a publicar en el tópico de su carril el mensaje de n archivos que ya
    # tienen resultado, como lo haría la cola al entregar dos veces o un reproceso
    # repetido. Va directo a Kafka, sin pasar por el código de Orquídea.
    n="${2:-50}"
    filas=$(psql -qtA -F $'\t' -c "SELECT llave, carril, bytes, registros FROM operacion.archivo
                                   WHERE resultado IS NOT NULL AND carril = 'carril-a' ORDER BY random() LIMIT $n")
    [ -n "$filas" ] || { echo "no hay archivos resueltos para repetir" >&2; exit 1; }
    mensajes=""
    while IFS=$'\t' read -r llave carril bytes registros; do
      ruta="$(echo "$llave" | tr '|' '_').xml"
      anotar repeticion.inyectada "{\"llave\":\"$llave\"}"
      mensajes+="$llave|{\"llave\":\"$llave\",\"ruta\":\"$ruta\",\"bytes\":$bytes,\"registros\":$registros,\"carril\":\"$carril\"}"$'\n'
    done <<<"$filas"
    # La llave del mensaje es la del archivo; el separador de la consola es «#».
    printf '%s' "$mensajes" | sed 's/|{/#{/' | docker exec -i reto4-kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server kafka:9092 --topic carril-a --property parse.key=true --property 'key.separator=#' >/dev/null
    echo "repetidos: $(printf '%s' "$mensajes" | grep -c .)"
    ;;
  *)
    echo "uso: inyectar cortar|restablecer <tercero> · lentitud <tercero> <ms> · repetir <n>" >&2; exit 2 ;;
esac
