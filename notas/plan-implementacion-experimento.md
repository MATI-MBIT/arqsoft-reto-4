# Plan e implementación del experimento de Orquídea

2026-11-07. Sale de `docs/experiments.md`, de los ADR-001 a ADR-008 de
`docs/modelos/adrs-orquidea.md` y de la vista de despliegue DG-DEP-002. Toma la
estructura de los prototipos del reto 1 y del reto 2 (`MATI-MBIT/arqsoft-reto-1`
y `MATI-MBIT/arqsoft-reto-2`): monorepo Gradle, `deploy/`, `load/plan.tsv`, un
solo orquestador, un registro de eventos como fuente del veredicto y Grafana para
ver la ejecución.

Este documento no cambia el diseño del experimento. Donde el diseño dejaba un
hueco que el código no podía dejar, la sección 2 fija un supuesto con su razón, y
cada supuesto se cambia con una variable, sin tocar código.

---

## 1. La estrategia que se heredó de los retos 1 y 2

| Principio | Cómo queda en este repositorio |
|---|---|
| **Qué se corre es dato; cómo se corre es código** | `load/plan.tsv` tiene una fila por ejecución con su escenario y su pregunta; `load/experimento.sh` es el único orquestador. Agregar un punto de medida es agregar una fila |
| **El veredicto sale de un registro de eventos, no de las métricas** | Cada servicio anota en `registro.evento` el instante del hecho y la llave del archivo, en lote y fuera del camino medido. Prometheus agrega y pierde la llave: sirve para ver, no para decidir |
| **Cruce uno a uno por identificador** | Las vistas de `deploy/postgres/03-vistas.sql` cruzan cada archivo enviado con su recepción, acuse, pendiente, ventana y resultado. Las leen el veredicto, los tableros y las gráficas, así que todos cuentan igual |
| **Un oráculo anotado antes del estímulo** | El remitente simulado anota `archivo.enviado` con lo que se espera del archivo (TOTAL, PARCIAL, RECHAZO o RECHAZO_CRIPTOGRAFICO) antes de enviarlo |
| **Todo reloj que decide es de un contenedor** | t0 (último byte) lo anota la recepción; el t0 de cada falla, el inyector, que corre en Docker. k6 corre en el host y solo da referencias |
| **Ciclo fijo por ejecución** | preparar → calentar → medir → resolver (masivos y ventanas) → cerrar → veredicto, con chequeo de validez al final |
| **Validez explícita** | Una ejecución con un hueco de más de 30 s en la carga, una suspensión del equipo o más de 1 % de solicitudes fallidas en k6 queda contaminada y se repite |
| **Evidencia en capas** | Veredicto en texto por ejecución, eventos crudos en CSV, enlace al tablero fijado en la ventana de la ejecución y gráficas SVG desde el registro |

---

## 2. Supuestos fijados

| # | Hueco | Supuesto | Por qué | Variable |
|---|---|---|---|---|
| P-1 | Cuánto cuesta aplicar las reglas cambiarias a una declaración | **1 ms de CPU por declaración** | Sin costo, un masivo de 100 000 registros dura segundos y el aislamiento de ASR-L04 no se pone a prueba. Con 1 ms, un masivo dura ~105 s en un hilo y 5 masivos ocupan el carril B unos 3 min, dentro de la referencia de 5 min | `COSTO_REGLA_US` |
| P-2 | El formato de la firma | Firma envuelta sobre los bytes que van desde el inicio hasta `<firma>`, con ECDSA P-256; no XMLDSig con canonicalización | Mide lo que pregunta SUP-01: resumir y verificar en streaming sobre los mismos bytes que se custodian. Canonicalizar queda fuera (amenaza a la validez) | — |
| P-3 | Tamaño de cada registro | ~500 bytes, para que un masivo de 100 000 registros pese ~49 MB, cerca del tope de 50 MB | El tope de la respuesta de BCR | — |
| P-4 | El umbral en bytes entre carriles | **30 000 bytes**: un pequeño de 50 registros pesa ~27 KB | La pregunta sigue abierta con BCR | `UMBRAL_BYTES` |
| P-5 | Pool y CPU de cada carril | Carril A: 8 consumidores y 2 CPU. Carril B: 5 consumidores y 3 CPU. Recepción: 2 CPU | Un consumidor por masivo de L-1 en el carril B; el carril A con holgura para 400 pequeños/min | `CONCURRENCIA_A`, `CONCURRENCIA_B`, `CPUS_CARRIL_A`, `CPUS_CARRIL_B` |
| P-6 | Timeout del adaptador del RNIC | **1 s** | La latencia de D-4 (3 s) lo supera con holgura | `RNIC_TIMEOUT_MS`, `LATENCIA_MS` |
| P-7 | Sondeo del monitor | **Cada 1 s, timeout de 800 ms, caído con 3 fallos seguidos**. Una llamada vencida de un adaptador cuenta como un fallo | Detecta en ~3 s; la detección se reporta, no tiene umbral | `SONDEO_MS`, `SONDEO_TIMEOUT_MS`, `SONDEO_N` |
| P-8 | Días de reintento | **2 ventanas** | El valor de prueba de `experiments.md` | `DIAS` (fila del plan) |
| P-9 | Ritmo del reprocesador | **5 000 declaraciones por segundo** | El tope que fija el reprocesador y no el regreso del tercero (C-04) | `RITMO_DECLARACIONES_S` |
| P-10 | Estampa | Desfase tolerado: 5 min hacia el futuro; antigüedad: 24 h | La pregunta sigue abierta con BCR | `ESTAMPA_DESFASE_S`, `ESTAMPA_ANTIGUEDAD_H` |
| P-11 | Vigencia de la copia local de la CRL | 1 h; S-2 la vence con un gancho de prueba al caer la ECD | Simula la ECD caída más allá del refresco | `REVOCACION_VIGENCIA_S` |
| P-12 | Qué es un archivo válido | El que el remitente anota como TOTAL o PARCIAL: se habría aceptado, total o parcialmente, con el RNIC disponible | Glosario. Un 5 % de las declaraciones lleva una cédula con un nombre que no le corresponde | `INVALIDAS` |
| P-13 | Cuándo un archivo se procesó «dentro de los días» | Tiene resultado igual al esperado, con exactamente tantas declaraciones rechazadas como inválidas llevaba, y si pasó por pendientes se resolvió con menos intentos que días | Es la medida de ASR-D01 escrita como consulta | — |
| P-14 | Arribo estocástico de los pequeños | k6 dispara a 4 veces la tasa y envía cada llegada con probabilidad 1/4 | Binomial, cercano a Poisson, sin perder la tasa media | `TASA` |
| P-15 | Qué pequeños entran a la medida de L04 | Los que terminaron de llegar mientras algún masivo estaba en proceso (del primer byte del primer masivo al resultado del último). Uno sin resultado al cierre cuenta con la latencia hasta el cierre | Es «con 5 masivos en proceso» | — |
| P-16 | Cuánto dura cada ejecución | D-1 dura 20 min, como el diseño; D-2 a D-5, 8 min de carga; L-1 a L-4, 10 min; S-1, 5 min; S-2, 6 min. Antes de cada una, 1 min de calentamiento | Retroalimentación corta, con cientos de archivos por escenario | `DURACION_S`, `WARMUP_S` |

---

## 3. Qué se construyó

| Entra | Se simula | Queda fuera |
|---|---|---|
| Recepción con custodia, verificación en streaming, llave de idempotencia, evidencia firmada y enrutador por tamaño · carriles A y B · monitor de terceros · reprocesador de la ventana nocturna · Kafka KRaft · PostgreSQL · registro de eventos · carga, inyección de fallas y veredicto | El RNIC (cédula contra nombre, determinista) · la ECD (CRL) · los remitentes (arman, firman y envían) · el costo de las reglas cambiarias | TLS mutuo (R-14) · STM, ANTA y MAMC (basta un tercero) · reglas cambiarias reales · entrega a las áreas · la caída de la cola misma |

```
├── services/
│   ├── comun/         Registro de eventos, formato y firma, lector en streaming, verificador,
│   │                  caché de revocación, evidencia, adaptador del RNIC, cliente del monitor,
│   │                  procesador idempotente, tópicos de Kafka
│   ├── pki/           Genera la PKI de prueba (make pki): raíz, autoridad no aceptada, estampa,
│   │                  remitentes, revocado, CRL y llave de evidencia de BCR
│   ├── recepcion/     Recepción y acuse (ADR-001, 004, 005, 006, 007, 008)
│   ├── carril/        Una imagen, dos contenedores: carril A y carril B (ADR-005)
│   ├── monitor/       Monitor de terceros con ping/echo y reportes de llamadas vencidas (ADR-002)
│   ├── reprocesador/  Ventana nocturna: pendientes, reprogramación, notificación (ADR-003, 007)
│   └── simuladores/   Una imagen, tres papeles: ROL=rnic | ecd | remitente
├── deploy/
│   ├── docker-compose.yml   14 contenedores: Orquídea, simuladores, Kafka, PostgreSQL, Toxiproxy,
│   │                        inyector, Prometheus y Grafana
│   ├── postgres/            Esquema (con evidencia de solo anexar) y vistas del cruce
│   ├── inyector/            Anota el t0 de cada falla y le pide a Toxiproxy el corte o la latencia
│   ├── toxiproxy/           Los proxies del RNIC y de la ECD
│   └── observabilidad/      Prometheus, fuentes de Grafana y generador de los tres tableros
├── load/
│   ├── k6/            pequenos.js (Ambiente A) · masivos.js
│   ├── plan.tsv       Una fila por ejecución, con su escenario y su pregunta
│   └── experimento.sh El orquestador
├── analisis/
│   ├── d01.sql · l04.sql · s06.sql   El veredicto de cada criterio, en SQL
│   ├── graficas.py                   Gráficas SVG de evidencia para docs/
│   └── resultados/                   Una carpeta por ejecución
└── Makefile
```

### Las piezas que deciden el resultado

**La recepción** (`RecepcionController`). Lee el archivo una sola vez con
`FlujoCustodiado`, que sobre los mismos bytes los copia a la custodia, calcula el
hash completo y el resumen firmado, mientras `LectorArchivo` valida la estructura
en streaming, sin DTD ni entidades externas. Con el último byte (t0) termina la
verificación: firma, cadena, firmante, estampa y revocación. Si pasa, registra la
llave con unicidad en la misma inserción que el archivo, anexa la evidencia
firmada por BCR, publica en el tópico de su carril y espera la confirmación del
bróker antes de acusar. Si la revocación no se pudo verificar, acusa en
«verificación pendiente» y deja el archivo en pendientes, sin carril.

**El procesador** (`Procesador`, compartido por los carriles y el reprocesador).
Recalcula el hash de la custodia y lo compara con el de la recepción. Lee las
declaraciones en lotes; antes de cada lote consulta al monitor, y si el RNIC está
caído o el adaptador vence su timeout, deja el archivo pendiente desde ese lote.
Escribe cada declaración con `ON CONFLICT DO NOTHING` y el resultado definitivo
con un `UPDATE … WHERE resultado IS NULL`: solo quien gana emite el efecto, y
`operacion.efecto_resultado` no tiene clave única, para que un duplicado quede a
la vista.

**El monitor** (`MonitorApp`). Sondea `/eco` del RNIC y de la ECD a través de
Toxiproxy. CAIDO con 3 fallos seguidos, DE_VUELTA con el primer eco y DISPONIBLE
con el siguiente. Los servicios lo consultan cada 200 ms.

**La ventana** (`Ventana`). Al abrir toma el final de cada partición del tópico
`pendientes` y lee hasta ahí; lo que reprograma va después del corte, así que
queda para la ventana siguiente. Al reprogramar por segunda vez notifica, y el
archivo sigue pendiente.

**El inyector**. Corta el RNIC o la ECD (`enabled=false` en Toxiproxy), les
agrega latencia, los restablece, y en D-5 vuelve a publicar en Kafka, sin pasar
por Orquídea, el mensaje de archivos ya resueltos.

---

## 4. La observabilidad

Tres tableros aprovisionados en Grafana (`make tablero`), uno por ASR, cada uno
con dos fuentes:

- **El registro de eventos**, en PostgreSQL. Arriba, el veredicto en vivo sobre
  la ventana del tablero (válidos procesados, rechazados por la caída,
  duplicados, p99 de los pequeños, catálogo aceptado, aceptados antes de
  verificar). En el centro, el recorrido de los archivos y, en L04, un punto por
  archivo pequeño con la línea de 3 s. Abajo, las fallas con su detección, las
  ventanas y los masivos.
- **Prometheus**, que raspa cada 2 s los servicios y recibe lo que empuja k6: el
  estado de cada tercero según el monitor, los pendientes, las llamadas vencidas,
  la CPU de cada contenedor frente a su límite, los archivos en proceso por carril
  y los rechazos por causa.

Las anotaciones marcan cada falla inyectada (rojo), cada restablecimiento
(verde), cada ventana (azul), cada caída y regreso que declara el monitor, y en
L04 cada masivo que empieza a llegar.

Cada ejecución deja en su carpeta `tablero.txt` con el enlace al tablero fijado
en su ventana de tiempo, para volver a la evidencia visual después.

---

## 5. Validez de cada ejecución

El orquestador la da por inválida si pasa cualquiera de estas cosas:

- **Hueco en la carga** de más de 30 s entre dos archivos recibidos: la máquina se
  congeló.
- **Suspensión del equipo** durante la ejecución, según `pmset -g log`. Con
  batería el orquestador no arranca, y `caffeinate` impide la suspensión por
  inactividad. La primera prueba manual de un masivo, el 2026-11-07, midió 10 min
  en vez de 105 s porque el equipo se suspendió: por eso esta regla.
- **Más de 1 % de solicitudes fallidas** en k6.

Una ejecución inválida se descarta (`…-contaminada`) y se repite una vez.

---

## 6. Los riesgos que quedan

| Riesgo | Qué se hace |
|---|---|
| Catorce contenedores, Kafka y cinco JVM en un portátil se pelean por CPU, disco y memoria | Límites de CPU por contenedor, heap acotado y el panel de CPU de L04. Si L-1 falla, hay que descartar que la contienda venga del equipo |
| Un masivo en el carril B puede caer en la misma partición que otro y esperar | 12 particiones y 5 consumidores; la duración de cada masivo se reporta |
| El registro en memoria pierde eventos si un contenedor muere | El inyector no mata contenedores: corta la red con Toxiproxy |
| Abrir Grafana durante una medición carga la máquina | `make up` precalienta Grafana; las capturas se toman al final |

---

## 7. Cambios que esto pide a `docs/experiments.md`

No se aplicaron: van con la regla de flujo de `CLAUDE.md` y la skill de escritura.

1. Montaje: sumar `remitente-sim` (arma y firma los archivos; k6 marca el ritmo),
   `reto4-inyector`, Prometheus y Grafana. `generador-carga` es k6 en el host.
2. Montaje: el reprocesador no notifica la verificación pendiente aparte; la
   resuelve en la misma ventana que los demás pendientes (ADR-007).
3. Datos: el 5 % de declaraciones inválidas vuelve PARCIAL al archivo (R-7); un
   archivo válido es el que se habría aceptado total o parcialmente.
4. D-5: los eventos repetidos se inyectan directo en Kafka.
5. S-3 no es una ejecución aparte: cada ejecución audita la evidencia al cerrar y
   el criterio aparece en los veredictos de D01 y S06.
6. Declarar los supuestos P-1 a P-16, en especial P-1 (costo de las reglas) y P-2
   (firma envuelta sobre bytes, sin canonicalización).
