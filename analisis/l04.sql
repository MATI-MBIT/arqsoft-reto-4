-- Veredicto de ASR-L04 para una ejecución. Uso: psql -v corrida=<id> -f l04.sql
--
-- Medida: p99 ≤ 3 s por archivo pequeño, del último byte al resultado definitivo,
-- con los masivos en proceso. Entran los pequeños cuyo último byte llegó mientras
-- algún masivo estaba en proceso (del primer byte del primer masivo al resultado
-- del último). Sin masivos (L-2) entran todos los de la medición: es la línea base.
-- Un pequeño sin resultado al cierre cuenta con la latencia hasta el cierre.
\set ON_ERROR_STOP on
\pset footer off
\pset null '—'

\echo
\echo '== Ejecución'
SELECT id, asr, escenario, variables, inicio, fin, cierre, fin - inicio AS medicion, valida, motivo
FROM registro.corrida WHERE id = :'corrida';

CREATE TEMP VIEW w AS
SELECT arranque, inicio, fin, cierre, coalesce((variables ->> 'masivos')::int, 0) AS n_masivos,
       variables ->> 'ruteo' AS ruteo
FROM registro.corrida WHERE id = :'corrida';

CREATE TEMP VIEW masivos AS
SELECT a.* FROM registro.archivos a, w WHERE a.tipo = 'masivo' AND a.enviado BETWEEN w.arranque AND w.cierre;

CREATE TEMP VIEW en_proceso AS
SELECT min(recepcion_inicio) AS desde, coalesce(max(resultado_en), (SELECT cierre FROM w)) AS hasta,
       count(*) AS masivos, count(resultado_en) AS terminados
FROM masivos;

CREATE TEMP VIEW pequenos AS
SELECT a.llave, a.recibido, a.resultado_en, a.acuse_ms, a.carril,
       coalesce(a.latencia_ms, round(extract(epoch FROM w.cierre - a.recibido) * 1000)) AS latencia_ms,
       a.resultado IS NULL AS sin_resultado,
       (SELECT masivos FROM en_proceso) > 0
         AND a.recibido BETWEEN (SELECT desde FROM en_proceso) AND (SELECT hasta FROM en_proceso) AS con_masivos
FROM registro.archivos a, w
WHERE a.tipo = 'pequeno' AND a.caso = 'valido' AND a.estado_acuse = 'ACUSADO'
  AND a.recibido BETWEEN w.inicio AND w.fin;

CREATE TEMP VIEW medidos AS
SELECT * FROM pequenos WHERE con_masivos OR (SELECT n_masivos FROM w) = 0;

\echo
\echo '== Criterio de ASR-L04'
SELECT CASE WHEN (SELECT n_masivos FROM w) = 0 THEN 'Línea base (L-2) · p99 de los pequeños sin masivos'
            ELSE 'Medida · p99 de los pequeños con ' || (SELECT n_masivos FROM w) || ' masivos en proceso ≤ 3 s' END AS criterio,
       count(*) AS casos,
       count(*) FILTER (WHERE latencia_ms <= 3000) AS cumplen,
       count(*) FILTER (WHERE latencia_ms > 3000) AS fallan,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY latencia_ms)::numeric) AS p99_ms,
       CASE WHEN count(*) = 0 THEN 'NO APLICA'
            WHEN (SELECT n_masivos FROM w) = 0 THEN 'OBSERVA'
            WHEN percentile_cont(0.99) WITHIN GROUP (ORDER BY latencia_ms) <= 3000 THEN 'PASA' ELSE 'FALLA' END AS veredicto
FROM medidos;

\echo
\echo '== Latencia de los pequeños (ms), del último byte al resultado definitivo'
SELECT CASE WHEN con_masivos THEN 'con masivos en proceso' ELSE 'sin masivos en proceso' END AS tramo,
       count(*) AS pequenos, count(*) FILTER (WHERE sin_resultado) AS sin_resultado,
       round(percentile_cont(0.50) WITHIN GROUP (ORDER BY latencia_ms)::numeric) AS p50,
       round(percentile_cont(0.95) WITHIN GROUP (ORDER BY latencia_ms)::numeric) AS p95,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY latencia_ms)::numeric) AS p99,
       max(latencia_ms) AS maximo
FROM pequenos GROUP BY con_masivos ORDER BY con_masivos DESC;

\echo
\echo '== Referencia del enunciado: acuse de los pequeños frente a 1 s'
SELECT count(*) AS acuses,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY acuse_ms)::numeric) AS p99_ms,
       max(acuse_ms) AS maximo_ms,
       count(*) FILTER (WHERE acuse_ms > 1000) AS mayores_de_1s
FROM pequenos;

\echo
\echo '== Masivos: carril, subida, acuse y duración frente a la referencia de 5 min'
SELECT llave, carril, registros, bytes,
       recibido - recepcion_inicio AS subida, acuse_ms,
       resultado_en - recibido AS duracion, resultado, rechazadas, invalidas,
       CASE WHEN resultado_en IS NULL THEN 'sin resultado al cierre'
            WHEN resultado_en - recibido <= interval '5 minutes' THEN '≤ 5 min' ELSE '> 5 min' END AS referencia
FROM masivos ORDER BY recibido;

\echo
\echo '== Masivos en proceso'
SELECT * FROM en_proceso;

\echo
\echo '== Latencia por minuto de la medición (p99 de los pequeños que llegaron en ese minuto)'
SELECT date_trunc('minute', recibido) AS minuto, count(*) AS pequenos,
       round(percentile_cont(0.99) WITHIN GROUP (ORDER BY latencia_ms)::numeric) AS p99_ms, max(latencia_ms) AS max_ms
FROM pequenos GROUP BY 1 ORDER BY 1;
