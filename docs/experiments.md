---
title: Experimento
nav_order: 9
helix_section: "Experiments"
---

# Experimento local para los tres ASR críticos

El experimento pone a prueba, en un solo equipo con Docker, las decisiones de arquitectura de los tres ASR críticos: [ASR-D01](quality-attributes.md#asr-d01), [ASR-L04](quality-attributes.md#asr-l04) y [ASR-S06](quality-attributes.md#asr-s06). La disponibilidad ante la caída de un sistema externo es el foco y tiene más escenarios.

El experimento no tiene que cumplir los ASR. Si una medida no se alcanza, se reporta el resultado con su análisis.

## 1. Resumen por ASR

| ASR | Hipótesis | Decisiones que se prueban | Escenarios |
|---|---|---|---|
| ASR-D01 · disponibilidad, recuperación | H1: acusar sin esperar a los terceros y dejar en pendientes durables lo que necesita al caído hace que el 100 % de los archivos válidos quede procesado dentro de los días de reintento | [ADR-001 a ADR-004](modelos/adrs-orquidea.md) | D-1 a D-5 |
| ASR-L04 · latencia | H2: dos carriles aislados, con enrutamiento por tamaño, mantienen el p99 de los pequeños en ≤ 3 s con 5 masivos en proceso | [ADR-005](modelos/adrs-orquidea.md) | L-1 a L-4 |
| ASR-S06 · seguridad, prevención | H3: verificar en el mismo paso que se lee el archivo y retener lo que no se pudo verificar hace que 0 archivos lleguen a aceptación sin verificación completa | [ADR-006, ADR-007 y ADR-008](modelos/adrs-orquidea.md) | S-1 a S-3 |

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

### La hipótesis de diseño

#### H1 — Ningún archivo válido se pierde ni se rechaza con el RNIC caído

**Creemos que** acusar sin esperar a los terceros, detectar la caída con un monitor de ping/echo y un timeout por llamada, y dejar en pendientes durables lo que necesita al tercero caído, con una llave de idempotencia, **para** un sistema externo que deja de responder o responde con latencia extrema durante la carga ([ASR-D01](quality-attributes.md#asr-d01)), **dará como resultado** que la recepción siga acusando y que cada archivo válido termine procesado en una ventana nocturna, **lo cual se validará cuando** el 100 % de los archivos válidos de D-1, D-2 y D-4 quede procesado dentro de los días de reintento, sin un solo rechazo por la caída. En D-5, ningún resultado puede escribirse dos veces.

- **Variables independientes:** el estado del RNIC simulado, que `toxiproxy` deja disponible, cortado o más lento que el timeout del adaptador, y el estado del RNIC en cada ventana: caído o de vuelta.
- **Variables dependientes:** los archivos válidos procesados frente a los enviados; los rechazos cuya causa es la caída; los resultados escritos dos veces; la demora del monitor en detectar la caída y el regreso, y la profundidad de los pendientes.

Fundamento: el acuse sale después de custodiar el archivo y registrar su llave, sin llamar a ningún tercero (ADR-001), así que la caída no frena la recepción. El monitor y el timeout por llamada convierten la caída y la lentitud en una misma señal (ADR-002). Lo que no se pudo enriquecer no se rechaza: espera en un tópico durable hasta la ventana, que lo reprocesa si el tercero volvió y lo reprograma si sigue caído (ADR-003). La llave impide que un reenvío o un mensaje repetido produzca un segundo resultado (ADR-004).

Lo que la refutaría, cualquiera de cuatro casos:

- Un archivo válido rechazado por la caída del RNIC, en lugar de quedar pendiente.
- Un archivo pendiente perdido, o sin procesar al cerrar la primera ventana con el RNIC de vuelta.
- Un RNIC lento que deja hilos bloqueados o archivos rechazados, en vez de tratarse como caído.
- Un archivo con dos resultados definitivos, por un reenvío o por un mensaje repetido.

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

### La hipótesis de diseño

#### H2 — Los pequeños no esperan detrás de los masivos

**Creemos que** separar el procesamiento en dos carriles aislados, cada uno con su tópico, su pool de hilos y su límite de CPU, y enrutar cada archivo por su tamaño en bytes al recibirlo, **para** archivos pequeños que llegan mientras 5 masivos están en proceso ([ASR-L04](quality-attributes.md#asr-l04)), **dará como resultado** que cada pequeño se resuelva sin esperar a que avancen los masivos, **lo cual se validará cuando** el p99 de los pequeños de L-1 sea ≤ 3 s, del último byte al resultado definitivo. El control sin carriles de L-3, con la misma carga, debe pasar de 3 s: si no pasa, los carriles no explican el resultado.

- **Variables independientes:** el enrutamiento, por tamaño hacia dos carriles o único hacia un solo tópico y un solo pool, y el número de masivos en proceso: 0, 5, 6, 8 y 10.
- **Variables dependientes:** el p99 y el p50 de los pequeños; los pequeños que pasan de 3 s; el margen de L-1 frente a la línea base de L-2; la CPU y los archivos en proceso de cada carril, y la duración de cada masivo.

Fundamento: un masivo ocupa un hilo durante minutos, porque cada una de sus declaraciones pasa por las reglas cambiarias. En una sola fila, los pequeños que caen detrás de él esperan aunque cada uno se procese en milisegundos. Con dos carriles, el masivo solo consume los hilos y la CPU del carril B, y el carril A atiende a los pequeños a su ritmo (ADR-005). La recepción decide el carril contando los bytes mientras el archivo llega, sin abrirlo, así que enrutar no agrega espera.

Lo que la refutaría, cualquiera de tres casos:

- Un p99 de los pequeños mayor que 3 s en L-1.
- Un control sin carriles (L-3) que también quede bajo 3 s: entonces el aislamiento no es lo que sostiene el resultado, y el experimento no prueba ADR-005.
- Un archivo enrutado al carril equivocado que se pierde o no llega a resultado definitivo.

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

### La hipótesis de diseño

#### H3 — Nada llega a aceptación sin la verificación criptográfica completa

**Creemos que** verificar la firma, el firmante, la cadena, la estampa y la revocación en el mismo paso que lee el archivo, retener el resultado de todo archivo cuya revocación no se pudo comprobar y anexar una evidencia firmada de cada verificación, **para** un archivo cuya firma no es válida o no se puede verificar ([ASR-S06](quality-attributes.md#asr-s06)), **dará como resultado** que ninguno llegue a aceptación: el inválido se rechaza con su causa y el que no se pudo verificar espera en verificación pendiente, **lo cual se validará cuando** 0 archivos del catálogo de S-1 y 0 de los acusados en verificación pendiente de S-2 tengan aceptación total o parcial antes de verificarse. En S-3, cada aceptado debe tener su entrada de evidencia firmada, con el hash de los bytes que se procesaron.

- **Variables independientes:** el caso del catálogo criptográfico que se envía y el estado de la ECD: disponible, o caída con la copia local de la revocación vencida.
- **Variables dependientes:** los archivos aceptados sin verificación completa; la causa de rechazo de cada caso; el tiempo en verificación pendiente, y los aceptados sin evidencia o con otro hash.

Fundamento: la verificación corre sobre los mismos bytes que se custodian y se procesan, así que lo verificado es exactamente lo procesado (ADR-006). Una revocación que no se puede comprobar no se trata como válida: el archivo se acusa, queda en verificación pendiente y se resuelve en la ventana, como un pendiente de D01 (ADR-007). La evidencia firmada por BCR deja la prueba que el remitente no puede negar (ADR-008).

Lo que la refutaría, cualquiera de cuatro casos:

- Un archivo del catálogo con aceptación total o parcial.
- Un archivo en verificación pendiente que llega a resultado definitivo antes de verificarse.
- Un rechazo sin causa, o con una causa que no corresponde al caso enviado.
- Un aceptado sin entrada de evidencia, o con un hash distinto del de los bytes procesados.

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
