-- Veredicto de ASR-S06 para una ejecución. Uso: psql -v corrida=<id> -f s06.sql
--
-- Medida: 0 archivos aceptados sin firma, firmante, cadena, estampa y revocación
-- verificadas. Entran los archivos del catálogo criptográfico (S-1) y los que se
-- acusaron en verificación pendiente (S-2): ninguno puede llegar a aceptación
-- total o parcial sin verificación completa.
\set ON_ERROR_STOP on
\pset footer off
\pset null '—'

\echo
\echo '== Ejecución'
SELECT id, asr, escenario, variables, inicio, fin, cierre, fin - inicio AS medicion, valida, motivo
FROM registro.corrida WHERE id = :'corrida';

CREATE TEMP VIEW w AS SELECT arranque, inicio, fin, cierre FROM registro.corrida WHERE id = :'corrida';
CREATE TEMP VIEW x AS SELECT a.* FROM registro.archivos a, w WHERE a.enviado BETWEEN w.arranque AND w.cierre;
CREATE TEMP VIEW catalogo AS SELECT * FROM x WHERE caso NOT IN ('valido', 'reenvio');
CREATE TEMP VIEW verif_pendiente AS SELECT * FROM x WHERE estado_acuse = 'VERIFICACION_PENDIENTE';

\echo
\echo '== Criterios de ASR-S06'
WITH criterios AS (
    SELECT 1 AS orden, 'Medida · archivos del catálogo aceptados (total o parcial)' AS criterio,
           count(*) AS casos, count(*) FILTER (WHERE resultado IS NULL OR resultado = 'RECHAZO') AS cumplen,
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')) AS fallan,
           CASE WHEN count(*) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')) = 0 THEN 'PASA' ELSE 'FALLA' END AS veredicto
    FROM catalogo
    UNION ALL
    SELECT 2, 'Medida · archivos en verificación pendiente aceptados antes de completar la verificación',
           count(*), count(*) FILTER (WHERE resultado_en IS NULL OR resultado_en > verificacion_completada OR resultado = 'RECHAZO'),
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND (verificacion_completada IS NULL OR resultado_en < verificacion_completada)),
           CASE WHEN count(*) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND (verificacion_completada IS NULL OR resultado_en < verificacion_completada)) = 0 THEN 'PASA'
                ELSE 'FALLA' END
    FROM verif_pendiente
    UNION ALL
    SELECT 3, 'Cada caso del catálogo se rechaza con su causa',
           count(*), count(*) FILTER (WHERE causa_rechazo IS NOT NULL), count(*) FILTER (WHERE causa_rechazo IS NULL),
           CASE WHEN count(*) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE causa_rechazo IS NULL) = 0 THEN 'PASA' ELSE 'FALLA' END
    FROM catalogo
    UNION ALL
    SELECT 4, 'S-2 · lo verificado en la ventana llega a resultado; lo revocado se rechaza',
           count(*), count(*) FILTER (WHERE resultado IS NOT NULL OR causa_rechazo IS NOT NULL),
           count(*) FILTER (WHERE resultado IS NULL AND causa_rechazo IS NULL),
           CASE WHEN count(*) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE resultado IS NULL AND causa_rechazo IS NULL) = 0 THEN 'PASA' ELSE 'FALLA' END
    FROM verif_pendiente
    UNION ALL
    SELECT 5, 'S-3 · cada aceptado tiene su evidencia firmada con el hash de los bytes procesados',
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')),
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND evidencia_firma_valida AND evidencia_hash_coincide),
           count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND NOT coalesce(evidencia_firma_valida AND evidencia_hash_coincide, false)),
           CASE WHEN count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')) = 0 THEN 'NO APLICA'
                WHEN count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND NOT coalesce(evidencia_firma_valida AND evidencia_hash_coincide, false)) = 0 THEN 'PASA'
                ELSE 'FALLA' END
    FROM x)
SELECT criterio, casos, cumplen, fallan, veredicto FROM criterios ORDER BY orden;

\echo
\echo '== Catálogo: causa de rechazo por caso'
SELECT caso, count(*) AS enviados, string_agg(DISTINCT coalesce(causa_rechazo, estado_acuse, 'sin respuesta'), ', ') AS causas,
       count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')) AS aceptados,
       max(coalesce(acuse_ms, round(extract(epoch FROM rechazado - recibido) * 1000))) AS respuesta_max_ms
FROM catalogo GROUP BY caso ORDER BY caso;

\echo
\echo '== S-2: verificación pendiente'
SELECT caso, count(*) AS acusados_en_verificacion_pendiente,
       count(*) FILTER (WHERE verificacion_completada IS NOT NULL) AS verificados_en_ventana,
       count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL')) AS aceptados_despues,
       count(*) FILTER (WHERE causa_rechazo IS NOT NULL) AS rechazados_en_ventana,
       min(verificacion_completada - acusado) AS espera_minima, max(resultado_en - acusado) AS espera_maxima
FROM verif_pendiente GROUP BY caso ORDER BY caso;

\echo
\echo '== Evidencia (S-3)'
SELECT count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL'))                                 AS aceptados,
       count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND auditado IS NOT NULL)       AS con_entrada,
       count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND evidencia_firma_valida)     AS firma_valida,
       count(*) FILTER (WHERE resultado IN ('TOTAL', 'PARCIAL') AND evidencia_hash_coincide)    AS hash_coincide
FROM x;
