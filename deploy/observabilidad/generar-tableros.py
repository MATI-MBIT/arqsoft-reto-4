#!/usr/bin/env python3
"""Genera los tableros de Grafana de ASR-D01, ASR-L04 y ASR-S06 (make tableros).

Cada tablero tiene dos fuentes:
  - el registro de eventos (PostgreSQL): cada archivo cruzado con su desenlace por
    la llave, con las mismas vistas que usa el veredicto. Es la evidencia.
  - Prometheus: lo que miden los servicios y k6, para ver la ejecución en vivo y
    cortarla si se daña.

Las cifras de arriba de cada tablero se calculan sobre la ventana de tiempo del
tablero. El veredicto de una ejecución se calcula sobre su propia ventana, con
analisis/*.sql; el orquestador deja en tablero.txt el enlace fijado en ella.
"""
import json
from pathlib import Path

DESTINO = Path(__file__).parent / "grafana" / "dashboards"
PROM = {"type": "prometheus", "uid": "prometheus"}
PG = {"type": "grafana-postgresql-datasource", "uid": "registro"}
ROJO, VERDE, NARANJA, AZUL, MORADO = "red", "green", "orange", "blue", "purple"

# Filtro común: los archivos enviados en la ventana del tablero.
EN_VENTANA = "$__timeFilter(enviado)"


class Tablero:
    def __init__(self):
        self.paneles, self.y, self.x, self.alto = [], 0, 0, 0

    def _ubicar(self, w, h):
        if self.x + w > 24:
            self.y += self.alto
            self.x, self.alto = 0, 0
        pos = {"x": self.x, "y": self.y, "w": w, "h": h}
        self.x += w
        self.alto = max(self.alto, h)
        return pos

    def fila(self, titulo):
        if self.x:
            self.y += self.alto
        self.x, self.alto = 0, 0
        self.paneles.append({"type": "row", "title": titulo, "collapsed": False,
                             "gridPos": {"x": 0, "y": self.y, "w": 24, "h": 1}, "panels": []})
        self.y += 1

    def panel(self, tipo, titulo, targets, w, h, descripcion="", **extra):
        p = {"type": tipo, "title": titulo, "description": descripcion, "gridPos": self._ubicar(w, h),
             "targets": targets, "datasource": targets[0]["datasource"]}
        p.update(extra)
        self.paneles.append(p)

    def json(self, uid, titulo, descripcion, anotaciones):
        for i, p in enumerate(self.paneles, start=1):
            p["id"] = i
        return {"uid": uid, "title": titulo, "description": descripcion, "tags": ["reto4", uid],
                "timezone": "browser", "refresh": "5s", "schemaVersion": 39, "version": 1,
                "time": {"from": "now-30m", "to": "now"}, "annotations": {"list": anotaciones},
                "panels": self.paneles, "templating": {"list": []}}


def sql(consulta, formato="table", ref="A"):
    return {"refId": ref, "datasource": PG, "rawSql": consulta.strip(), "format": formato,
            "rawQuery": True, "editorMode": "code"}


def prom(expr, leyenda="", ref="A"):
    return {"refId": ref, "datasource": PROM, "expr": expr, "legendFormat": leyenda, "range": True}


def umbrales(*pasos):
    return {"mode": "absolute", "steps": [{"value": v, "color": c} for v, c in pasos]}


def stat(t, titulo, consulta, pasos, descripcion="", unidad="none", w=4):
    t.panel("stat", titulo, [sql(consulta)], w, 4, descripcion,
            fieldConfig={"defaults": {"thresholds": umbrales(*pasos), "unit": unidad, "noValue": "0"}, "overrides": []},
            options={"colorMode": "background", "graphMode": "none", "reduceOptions": {"calcs": ["lastNotNull"]}})


def serie(t, titulo, targets, w=12, h=8, descripcion="", unidad="none", **campos):
    defaults = {"unit": unidad, "custom": {"lineWidth": 2, "fillOpacity": 10, "showPoints": "never"}}
    defaults.update(campos)
    t.panel("timeseries", titulo, targets, w, h, descripcion, fieldConfig={"defaults": defaults, "overrides": []},
            options={"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}})


def tabla(t, titulo, consulta, w=12, h=8, descripcion=""):
    t.panel("table", titulo, [sql(consulta)], w, h, descripcion, options={"showHeader": True})


def anotacion(nombre, tipo, color, texto="datos::text"):
    return {"name": nombre, "datasource": PG, "enable": True, "iconColor": color,
            "rawQuery": f"SELECT extract(epoch FROM ts) * 1000 AS time, '{nombre}: ' || {texto} AS text "
                        f"FROM registro.evento WHERE tipo = '{tipo}' AND $__timeFilter(ts) ORDER BY ts",
            "target": {"rawSql": f"SELECT ts AS time, '{nombre}: ' || {texto} AS text FROM registro.evento "
                                 f"WHERE tipo = '{tipo}' AND $__timeFilter(ts) ORDER BY ts", "format": "table"}}


COMUNES = [
    anotacion("Falla inyectada", "falla.inyectada", ROJO),
    anotacion("Tercero restablecido", "falla.restablecida", VERDE),
    anotacion("Ventana nocturna", "ventana.inicio", AZUL),
    anotacion("Monitor: caído", "tercero.caido", NARANJA, "(datos->>'tercero')"),
    anotacion("Monitor: de vuelta", "tercero.de_vuelta", MORADO, "(datos->>'tercero')"),
]


def d01():
    t = Tablero()
    t.fila("Veredicto en vivo de ASR-D01 (registro de eventos, ventana del tablero)")
    base = f"FROM registro.archivos WHERE {EN_VENTANA} AND caso = 'valido'"
    stat(t, "Válidos enviados", f"SELECT count(*) {base} AND esperado IN ('TOTAL','PARCIAL')", [(None, AZUL)])
    stat(t, "Válidos procesados", f"SELECT count(*) {base} AND esperado IN ('TOTAL','PARCIAL') AND resultado = esperado AND rechazadas = invalidas",
         [(None, VERDE)], "Con resultado definitivo igual al que habrían tenido con el RNIC disponible")
    stat(t, "Pendientes ahora", "SELECT count(*) FROM operacion.pendiente WHERE estado = 'PENDIENTE'", [(None, VERDE), (1, NARANJA)],
         "Archivos en espera durable de la ventana nocturna (ADR-003)")
    stat(t, "Rechazados por la caída", f"SELECT count(*) {base} AND esperado IN ('TOTAL','PARCIAL') AND resultado IS NOT NULL AND (resultado <> esperado OR rechazadas <> invalidas)",
         [(None, VERDE), (1, ROJO)], "Debe ser 0: la caída nunca produce un rechazo")
    stat(t, "Resultados escritos dos veces", f"SELECT count(*) {base} AND resultados > 1", [(None, VERDE), (1, ROJO)],
         "Debe ser 0: la llave de idempotencia (ADR-004)")
    stat(t, "Notificados al remitente", f"SELECT count(*) {base} AND notificado IS NOT NULL", [(None, AZUL)],
         "Pendientes que agotaron los días de reintento: se notifica y siguen pendientes")

    t.fila("El recorrido de los archivos")
    serie(t, "Archivos por cada 10 s: recibidos, resueltos en carril, en pendientes y resueltos en ventana", [
        sql("SELECT $__timeGroupAlias(ts, '10s'), count(*) AS \"recibidos\" FROM registro.evento WHERE tipo = 'archivo.recibido' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "A"),
        sql("SELECT $__timeGroupAlias(ts, '10s'), count(*) AS \"resueltos en carril\" FROM registro.evento WHERE tipo = 'archivo.resultado' AND datos->>'componente' LIKE 'carril%' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "B"),
        sql("SELECT $__timeGroupAlias(ts, '10s'), count(*) AS \"a pendientes\" FROM registro.evento WHERE tipo = 'archivo.pendiente' AND datos->>'componente' <> 'reprocesador' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "C"),
        sql("SELECT $__timeGroupAlias(ts, '10s'), count(*) AS \"resueltos en ventana\" FROM registro.evento WHERE tipo = 'archivo.resultado' AND datos->>'componente' = 'reprocesador' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "D"),
    ], 16, 9, "Con el RNIC caído, la recepción sigue acusando y lo que necesita al RNIC va a pendientes; la ventana lo resuelve")
    serie(t, "Estado de cada tercero según el monitor (0 disponible · 1 de vuelta · 2 caído)",
          [prom("max by (tercero) (orquidea_tercero_estado)", "{{tercero}}")], 8, 9,
          "Ping/echo cada SONDEO_MS; caído con SONDEO_N fallos seguidos (ADR-002)", min=0, max=2)
    serie(t, "Pendientes en espera (Prometheus)", [prom("max(orquidea_pendientes)", "pendientes")], 8, 7)
    serie(t, "Llamadas vencidas del adaptador del RNIC por cada 10 s", [
        sql("SELECT $__timeGroupAlias(ts, '10s'), count(*) AS \"vencidas\" FROM registro.evento WHERE tipo = 'adaptador.vencida' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series")],
          8, 7, "Timeout por llamada (DIS-04): la lentitud cuenta como caída")
    serie(t, "Acuses por segundo según su estado", [prom("sum by (estado) (rate(orquidea_acuses_total[30s]))", "{{estado}}")], 8, 7)

    t.fila("Detección y ventanas")
    tabla(t, "Fallas inyectadas: de la falla al cambio de estado en el monitor",
          "SELECT falla, tipo, tercero, deteccion_ms, llamadas_antes_de_detectar, restablecida, regreso_ms FROM registro.fallas WHERE $__timeFilter(falla) ORDER BY falla", 12, 7)
    tabla(t, "Ventanas nocturnas",
          "SELECT numero, rnic_al_abrir, inicio, fin - inicio AS duracion, desenlace->>'resueltos' AS resueltos, desenlace->>'reprogramados' AS reprogramados, desenlace->>'notificados' AS notificados FROM registro.ventanas WHERE $__timeFilter(inicio) ORDER BY inicio", 12, 7)
    return t.json("d01", "ASR-D01 · Ningún archivo se rechaza por la caída de un sistema externo",
                  "Detección (ADR-002), pendientes y ventana nocturna (ADR-003), idempotencia (ADR-004)", COMUNES)


def l04():
    t = Tablero()
    t.fila("Veredicto en vivo de ASR-L04 (registro de eventos, ventana del tablero)")
    peq = f"FROM registro.archivos WHERE {EN_VENTANA} AND tipo = 'pequeno' AND caso = 'valido' AND latencia_ms IS NOT NULL"
    stat(t, "p99 de los pequeños", f"SELECT percentile_cont(0.99) WITHIN GROUP (ORDER BY latencia_ms) {peq}",
         [(None, VERDE), (3000, ROJO)], "Del último byte al resultado definitivo. Umbral: 3 s", "ms")
    stat(t, "p50 de los pequeños", f"SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY latencia_ms) {peq}", [(None, AZUL)], unidad="ms")
    stat(t, "Pequeños resueltos", f"SELECT count(*) {peq}", [(None, AZUL)])
    stat(t, "Pequeños sobre 3 s", f"SELECT count(*) {peq} AND latencia_ms > 3000", [(None, VERDE), (1, NARANJA)])
    stat(t, "Masivos en proceso", f"SELECT count(*) FROM registro.archivos WHERE {EN_VENTANA} AND tipo = 'masivo' AND resultado_en IS NULL",
         [(None, AZUL)])
    stat(t, "p99 del acuse", f"SELECT percentile_cont(0.99) WITHIN GROUP (ORDER BY acuse_ms) {peq}",
         [(None, VERDE), (1000, ROJO)], "Referencia del enunciado: 1 s desde el último byte", "ms")

    t.fila("Los pequeños frente al umbral, y lo que hacen los carriles")
    t.panel("timeseries", "Latencia de cada pequeño (un punto por archivo) y el umbral de 3 s", [
        sql(f"SELECT recibido AS time, latencia_ms AS \"pequeño\" FROM registro.archivos WHERE $__timeFilter(recibido) AND tipo = 'pequeno' AND caso = 'valido' AND latencia_ms IS NOT NULL ORDER BY 1", "time_series")],
        24, 10, "Cada punto es un archivo pequeño. Las anotaciones marcan cada masivo que empieza a llegar",
        fieldConfig={"defaults": {"unit": "ms", "custom": {"drawStyle": "points", "pointSize": 4,
                                                           "thresholdsStyle": {"mode": "line+area"}},
                                  "thresholds": umbrales((None, "transparent"), (3000, ROJO))}, "overrides": []},
        options={"legend": {"displayMode": "hidden"}, "tooltip": {"mode": "single"}})
    serie(t, "CPU de cada contenedor (fracción de su límite)", [prom("max by (application) (process_cpu_usage)", "{{application}}")],
          8, 8, "Cada carril tiene su propio límite de CPU: el bulkhead de ADR-005", "percentunit")
    serie(t, "Archivos en proceso por carril", [prom("max by (application) (orquidea_procesamiento_en_curso)", "{{application}}")], 8, 8,
          "Hilos consumidores ocupados: el carril A no debe saturarse por los masivos")
    serie(t, "Duración del procesamiento en el carril (p99, Prometheus)", [
        prom("histogram_quantile(0.99, sum by (le, componente, tipo) (rate(orquidea_procesamiento_seconds_bucket[1m])))", "{{componente}} {{tipo}}")],
          8, 8, unidad="s")

    t.fila("Masivos")
    tabla(t, "Masivos: carril, subida, acuse y duración frente a la referencia de 5 min",
          "SELECT llave, carril, registros, bytes, recibido - recepcion_inicio AS subida, acuse_ms, resultado_en - recibido AS duracion, resultado FROM registro.archivos WHERE $__timeFilter(enviado) AND tipo = 'masivo' ORDER BY enviado",
          24, 7)
    anot = COMUNES + [anotacion("Masivo", "archivo.recepcion.inicio", AZUL, "(datos->>'bytes')")]
    anot[-1]["rawQuery"] = anot[-1]["rawQuery"].replace("WHERE tipo", "WHERE (datos->>'bytes')::bigint > 1000000 AND tipo")
    anot[-1]["target"]["rawSql"] = anot[-1]["target"]["rawSql"].replace("WHERE tipo", "WHERE (datos->>'bytes')::bigint > 1000000 AND tipo")
    return t.json("l04", "ASR-L04 · Aislamiento de carga entre archivos masivos y pequeños",
                  "Dos carriles aislados con enrutador por tamaño (ADR-005)", anot)


def s06():
    t = Tablero()
    t.fila("Veredicto en vivo de ASR-S06 (registro de eventos, ventana del tablero)")
    base = f"FROM registro.archivos WHERE {EN_VENTANA}"
    stat(t, "Catálogo enviado", f"SELECT count(*) {base} AND caso NOT IN ('valido','reenvio')", [(None, AZUL)])
    stat(t, "Catálogo aceptado", f"SELECT count(*) {base} AND caso NOT IN ('valido','reenvio') AND resultado IN ('TOTAL','PARCIAL')",
         [(None, VERDE), (1, ROJO)], "La medida de ASR-S06: debe ser 0")
    stat(t, "En verificación pendiente", f"SELECT count(*) {base} AND estado_acuse = 'VERIFICACION_PENDIENTE'", [(None, AZUL)],
         "Acusados con la revocación sin verificar: la ECD caída y la copia local vencida (ADR-007)")
    stat(t, "Aceptados antes de verificar", f"SELECT count(*) {base} AND estado_acuse = 'VERIFICACION_PENDIENTE' AND resultado IN ('TOTAL','PARCIAL') AND (verificacion_completada IS NULL OR resultado_en < verificacion_completada)",
         [(None, VERDE), (1, ROJO)], "Debe ser 0: el resultado queda retenido hasta verificar (ADR-006)")
    stat(t, "Aceptados con evidencia válida", f"SELECT count(*) {base} AND resultado IN ('TOTAL','PARCIAL') AND evidencia_firma_valida AND evidencia_hash_coincide",
         [(None, VERDE)], "Entrada firmada por BCR con el hash de los bytes procesados (ADR-008)")
    stat(t, "Aceptados sin evidencia válida", f"SELECT count(*) {base} AND resultado IN ('TOTAL','PARCIAL') AND auditado IS NOT NULL AND NOT (evidencia_firma_valida AND evidencia_hash_coincide)",
         [(None, VERDE), (1, ROJO)])

    t.fila("Rechazos y verificación pendiente")
    tabla(t, "Catálogo criptográfico: causa de rechazo por caso",
          f"SELECT caso, count(*) AS enviados, string_agg(DISTINCT coalesce(causa_rechazo, estado_acuse, 'sin respuesta'), ', ') AS causas, count(*) FILTER (WHERE resultado IN ('TOTAL','PARCIAL')) AS aceptados {base} AND caso NOT IN ('valido','reenvio') GROUP BY caso ORDER BY caso",
          12, 9)
    serie(t, "Rechazos por causa (Prometheus)", [prom("sum by (causa) (increase(orquidea_rechazos_total[1m]))", "{{causa}}")], 12, 9)
    serie(t, "Acuses por segundo según su estado", [prom("sum by (estado) (rate(orquidea_acuses_total[30s]))", "{{estado}}")], 12, 8)
    tabla(t, "Verificación pendiente: cuándo se verificó y cómo terminó",
          f"SELECT llave, caso, acusado, verificacion_completada, resultado, causa_rechazo {base} AND estado_acuse = 'VERIFICACION_PENDIENTE' ORDER BY acusado DESC LIMIT 200",
          12, 8)
    anot = COMUNES + [anotacion("CRL vencida", "revocacion.vencida", ROJO, "''"),
                      anotacion("CRL refrescada", "revocacion.refrescada", VERDE, "''")]
    return t.json("s06", "ASR-S06 · Ningún archivo se acepta sin validación criptográfica completa",
                  "Verificación completa (ADR-006), revocación en caché (ADR-007), evidencia (ADR-008)", anot)


if __name__ == "__main__":
    DESTINO.mkdir(parents=True, exist_ok=True)
    for nombre, tablero in (("d01", d01()), ("l04", l04()), ("s06", s06())):
        (DESTINO / f"{nombre}.json").write_text(json.dumps(tablero, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"tablero {nombre}: {len(tablero['panels'])} paneles")
