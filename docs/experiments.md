---
title: Experimento
nav_order: 9
helix_section: "Experiments"
---

# Experimento local para los tres ASR críticos

El experimento pone a prueba, en un solo equipo con Docker, las decisiones de arquitectura de los tres ASR críticos: [ASR-D01](quality-attributes.md#asr-d01), [ASR-L04](quality-attributes.md#asr-l04) y [ASR-S06](quality-attributes.md#asr-s06). La disponibilidad ante la caída de un sistema externo es el foco y tiene más escenarios.

El experimento no tiene que cumplir los ASR. Si una medida no se alcanza, se reporta el resultado con su análisis.

## 1. Resumen por ASR

| ASR | Hipótesis (la medida del ASR) | Decisiones que se prueban | Escenarios |
|---|---|---|---|
| ASR-D01 · disponibilidad, recuperación | Con un sistema externo caído, 100 % de los archivos válidos procesados dentro de los días de reintento | [ADR-001 a ADR-004](modelos/adrs-orquidea.md) | D-1 a D-5 |
| ASR-L04 · latencia | Con 5 masivos en proceso, p99 ≤ 3 s por archivo pequeño | [ADR-005](modelos/adrs-orquidea.md) | L-1 a L-4 |
| ASR-S06 · seguridad, prevención | 0 archivos aceptados sin firma, firmante, cadena, estampa y revocación verificadas | [ADR-006, ADR-007 y ADR-008](modelos/adrs-orquidea.md) | S-1 a S-3 |

Quedan fuera la autenticación del canal (TLS mutuo), la integración con el Banco de la República, las reglas cambiarias completas y la entrega a las áreas de BCR.

## 2. Montaje

Todo corre en Docker Compose sobre un solo equipo. El diagrama está en la vista de despliegue (DG-DEP-004).

| Contenedor | Papel | ASR |
|---|---|---|
| `recepcion` | Recibe el archivo por HTTP, lo custodia, verifica la firma, registra su llave, acusa y lo publica en el tópico de su carril según el tamaño en bytes | D01, L04, S06 |
| `kafka` | Cola durable en modo KRaft, nodo único, con los tópicos `carril-a`, `carril-b` y `pendientes` | D01, L04 |
| `carril-a` | Procesa pequeños con su propio pool y su propio límite de CPU | L04 |
| `carril-b` | Procesa masivos por lotes, con su propio pool y su propio límite de CPU | L04 |
| `monitor-terceros` | Sondea con ping/echo al sistema externo simulado y publica su estado: disponible, caído o de vuelta | D01 |
| `reprocesador` | Corre la ventana de reproceso al llamar a un endpoint de prueba; reprograma lo pendiente y notifica lo que agotó los reintentos, sin sacarlo de los pendientes | D01, S06 |
| `tercero-rnic-sim` | RNIC simulado: una tabla de personas ficticias que cruza cédula contra nombre | D01 |
| `toxiproxy` | Proxy hacia el RNIC simulado, para cortarlo o agregarle latencia en mitad de la carga | D01 |
| `ecd-sim` | Entidad de certificación de prueba, con su lista de revocación y su estampa cronológica | S06 |
| `postgres` | Archivos, llaves, declaraciones, pendientes, resultados y el registro de evidencia de solo anexar | D01, S06 |
| `generador-carga` | k6: archivos pequeños a ritmo constante y masivos a demanda | Todos |

Basta un sistema externo simulado: STM, ANTA y MAMC se comportan igual ante una caída.

### Datos de prueba

| Conjunto | Contenido |
|---|---|
| Archivos pequeños | 1 a 50 registros, firmados con un certificado vigente de `ecd-sim` |
| Archivos masivos | 100 000 registros y hasta 50 MB, firmados |
| Personas ficticias | Tabla del RNIC simulado. Un 5 % de las declaraciones lleva una cédula que no corresponde al nombre `[SUPUESTO → equipo]`: esos archivos no son válidos y tienen que rechazarse por la regla, no por la caída |
| Catálogo criptográfico | Un archivo por caso: firma alterada, firma envuelta, firmante distinto del remitente, certificado revocado, certificado de una autoridad no aceptada, estampa desfasada y estampa vencida |

Cada archivo deja en `postgres` sus marcas de tiempo, con el reloj del equipo: el último byte recibido, el acuse, la llegada al carril, el paso a pendiente, cada ventana, la notificación y el resultado definitivo.

## 3. ASR-D01 · Ningún archivo se rechaza por la caída de un sistema externo

**Hipótesis.** Con el RNIC simulado caído durante la carga, el 100 % de los archivos válidos se procesa dentro de los días de reintento.

**Tácticas que se prueban.** Pipeline asíncrono con acuse temprano (ADR-001). Detección: monitor con ping/echo a cada tercero y timeout por llamada (ADR-002). Recuperación: pendientes durables, reintento diferido a la ventana nocturna y degradación con gracia (ADR-003). Llave de idempotencia en transacción local (ADR-004).

La detección va primero porque sin ella no hay recuperación: el monitor es el que dice que el RNIC cayó, para dejar pendiente, y que volvió, para reprocesar. Por eso cada escenario mide también cuándo la detecta.

Las caídas de horas se comprimen: una «noche» dura lo que tarde la prueba en disparar la ventana a mano.

| ID | Escenario | Qué se hace | Qué se observa |
|---|---|---|---|
| D-1 | Caída y regreso | 400 pequeños por minuto durante 20 min; a los 5 min `toxiproxy` corta el RNIC; a los 15 min se restablece y se dispara la ventana | Válidos aceptados al cierre de la ventana; rechazos por la caída; cédulas que no corresponden, rechazadas por la regla |
| D-2 | Sigue caído en la ventana | Como D-1, pero la primera ventana corre con el RNIC caído y la segunda con el RNIC de vuelta | Pendientes reprogramados; válidos aceptados en la segunda ventana |
| D-3 | Reintentos agotados | El RNIC sigue caído durante 2 ventanas, el máximo de días fijado solo para la prueba `[SUPUESTO → BCR]`; después se restablece | Archivos notificados que siguen pendientes y quedan aceptados en la ventana siguiente al regreso |
| D-4 | Detección de lentitud y regreso | `toxiproxy` agrega al RNIC una latencia mayor que el timeout del adaptador; luego la quita | El monitor lo marca caído y después de vuelta; las declaraciones quedan pendientes, sin rechazos ni hilos bloqueados |
| D-5 | Reenvío y reproceso sin duplicar | Con archivos pendientes, se reenvían 50 con la misma llave y 50 nuevos; luego se repiten eventos del reproceso | Reenvíos rechazados como duplicados; nuevos recibidos; ningún resultado escrito dos veces |

| Métrica | Cómo se calcula |
|---|---|
| **Archivos válidos procesados** (la medida) | Válidos procesados dentro de los días de reintento ÷ válidos enviados, por conciliación |
| Rechazos por la caída | Rechazos cuya causa es la falta de respuesta del externo |
| Duplicados | Archivos con la misma llave aceptados más de una vez; resultados escritos dos veces |
| Pendientes y ventana | Mensajes en `pendientes`; duración de cada ventana |
| Detección de la caída y del regreso | De la caída (o del regreso) en `toxiproxy` al cambio de estado en el monitor; también, cuántas declaraciones alcanzan a llamar al tercero caído antes de que se detecte |

**Criterio.** Éxito: 100 % de los archivos válidos procesados dentro de los días de reintento en D-1, D-2 y D-4. Fracaso: un solo archivo válido rechazado, perdido o sin procesar en ese plazo. En D-3 el RNIC sigue caído más allá de los días de reintento: queda fuera de la medida, y se reporta que lo notificado se procesa al volver. Se reportan además los duplicados de D-5, la profundidad de pendientes, la duración de la ventana y el tiempo de detección.

## 4. ASR-L04 · Aislamiento de carga entre archivos masivos y pequeños

**Hipótesis.** Con 5 masivos en proceso, el p99 de los archivos pequeños es ≤ 3 s, desde que el archivo termina de llegar hasta que su resultado está disponible.

**Tácticas que se prueban.** Carriles aislados (bulkhead), enrutamiento por tamaño al recibir y un pool con su política por carril (ADR-005).

| ID | Escenario | Qué se hace | Qué se observa |
|---|---|---|---|
| L-1 | Con 5 masivos | 400 pequeños por minuto durante 10 min, con 5 masivos lanzados al minuto 2 | p99 de los pequeños |
| L-2 | Línea base (complemento) | La misma carga, sin masivos | p99 de los pequeños sin masivos, para el margen de 10 % que propuso BCR |
| L-3 | Control sin carriles | Como L-1, con un solo tópico y un solo pool para todo | p99 de los pequeños sin aislamiento |
| L-4 | Barrido hasta el quiebre | Como L-1, con 6, 8 y 10 masivos | Número de masivos en que el p99 de los pequeños pasa de 3 s |

| Métrica | Cómo se calcula |
|---|---|
| **p99 de los pequeños con 5 masivos** (la medida) | Del último byte al resultado definitivo, en L-1 |
| Margen frente a la línea base | p99 de L-1 ÷ p99 de L-2 |
| Efecto del aislamiento | p99 de L-1 frente a L-3 |
| Referencias del enunciado | Acuse frente a 1 s; duración de cada masivo frente a 5 min |

**Criterio.** Éxito: p99 ≤ 3 s en L-1. Fracaso: p99 > 3 s en L-1. Se reportan además el margen frente a L-2, el contraste con L-3 y el quiebre de L-4.

## 5. ASR-S06 · Ningún archivo se acepta sin validación criptográfica completa

**Hipótesis.** Ningún archivo con firma, firmante, cadena, estampa o revocación inválida, o que no se pudo verificar, llega a aceptación, con la ECD disponible o caída.

**Tácticas que se prueban.** Autenticar la firma y el firmante, verificar la integridad y validar la entrada (ADR-006); revocación en caché con verificación pendiente (ADR-007); registro de evidencia firmado (ADR-008).

BCR aceptó que este ASR es difícil de probar y que basta con que la arquitectura lo muestre con claridad. La prueba es mínima.

| ID | Escenario | Qué se hace | Qué se observa |
|---|---|---|---|
| S-1 | Catálogo de archivos inválidos | Se envía cada archivo del catálogo, mezclado con la carga de L-2 | Inválidos aceptados; causa de rechazo de cada uno |
| S-2 | ECD caída con la revocación vencida | Se apaga `ecd-sim` y se vence la lista en caché; luego se restablece y se dispara la ventana | Archivos en verificación pendiente, sin resultado; verificados y resueltos en la ventana |
| S-3 | Evidencia de lo aceptado | Al final de L-2 y de D-1 se cruza cada aceptado contra el registro de evidencia | Aceptados con su entrada firmada y el mismo hash de los bytes procesados |

| Métrica | Cómo se calcula |
|---|---|
| **Archivos aceptados sin verificación completa** (la medida) | Archivos del catálogo, o en verificación pendiente, con aceptación total o parcial |
| Aceptados sin evidencia | Aceptados sin entrada firmada o con otro hash |

**Criterio.** Éxito: 0 archivos aceptados sin verificación completa en S-1 y S-2. Fracaso: uno solo. Se reportan además la causa de rechazo de cada caso y el resultado de S-3.

## 6. Amenazas a la validez

- **Tiempos optimistas.** En local no hay saltos de red ni balanceadores: un p99 ≤ 3 s en local no prueba los 3 s en producción. Por eso se reporta también el margen frente a la línea base, que compara dos corridas en el mismo equipo.
- **Recursos compartidos.** Los carriles comparten disco, memoria y bus, aunque tengan límites de CPU propios. Si L-1 falla, hay que descartar que la contienda venga del equipo y no del diseño.
- **Horas comprimidas.** Las caídas de horas y la ventana nocturna se simulan en minutos. No se mide cuánto tarda una ventana real con 500 000 declaraciones acumuladas.
- **Externos simulados.** Una caída real puede ser parcial o devolver datos errados, y eso el monitor no lo detecta.
- **Cola de un nodo.** No se prueba la caída de la cola misma.
- **Pocos masivos.** Con 5 por corrida, su duración se reporta como máximo, no como percentil.

## 7. Parámetros que dependen de BCR

Mientras BCR no los fije, el experimento usa valores de prueba y el informe de resultados los declara: la hora de la ventana (se dispara a mano), los días de reintento antes de notificar (2), el umbral en bytes entre carriles (el tamaño de un archivo de 50 registros), el timeout del adaptador, el periodo de sondeo del monitor y el desfase y la antigüedad tolerados en la estampa. Las preguntas están en la [página de ASR](quality-attributes.md#6-abierto).
