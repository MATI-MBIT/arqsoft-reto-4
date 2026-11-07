-- Veredicto de ASR-D01 para una ejecución. Uso: psql -v corrida=<id> -f d01.sql
--
-- Medida: 100 % de los archivos válidos procesados dentro de los días de reintento.
-- Válido: se habría aceptado con el RNIC disponible (el remitente lo anota como
-- TOTAL o PARCIAL al enviarlo). Procesado dentro de los días: tiene resultado
-- definitivo, igual al esperado, y si pasó por pendientes se resolvió a más tardar
-- en la ventana número DIAS_REINTENTO (intentos al resolver < días).
\set ON_ERROR_STOP on
\pset footer off
\pset null '—'

\echo
\echo '== Ejecución'
SELECT id, asr, escenario, variables, inicio, fin, cierre, fin - inicio AS medicion, valida, motivo
FROM registro.corrida WHERE id = :'corrida';

CREATE TEMP VIEW w AS
SELECT arranque, inicio, fin, cierre, coalesce((variables ->> 'dias')::int, 2) AS dias,
       coalesce(variables ->> 'ventanas', '') AS ventanas
FROM registro.corrida WHERE id = :'corrida';

CREATE TEMP VIEW x AS
SELECT a.* FROM registro.archivos a, w
WHERE a.enviado BETWEEN w.arranque AND w.cierre AND a.caso = 'valido';

CREATE TEMP VIEW validos AS SELECT * FROM x WHERE esperado IN ('TOTAL', 'PARCIAL');

CREATE TEMP VIEW juicio AS
SELECT v.*,
       (v.estado_acuse = 'ACUSADO' AND v.resultado = v.esperado AND v.rechazadas = v.invalidas
        AND coalesce(v.intentos_al_resolver, 0) < (SELECT dias FROM w)) AS dentro,
       (v.estado_acuse = 'ACUSADO' AND v.resultado = v.esperado AND v.rechazadas = v.invalidas) AS procesado
FROM validos v;

-- D-3: el RNIC sigue caído en tantas ventanas como días de reintento; la medida no aplica.
CREATE TEMP VIEW d3 AS
SELECT (length(ventanas) - length(replace(ventanas, 'abajo', ''))) / 5 >= dias AS fuera_de_medida FROM w;

\echo
\echo '== Criterios de ASR-D01'
WITH criterios AS (
    SELECT 1 AS orden, 'Medida · archivos válidos procesados dentro de los días de reintento = 100 %' AS criterio,
           count(*) AS casos, count(*) FILTER (WHERE dentro) AS cumplen, count(*) FILTER (WHERE NOT dentro OR dentro IS NULL) AS fallan,
           CASE WHEN (SELECT fuera_de_medida FROM d3) THEN 'NO APLICA'
                WHEN count(*) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE NOT dentro OR dentro IS NULL) = 0 THEN 'PASA' ELSE 'FALLA' END AS veredicto
    FROM juicio
    UNION ALL
    SELECT 2, 'D-3 · lo notificado sigue pendiente y se procesa al volver el RNIC',
           count(*) FILTER (WHERE notificado IS NOT NULL),
           count(*) FILTER (WHERE notificado IS NOT NULL AND procesado AND resultado_en > notificado),
           count(*) FILTER (WHERE notificado IS NOT NULL AND NOT coalesce(procesado AND resultado_en > notificado, false)),
           CASE WHEN NOT (SELECT fuera_de_medida FROM d3) THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE notificado IS NOT NULL) = 0 THEN 'FALLA'
                WHEN count(*) FILTER (WHERE notificado IS NOT NULL AND NOT coalesce(procesado AND resultado_en > notificado, false)) = 0 THEN 'PASA'
                ELSE 'FALLA' END
    FROM juicio
    UNION ALL
    SELECT 3, 'Ningún archivo válido rechazado por la caída (resultado distinto del esperado)',
           count(*), count(*) FILTER (WHERE resultado IS NULL OR (resultado = esperado AND rechazadas = invalidas)),
           count(*) FILTER (WHERE resultado IS NOT NULL AND (resultado <> esperado OR rechazadas <> invalidas)),
           CASE WHEN count(*) FILTER (WHERE resultado IS NOT NULL AND (resultado <> esperado OR rechazadas <> invalidas)) = 0 THEN 'PASA' ELSE 'FALLA' END
    FROM validos
    UNION ALL
    SELECT 4, 'Archivos con cédulas que no corresponden: rechazados por la regla, con su número exacto',
           count(*) FILTER (WHERE invalidas > 0),
           count(*) FILTER (WHERE invalidas > 0 AND rechazadas = invalidas),
           count(*) FILTER (WHERE invalidas > 0 AND rechazadas IS DISTINCT FROM invalidas),
           CASE WHEN count(*) FILTER (WHERE invalidas > 0) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE invalidas > 0 AND rechazadas IS DISTINCT FROM invalidas) = 0 THEN 'PASA' ELSE 'FALLA' END
    FROM x
    UNION ALL
    SELECT 5, 'D-5 · cada archivo tiene un solo resultado definitivo (0 escritos dos veces)',
           count(*) FILTER (WHERE resultados > 0), count(*) FILTER (WHERE resultados = 1), count(*) FILTER (WHERE resultados > 1),
           CASE WHEN count(*) FILTER (WHERE resultados > 1) = 0 THEN 'PASA' ELSE 'FALLA' END
    FROM x
    UNION ALL
    SELECT 6, 'D-5 · cada reenvío con la misma llave se rechaza como duplicado',
           count(*), count(*) FILTER (WHERE duplicado IS NOT NULL AND acusado_otra_vez IS NULL),
           count(*) FILTER (WHERE duplicado IS NULL OR acusado_otra_vez IS NOT NULL),
           CASE WHEN count(*) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE duplicado IS NULL OR acusado_otra_vez IS NOT NULL) = 0 THEN 'PASA' ELSE 'FALLA' END
    FROM registro.reenvios r, w WHERE r.reenviado BETWEEN w.arranque AND w.cierre
    UNION ALL
    SELECT 7, 'S-3 · cada aceptado tiene su evidencia firmada con el hash de los bytes procesados',
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')),
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND evidencia_firma_valida AND evidencia_hash_coincide),
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND NOT coalesce(evidencia_firma_valida AND evidencia_hash_coincide, false)),
           CASE WHEN count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND NOT coalesce(evidencia_firma_valida AND evidencia_hash_coincide, false)) = 0 THEN 'PASA'
                ELSE 'FALLA' END
    FROM x)
SELECT criterio, casos, cumplen, fallan, veredicto FROM criterios ORDER BY orden;

\echo
\echo '== Recorrido de los archivos válidos'
SELECT count(*)                                                   AS validos_enviados,
       count(*) FILTER (WHERE estado_acuse = 'ACUSADO')          AS acusados,
       count(*) FILTER (WHERE pendiente_desde IS NOT NULL)        AS pasaron_por_pendientes,
       count(*) FILTER (WHERE resuelto_por LIKE 'carril%')        AS resueltos_en_carril,
       count(*) FILTER (WHERE resuelto_por = 'reprocesador')      AS resueltos_en_ventana,
       count(*) FILTER (WHERE notificado IS NOT NULL)             AS notificados,
       count(*) FILTER (WHERE resultado IS NULL)                  AS sin_resultado,
       max(intentos_al_resolver)                                  AS max_intentos
FROM juicio;

\echo
\echo '== Detección (ADR-002): de la falla al cambio de estado en el monitor'
SELECT f.tipo, f.tercero, f.falla, f.deteccion_ms, f.llamadas_antes_de_detectar, f.llamadas_vencidas,
       f.restablecida, f.regreso_ms
FROM registro.fallas f, w WHERE f.falla BETWEEN w.arranque AND w.cierre ORDER BY f.falla;

\echo
\echo '== Ventanas nocturnas'
SELECT v.numero, v.rnic_al_abrir, v.inicio, v.fin - v.inicio AS duracion,
       v.desenlace ->> 'resueltos' AS resueltos, v.desenlace ->> 'reprogramados' AS reprogramados,
       v.desenlace ->> 'notificados' AS notificados, v.desenlace ->> 'liberados' AS liberados,
       v.desenlace ->> 'omitidos' AS omitidos
FROM registro.ventanas v, w WHERE v.inicio BETWEEN w.arranque AND w.cierre ORDER BY v.inicio;

\echo
\echo '== Repeticiones inyectadas (D-5): resultados repetidos que el UPDATE evitó'
SELECT (SELECT count(*) FROM registro.evento e, w WHERE e.tipo = 'repeticion.inyectada' AND e.ts BETWEEN w.arranque AND w.cierre) AS repeticiones,
       (SELECT count(*) FROM registro.evento e, w WHERE e.tipo = 'resultado.repetido_evitado' AND e.ts BETWEEN w.arranque AND w.cierre) AS repetidos_evitados;

\echo
\echo '== Escapes: archivos válidos que no se procesaron dentro de los días'
SELECT llave, esperado, estado_acuse, resultado, rechazadas, invalidas, intentos_al_resolver, pendiente_desde, resultado_en
FROM juicio WHERE NOT coalesce(dentro, false) ORDER BY enviado LIMIT 30;
