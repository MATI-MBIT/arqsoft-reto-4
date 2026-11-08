-- Vistas del cruce uno a uno por la llave del archivo. Las leen el veredicto
-- (analisis/*.sql), los tableros de Grafana y las gráficas, para que todos cuenten
-- igual. Se recargan sin perder datos con `make vistas`.
--
-- Todo sale de registro.evento: lo que anotó cada componente con su reloj, en el
-- instante del hecho. Las tablas de operacion se vacían al preparar cada ejecución;
-- el registro no, así que un veredicto se puede recalcular después.

-- Un archivo enviado (no los reenvíos de D-5), con cada hito de su recorrido.
-- Cada hito es el primero de su tipo después del envío; así, el reenvío de un
-- archivo con la misma llave no se confunde con el original.
CREATE OR REPLACE VIEW registro.archivos AS
SELECT e.archivo                                         AS llave,
       e.ts                                              AS enviado,
       e.datos ->> 'tipo'                                AS tipo,
       e.datos ->> 'caso'                                AS caso,
       e.datos ->> 'esperado'                            AS esperado,
       (e.datos ->> 'registros')::int                    AS registros,
       (e.datos ->> 'invalidas')::int                    AS invalidas,
       (e.datos ->> 'bytes')::bigint                     AS bytes,
       ini.ts                                            AS recepcion_inicio,
       rec.ts                                            AS recibido,       -- t0: último byte
       ac.ts                                             AS acusado,
       ac.datos ->> 'estado'                             AS estado_acuse,
       ac.datos ->> 'carril'                             AS carril,
       rz.ts                                             AS rechazado,
       rz.datos ->> 'causa'                              AS causa_rechazo,
       rz.datos ->> 'componente'                         AS rechazado_por,
       res.ts                                            AS resultado_en,
       res.datos ->> 'resultado'                         AS resultado,
       res.datos ->> 'componente'                        AS resuelto_por,
       (res.datos ->> 'rechazadas')::int                 AS rechazadas,
       res.datos ->> 'hash'                              AS hash_procesado,
       (SELECT count(*) FROM registro.evento r
        WHERE r.archivo = e.archivo AND r.tipo = 'archivo.resultado') AS resultados,
       pen.ts                                            AS pendiente_desde,
       pen.datos ->> 'motivo'                            AS motivo_pendiente,
       (pres.datos ->> 'intentos')::int                  AS intentos_al_resolver,
       noti.ts                                           AS notificado,
       ver.ts                                            AS verificacion_completada,
       aud.ts                                            AS auditado,
       (aud.datos ->> 'firmaValida')::boolean            AS evidencia_firma_valida,
       (aud.datos ->> 'hashCoincide')::boolean           AS evidencia_hash_coincide,
       round(extract(epoch FROM res.ts - rec.ts) * 1000) AS latencia_ms,     -- ASR-L04
       round(extract(epoch FROM ac.ts - rec.ts) * 1000)  AS acuse_ms
FROM registro.evento e
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.recepcion.inicio' AND x.ts >= e.ts ORDER BY ts LIMIT 1) ini ON true
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.recibido' AND x.ts >= e.ts ORDER BY ts LIMIT 1) rec ON true
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.acusado' AND x.ts >= e.ts ORDER BY ts LIMIT 1) ac ON true
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.rechazado' AND x.ts >= e.ts ORDER BY ts LIMIT 1) rz ON true
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.resultado' AND x.ts >= e.ts ORDER BY ts LIMIT 1) res ON true
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.pendiente' AND x.ts >= e.ts ORDER BY ts LIMIT 1) pen ON true
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'pendiente.resuelto' AND x.ts >= e.ts ORDER BY ts LIMIT 1) pres ON true
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'remitente.notificado' AND x.ts >= e.ts ORDER BY ts LIMIT 1) noti ON true
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'verificacion.completada' AND x.ts >= e.ts ORDER BY ts LIMIT 1) ver ON true
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'evidencia.auditada' AND x.ts >= e.ts ORDER BY ts DESC LIMIT 1) aud ON true
WHERE e.tipo = 'archivo.enviado' AND NOT coalesce((e.datos ->> 'reenvio')::boolean, false);

-- Los reenvíos de D-5: misma llave y mismos bytes que un archivo pendiente.
CREATE OR REPLACE VIEW registro.reenvios AS
SELECT e.archivo AS llave, e.ts AS reenviado,
       (SELECT min(x.ts) FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.duplicado' AND x.ts >= e.ts) AS duplicado,
       (SELECT min(x.ts) FROM registro.evento x WHERE x.archivo = e.archivo AND x.tipo = 'archivo.acusado' AND x.ts >= e.ts) AS acusado_otra_vez
FROM registro.evento e
WHERE e.tipo = 'archivo.enviado' AND coalesce((e.datos ->> 'reenvio')::boolean, false);

-- Cada falla inyectada con su restablecimiento y la detección del monitor.
-- detectada: el primer «tercero.caido» del mismo tercero después de la falla.
-- de_vuelta: el primer «tercero.de_vuelta» después de la detección de esa falla.
-- Hasta el 2026-11-07 el inyector anotaba el restablecimiento después de
-- aplicarlo, así que el regreso podía quedar unos milisegundos antes (negativo).
CREATE OR REPLACE VIEW registro.fallas AS
SELECT f.id AS falla_id, f.ts AS falla, f.datos ->> 'tipo' AS tipo, f.datos ->> 'tercero' AS tercero,
       r.ts AS restablecida,
       d.ts AS detectada,
       round(extract(epoch FROM d.ts - f.ts) * 1000) AS deteccion_ms,
       v.ts AS de_vuelta,
       round(extract(epoch FROM v.ts - r.ts) * 1000) AS regreso_ms,
       (SELECT count(*) FROM registro.evento a
        WHERE a.tipo = 'adaptador.vencida' AND a.ts BETWEEN f.ts AND coalesce(d.ts, f.ts)) AS llamadas_antes_de_detectar,
       (SELECT count(*) FROM registro.evento a
        WHERE a.tipo = 'adaptador.vencida' AND a.ts BETWEEN f.ts AND coalesce(r.ts, 'infinity')) AS llamadas_vencidas
FROM registro.evento f
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.tipo = 'falla.restablecida' AND x.datos ->> 'tercero' = f.datos ->> 'tercero' AND x.ts > f.ts ORDER BY ts LIMIT 1) r ON true
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.tipo = 'tercero.caido' AND x.datos ->> 'tercero' = f.datos ->> 'tercero' AND x.ts >= f.ts ORDER BY ts LIMIT 1) d ON true
LEFT JOIN LATERAL (SELECT ts FROM registro.evento x WHERE x.tipo = 'tercero.de_vuelta' AND x.datos ->> 'tercero' = f.datos ->> 'tercero' AND x.ts > coalesce(d.ts, f.ts) ORDER BY ts LIMIT 1) v ON true
WHERE f.tipo = 'falla.inyectada';

-- Cada ventana nocturna con su desenlace.
CREATE OR REPLACE VIEW registro.ventanas AS
SELECT i.ts AS inicio, (i.datos ->> 'numero')::int AS numero, i.datos ->> 'rnic' AS rnic_al_abrir,
       f.ts AS fin, f.datos AS desenlace
FROM registro.evento i
LEFT JOIN LATERAL (SELECT ts, datos FROM registro.evento x WHERE x.tipo = 'ventana.fin'
                   AND x.datos ->> 'numero' = i.datos ->> 'numero' AND x.ts >= i.ts ORDER BY ts LIMIT 1) f ON true
WHERE i.tipo = 'ventana.inicio';

-- Cada evento en una frase, para las pantallas de evidencia (ev-d01, ev-l04, ev-s06).
CREATE OR REPLACE FUNCTION registro.archivo_corto(llave text) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN llave IS NULL THEN '' ELSE split_part(llave, '|', 1) || ' #' || ltrim(split_part(llave, '|', 4), '0') END
$$;

CREATE OR REPLACE FUNCTION registro.describir(tipo text, d jsonb) RETURNS text LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE tipo
        WHEN 'falla.inyectada' THEN CASE d ->> 'tipo'
                WHEN 'lentitud' THEN '⚡ El ' || upper(d ->> 'tercero') || ' se vuelve lento: ' || (d ->> 'latenciaMs') || ' ms por respuesta'
                ELSE '⚡ Se corta el ' || upper(d ->> 'tercero') END
        WHEN 'falla.restablecida' THEN '✔ Se restablece el ' || upper(d ->> 'tercero')
        WHEN 'tercero.caido' THEN 'Monitor: el ' || upper(d ->> 'tercero') || ' está CAÍDO'
        WHEN 'tercero.de_vuelta' THEN 'Monitor: el ' || upper(d ->> 'tercero') || ' está DE VUELTA'
        WHEN 'tercero.disponible' THEN 'Monitor: el ' || upper(d ->> 'tercero') || ' está DISPONIBLE'
        WHEN 'ventana.inicio' THEN 'Abre la ventana nocturna ' || (d ->> 'numero') || ' (RNIC ' || (d ->> 'rnic') || ')'
        WHEN 'ventana.fin' THEN 'Cierra la ventana ' || (d ->> 'numero') || ': ' || (d ->> 'resueltos') || ' resueltos · '
                || (d ->> 'reprogramados') || ' reprogramados · ' || (d ->> 'notificados') || ' notificados · '
                || (d ->> 'liberados') || ' liberados'
        WHEN 'revocacion.vencida' THEN '⚡ Se vence la copia local de la CRL'
        WHEN 'revocacion.refrescada' THEN 'La recepción refresca la CRL de la ECD'
        WHEN 'repeticion.inyectada' THEN '⚡ Se repite en la cola un mensaje ya procesado'
        WHEN 'archivo.enviado' THEN CASE WHEN (d ->> 'reenvio')::boolean THEN '⚡ El remitente reenvía el archivo con la misma llave'
                ELSE 'El remitente envía el archivo: ' || (d ->> 'registros') || ' declaraciones, ' || (d ->> 'invalidas')
                     || ' con cédula errada' || CASE WHEN d ->> 'caso' <> 'valido' THEN ' · caso ' || (d ->> 'caso') ELSE '' END END
        WHEN 'archivo.recibido' THEN 'Llega el último byte (t0): ' || round((d ->> 'bytes')::numeric / 1024) || ' KB'
        WHEN 'evidencia.registrada' THEN 'Evidencia anexada y firmada por BCR'
        WHEN 'archivo.acusado' THEN 'Acuse en ' || (d ->> 'acuseMs') || ' ms: ' || (d ->> 'estado') || ' → ' || (d ->> 'carril')
        WHEN 'archivo.duplicado' THEN 'RECHAZADO como duplicado: la llave ya existe'
        WHEN 'archivo.rechazado' THEN 'RECHAZADO: ' || (d ->> 'causa')
        WHEN 'archivo.procesamiento.inicio' THEN 'Empieza a procesarlo ' || (d ->> 'componente') || ' desde la declaración ' || (d ->> 'desdeN')
        WHEN 'adaptador.vencida' THEN 'La llamada al RNIC vence el timeout'
        WHEN 'archivo.pendiente' THEN 'Queda PENDIENTE desde la declaración ' || (d ->> 'desdeN') || ' (' || (d ->> 'motivo') || ', intento '
                || (d ->> 'intentos') || ')'
        WHEN 'pendiente.reprogramado' THEN 'Se reprograma para la siguiente ventana (intento ' || (d ->> 'intentos') || ')'
        WHEN 'remitente.notificado' THEN 'Se notifica al remitente; el archivo sigue pendiente'
        WHEN 'verificacion.completada' THEN 'Revocación verificada en la ventana → se libera a ' || (d ->> 'carril')
        WHEN 'archivo.resultado' THEN 'Resultado definitivo ' || (d ->> 'resultado') || ': ' || (d ->> 'aceptadas') || ' aceptadas, '
                || (d ->> 'rechazadas') || ' rechazadas por la regla · ' || (d ->> 'componente')
        WHEN 'pendiente.resuelto' THEN 'El pendiente queda resuelto en la ventana'
        WHEN 'resultado.repetido_evitado' THEN 'Mensaje repetido: no se escribe un segundo resultado'
        ELSE tipo END
$$;

-- Los hechos de un archivo, en orden, con su hora de Bogotá.
CREATE OR REPLACE VIEW registro.recorrido AS
SELECT e.archivo AS llave, e.ts, to_char(e.ts AT TIME ZONE 'America/Bogota', 'HH24:MI:SS.MS') AS hora,
       e.componente AS quien, registro.describir(e.tipo, e.datos) AS que, e.tipo
FROM registro.evento e
WHERE e.archivo IS NOT NULL AND e.tipo NOT IN ('archivo.recepcion.inicio', 'evidencia.auditada');
