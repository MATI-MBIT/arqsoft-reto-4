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


def serie(t, titulo, targets, w=12, h=8, descripcion="", unidad="none", puntos="never", **campos):
    defaults = {"unit": unidad, "custom": {"lineWidth": 2, "fillOpacity": 10, "showPoints": puntos, "pointSize": 6}}
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
    t.panel("timeseries", "Cada 5 s: p99 y el más lento de los pequeños, frente al umbral de 3 s", [
        sql("SELECT $__timeGroupAlias(recibido, '10s'), (percentile_cont(0.99) WITHIN GROUP (ORDER BY latencia_ms))::float8 AS \"p99 de los pequeños\", max(latencia_ms)::float8 AS \"el más lento\" FROM registro.archivos WHERE $__timeFilter(recibido) AND tipo = 'pequeno' AND caso = 'valido' AND latencia_ms IS NOT NULL GROUP BY 1 ORDER BY 1", "time_series")],
        24, 10, "Cada punto es un archivo pequeño. Las anotaciones marcan cada masivo que empieza a llegar",
        fieldConfig={"defaults": {"unit": "ms", "min": 10, "max": 200000, "custom": {"drawStyle": "line", "lineWidth": 2, "showPoints": "always", "pointSize": 4, "scaleDistribution": {"type": "log", "log": 10},
                                                           "thresholdsStyle": {"mode": "line+area"}},
                                  "thresholds": umbrales((None, "transparent"), (3000, ROJO))}, "overrides": []},
        options={"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}})
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


# --- Pantallas de evidencia ------------------------------------------------------
# Una por ASR, pensada para grabarse en un clip: en una sola pantalla, el estímulo
# (los hechos del experimento) y la respuesta (contadores, serie de tiempo y el
# recorrido completo de un archivo testigo). Todo sale del registro de eventos,
# el mismo que decide el veredicto. Se refrescan cada segundo y miran los últimos
# 3 minutos (load/evidencias.sh las abre y graba).

HECHOS = """
SELECT to_char(min(ts) AT TIME ZONE 'America/Bogota', 'HH24:MI:SS.MS') AS "Hora", componente AS "Quién",
       registro.describir(tipo, (array_agg(datos ORDER BY ts))[1])
         || CASE WHEN count(*) > 1 THEN ' (× ' || count(*) || ')' ELSE '' END AS "Qué pasa"
FROM registro.evento WHERE $__timeFilter(ts) AND tipo IN ({tipos})
GROUP BY tipo, componente, date_trunc('minute', ts),
         CASE WHEN tipo IN ('resultado.repetido_evitado', 'archivo.duplicado', 'repeticion.inyectada') THEN '' ELSE id::text END
ORDER BY min(ts) DESC LIMIT 14"""

TESTIGO = """
WITH t AS (SELECT coalesce(
         (SELECT archivo FROM registro.evento WHERE $__timeFilter(ts) AND tipo = '{preferido}' {filtro_preferido} ORDER BY ts LIMIT 1),
         (SELECT archivo FROM registro.evento WHERE $__timeFilter(ts) AND tipo = '{tipo}' {filtro} ORDER BY ts LIMIT 1)) AS archivo)
SELECT r.hora AS "Hora", r.quien AS "Quién", r.que AS "Qué le pasa", registro.archivo_corto(r.llave) AS "Archivo"
FROM registro.recorrido r WHERE r.llave = (SELECT archivo FROM t) ORDER BY r.ts"""


def tabla_viva(t, titulo, consulta, w, h, descripcion=""):
    t.panel("table", titulo, [sql(consulta)], w, h, descripcion,
            options={"showHeader": True, "cellHeight": "sm", "footer": {"show": False}},
            fieldConfig={"defaults": {"custom": {"align": "left", "inspect": False, "cellOptions": {"type": "auto", "wrapText": True}}}, "overrides": [
                {"matcher": {"id": "byName", "options": "Hora"}, "properties": [{"id": "custom.width", "value": 100}]},
                {"matcher": {"id": "byName", "options": "Quién"}, "properties": [{"id": "custom.width", "value": 125}]},
                {"matcher": {"id": "byName", "options": "Archivo"}, "properties": [{"id": "custom.width", "value": 105}]}]})


def stat_vivo(t, titulo, consulta, pasos, descripcion="", unidad="none", w=4, fuente=None):
    target = consulta if isinstance(consulta, dict) else sql(consulta)
    t.panel("stat", titulo, [target], w, 4, descripcion,
            fieldConfig={"defaults": {"thresholds": umbrales(*pasos), "unit": unidad, "noValue": "0", "mappings": fuente or []}, "overrides": []},
            options={"colorMode": "background", "graphMode": "none", "textMode": "value", "reduceOptions": {"calcs": ["lastNotNull"]}})


ESTADO_TERCERO = [{"type": "value", "options": {
    "0": {"text": "DISPONIBLE", "color": VERDE}, "1": {"text": "DE VUELTA", "color": AZUL}, "2": {"text": "CAÍDO", "color": ROJO}}}]


def evidencia(uid, titulo, descripcion, t, anotaciones):
    j = t.json(uid, titulo, descripcion, anotaciones)
    j.update({"refresh": "1s", "time": {"from": "now-3m", "to": "now"}, "tags": ["reto4", "evidencia"]})
    return j


def ev_d01():
    t = Tablero()
    stat_vivo(t, "RNIC según el monitor", prom('max(orquidea_tercero_estado{tercero="rnic"})'), [(None, VERDE), (1, AZUL), (2, ROJO)],
              "Ping/echo cada 1 s; caído con 3 fallos seguidos (ADR-002)", fuente=ESTADO_TERCERO)
    stat_vivo(t, "Archivos acusados", "SELECT count(*) FROM registro.evento WHERE tipo = 'archivo.acusado' AND $__timeFilter(ts)",
              [(None, AZUL)], "La recepción acusa aunque el RNIC esté caído (ADR-001)")
    stat_vivo(t, "Pendientes ahora", "SELECT count(*) FROM operacion.pendiente WHERE estado = 'PENDIENTE'", [(None, VERDE), (1, NARANJA)],
              "En espera durable de la ventana nocturna (ADR-003)")
    stat_vivo(t, "Resueltos en la ventana", "SELECT count(*) FROM registro.evento WHERE tipo = 'archivo.resultado' AND datos->>'componente' = 'reprocesador' AND $__timeFilter(ts)",
              [(None, VERDE)])
    stat_vivo(t, "Rechazados por la caída", f"SELECT count(*) FROM registro.archivos WHERE {EN_VENTANA} AND caso = 'valido' AND esperado IN ('TOTAL','PARCIAL') AND resultado IS NOT NULL AND (resultado <> esperado OR rechazadas <> invalidas)",
              [(None, VERDE), (1, ROJO)], "Debe quedarse en 0")
    stat_vivo(t, "Resultados escritos dos veces", f"SELECT count(*) FROM registro.archivos WHERE {EN_VENTANA} AND resultados > 1",
              [(None, VERDE), (1, ROJO)], "Debe quedarse en 0 (ADR-004)")
    serie(t, "Cada 5 s: archivos acusados, que quedan pendientes y que se resuelven", [
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"acusados\" FROM registro.evento WHERE tipo = 'archivo.acusado' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "A"),
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"quedan pendientes\" FROM registro.evento WHERE tipo = 'archivo.pendiente' AND datos->>'componente' LIKE 'carril%' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "B"),
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"resueltos en carril\" FROM registro.evento WHERE tipo = 'archivo.resultado' AND datos->>'componente' LIKE 'carril%' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "C"),
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"resueltos en la ventana\" FROM registro.evento WHERE tipo = 'archivo.resultado' AND datos->>'componente' = 'reprocesador' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "D"),
    ], 24, 8, "Las anotaciones marcan la falla (rojo), el restablecimiento (verde) y cada ventana (azul)", puntos="always")
    tabla_viva(t, "Lo que pasa en el experimento: fallas, monitor, ventanas, reenvíos", HECHOS.format(tipos="'falla.inyectada','falla.restablecida','tercero.caido','tercero.de_vuelta','tercero.disponible','ventana.inicio','ventana.fin','repeticion.inyectada','resultado.repetido_evitado','archivo.duplicado'"),
               10, 15)
    tabla_viva(t, "Archivo testigo: el primero reenviado o, si no hay, el primero que quedó pendiente",
               TESTIGO.format(preferido="archivo.duplicado", filtro_preferido="", tipo="archivo.pendiente", filtro="AND datos->>'componente' LIKE 'carril%'"), 14, 15)
    return evidencia("ev-d01", "Evidencia ASR-D01 · el RNIC cae y nada se rechaza",
                     "Pantalla de evidencia de load/evidencias.sh: caída, pendientes, ventana y recuperación", t, COMUNES)


def ev_l04():
    t = Tablero()
    # Como el veredicto: un pequeño acusado sin resultado cuenta con su espera hasta ahora.
    lat = "coalesce(latencia_ms, round(extract(epoch FROM now() - recibido) * 1000))"
    peq = ("FROM registro.archivos WHERE tipo = 'pequeno' AND caso = 'valido' AND estado_acuse = 'ACUSADO' "
           "AND recibido > now() - interval '60 seconds'")
    stat_vivo(t, "p99 de los pequeños (último minuto)", f"SELECT percentile_cont(0.99) WITHIN GROUP (ORDER BY {lat}) {peq}",
              [(None, VERDE), (3000, ROJO)], "Del último byte al resultado; el que aún espera cuenta con su espera hasta ahora", "ms", 5)
    stat_vivo(t, "Pequeños sobre 3 s (último minuto)", f"SELECT count(*) {peq} AND {lat} > 3000", [(None, VERDE), (1, ROJO)], w=5)
    stat_vivo(t, "Pequeños esperando su resultado", "SELECT count(*) FROM registro.archivos WHERE tipo = 'pequeno' AND caso = 'valido' AND estado_acuse = 'ACUSADO' AND resultado IS NULL AND recibido > now() - interval '90 seconds'",
              [(None, VERDE), (5, NARANJA), (50, ROJO)], "Acusados que todavía no tienen resultado definitivo", w=5)
    stat_vivo(t, "Masivos en proceso", "SELECT count(*) FROM operacion.archivo WHERE registros > 50 AND resultado IS NULL",
              [(None, AZUL)], w=4)
    stat_vivo(t, "En proceso en el carril B", prom('max(orquidea_procesamiento_en_curso{application="carril-b"})'), [(None, AZUL)],
              "Archivos en proceso en el carril B. En 0 con masivos en proceso: todo va por el carril A (sin carriles)", w=5,
              fuente=[{"type": "value", "options": {"0": {"text": "ocioso"}}}])
    t.panel("timeseries", "Cada 5 s: p99 y el más lento de los pequeños, frente al umbral de 3 s", [
        sql(f"SELECT $__timeGroupAlias(recibido, '5s'), (percentile_cont(0.99) WITHIN GROUP (ORDER BY {lat}))::float8 AS \"p99 de los pequeños\", max({lat})::float8 AS \"el más lento\" FROM registro.archivos WHERE $__timeFilter(recibido) AND tipo = 'pequeno' AND caso = 'valido' AND estado_acuse = 'ACUSADO' GROUP BY 1 ORDER BY 1", "time_series")],
        14, 13, "Las anotaciones azules marcan cada masivo que empieza a llegar",
        fieldConfig={"defaults": {"unit": "ms", "min": 10, "max": 200000, "custom": {"drawStyle": "line", "lineWidth": 2, "showPoints": "always", "pointSize": 4,
                                                                                  "scaleDistribution": {"type": "log", "log": 10}, "thresholdsStyle": {"mode": "line+area"}},
                                  "thresholds": umbrales((None, "transparent"), (3000, ROJO))}, "overrides": []},
        options={"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}})
    serie(t, "CPU de cada carril frente a su límite", [prom("max by (application) (process_cpu_usage{application=~\"carril-.*|recepcion\"})", "{{application}}")],
          10, 7, "Cada carril tiene su propio límite de CPU (bulkhead, ADR-005)", "percentunit", min=0, max=1)
    serie(t, "Archivos en proceso por carril", [prom("max by (application) (orquidea_procesamiento_en_curso{application=~\"carril-.*\"})", "{{application}}")],
          10, 6, "Hilos consumidores ocupados", min=0)
    tabla_viva(t, "Progreso de cada masivo", """
SELECT registro.archivo_corto(a.llave) AS "Masivo", a.carril AS "Carril", round(a.bytes / 1048576.0, 1) AS "MB",
       (SELECT count(*) FROM operacion.resultado_declaracion r WHERE r.llave = a.llave) * 100 / a.registros AS "% procesado",
       CASE WHEN a.resultado IS NULL THEN 'en proceso' ELSE a.resultado END AS "Estado",
       to_char(coalesce(a.resultado_en, now()) - a.recibido_en, 'MI:SS') AS "Lleva"
FROM operacion.archivo a WHERE a.registros > 50 ORDER BY a.recibido_en""", 24, 7)
    # La gráfica a la izquierda; CPU y archivos en proceso apilados a su derecha; los masivos abajo.
    por_titulo = {p["title"]: p for p in t.paneles}
    por_titulo["Cada 5 s: p99 y el más lento de los pequeños, frente al umbral de 3 s"]["gridPos"] = {"x": 0, "y": 4, "w": 14, "h": 13}
    por_titulo["CPU de cada carril frente a su límite"]["gridPos"] = {"x": 14, "y": 4, "w": 10, "h": 7}
    por_titulo["Archivos en proceso por carril"]["gridPos"] = {"x": 14, "y": 11, "w": 10, "h": 6}
    por_titulo["Progreso de cada masivo"]["gridPos"] = {"x": 0, "y": 17, "w": 24, "h": 7}
    anot = COMUNES + [anotacion("Masivo", "archivo.recepcion.inicio", AZUL, "(datos->>'bytes')")]
    anot[-1]["rawQuery"] = anot[-1]["rawQuery"].replace("WHERE tipo", "WHERE (datos->>'bytes')::bigint > 1000000 AND tipo")
    anot[-1]["target"]["rawSql"] = anot[-1]["target"]["rawSql"].replace("WHERE tipo", "WHERE (datos->>'bytes')::bigint > 1000000 AND tipo")
    return evidencia("ev-l04", "Evidencia ASR-L04 · masivos en su carril, pequeños sin esperar",
                     "Pantalla de evidencia de load/evidencias.sh: masivos con carriles y sin carriles", t, anot)


def ev_s06():
    t = Tablero()
    stat_vivo(t, "ECD según el monitor", prom('max(orquidea_tercero_estado{tercero="ecd"})'), [(None, VERDE), (1, AZUL), (2, ROJO)],
              fuente=ESTADO_TERCERO)
    stat_vivo(t, "Rechazados por la recepción", "SELECT count(*) FROM registro.evento WHERE tipo = 'archivo.rechazado' AND $__timeFilter(ts)",
              [(None, AZUL)])
    stat_vivo(t, "Catálogo aceptado", f"SELECT count(*) FROM registro.archivos WHERE {EN_VENTANA} AND caso NOT IN ('valido','reenvio') AND resultado IN ('TOTAL','PARCIAL')",
              [(None, VERDE), (1, ROJO)], "La medida de ASR-S06: debe quedarse en 0")
    stat_vivo(t, "Acusados en verificación pendiente", "SELECT count(*) FROM registro.evento WHERE tipo = 'archivo.acusado' AND datos->>'estado' = 'VERIFICACION_PENDIENTE' AND $__timeFilter(ts)",
              [(None, AZUL)], "La ECD no responde y la CRL local venció: se acusa, no se acepta (ADR-007)")
    stat_vivo(t, "Aceptados antes de verificar", f"SELECT count(*) FROM registro.archivos WHERE {EN_VENTANA} AND estado_acuse = 'VERIFICACION_PENDIENTE' AND resultado IN ('TOTAL','PARCIAL') AND (verificacion_completada IS NULL OR resultado_en < verificacion_completada)",
              [(None, VERDE), (1, ROJO)], "Debe quedarse en 0 (ADR-006)")
    stat_vivo(t, "Verificados en la ventana", "SELECT count(*) FROM registro.evento WHERE tipo = 'verificacion.completada' AND $__timeFilter(ts)",
              [(None, VERDE)])
    serie(t, "Cada 5 s: cómo responde la recepción", [
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"acusados y verificados\" FROM registro.evento WHERE tipo = 'archivo.acusado' AND datos->>'estado' = 'ACUSADO' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "A"),
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"acusados en verificación pendiente\" FROM registro.evento WHERE tipo = 'archivo.acusado' AND datos->>'estado' = 'VERIFICACION_PENDIENTE' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "B"),
        sql("SELECT $__timeGroupAlias(ts, '5s'), count(*) AS \"rechazados\" FROM registro.evento WHERE tipo = 'archivo.rechazado' AND $__timeFilter(ts) GROUP BY 1 ORDER BY 1", "time_series", "C"),
    ], 24, 7, puntos="always")
    tabla_viva(t, "Rechazos de la recepción: qué se envió y por qué se rechazó", """
SELECT to_char(r.ts AT TIME ZONE 'America/Bogota', 'HH24:MI:SS.MS') AS "Hora",
       coalesce((SELECT e.datos->>'caso' FROM registro.evento e WHERE e.archivo = r.archivo AND e.tipo = 'archivo.enviado' ORDER BY e.ts DESC LIMIT 1), '—') AS "Caso enviado",
       r.datos->>'causa' AS "Causa del rechazo", r.componente AS "Quién"
FROM registro.evento r WHERE r.tipo = 'archivo.rechazado' AND $__timeFilter(r.ts) ORDER BY r.ts DESC LIMIT 14""", 10, 16)
    tabla_viva(t, "Archivo testigo: el primero en verificación pendiente o, si no hay, el primero rechazado",
               TESTIGO.format(preferido="archivo.acusado", filtro_preferido="AND datos->>'estado' = 'VERIFICACION_PENDIENTE'", tipo="archivo.rechazado", filtro=""), 14, 16)
    anot = COMUNES + [anotacion("CRL vencida", "revocacion.vencida", ROJO, "''")]
    return evidencia("ev-s06", "Evidencia ASR-S06 · nada se acepta sin verificar",
                     "Pantalla de evidencia de load/evidencias.sh: catálogo criptográfico y ECD caída", t, anot)


if __name__ == "__main__":
    DESTINO.mkdir(parents=True, exist_ok=True)
    for nombre, tablero in (("d01", d01()), ("l04", l04()), ("s06", s06()),
                            ("ev-d01", ev_d01()), ("ev-l04", ev_l04()), ("ev-s06", ev_s06())):
        (DESTINO / f"{nombre}.json").write_text(json.dumps(tablero, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"tablero {nombre}: {len(tablero['panels'])} paneles")
