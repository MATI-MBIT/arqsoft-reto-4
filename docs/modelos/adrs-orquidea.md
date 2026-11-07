---
contrato: adr-log/v2
sistema: Orquídea
fecha: 2026-11-06
alcance_atributos: [disponibilidad, latencia, seguridad]
title: Decisiones de arquitectura (ADR)
parent: Vistas de arquitectura
nav_order: 0
---
# ADRs — Orquídea

## 0. Índice

| ADR | Título | Estado | Confianza | ASRs |
|---|---|---|---|---|
| ADR-001 | Pipeline asíncrono por etapas con acuse temprano sobre una cola durable | Propuesta | Media | ASR-D01, ASR-L04 |
| ADR-003 | Monitor de terceros, pendientes durables y ventana nocturna de reproceso con reintento diario y notificación | Propuesta | Media | ASR-D01 |
| ADR-004 | Llave de idempotencia por archivo, registrada antes del acuse | Propuesta | Media | ASR-D01 |
| ADR-002 | Dos carriles aislados, A para pequeños y B para masivos, con enrutador por tamaño al recibir | Propuesta | Media | ASR-L04 |
| ADR-005 | Verificación criptográfica completa en la recepción, con resultado retenido hasta verificar | Propuesta | Baja | ASR-S06 |
| ADR-006 | Información de revocación en caché local, con verificación pendiente que se reprocesa en la ventana nocturna | Propuesta | Media | ASR-S06, ASR-D01 |
| ADR-007 | Registro de evidencia de la verificación, de solo anexar y firmado por BCR | Propuesta | Media | ASR-S06 |

## 1. Insumos

### 1.1 Contexto

Orquídea es la plataforma de BCR por la que los IMC y los titulares de cuentas de compensación reportan las operaciones de divisas del país en archivos XML firmados de 1 a 100 000 registros. Reemplaza un sistema existente, en un dominio maduro.

Funcionales primarios: recibir el archivo y acusarlo; verificar firma, firmante, estampa y certificado; validar cada declaración y enriquecerla con STM, ANTA, MAMC y RNIC; persistir y notificar el resultado definitivo.

Actores: remitentes (IMC y titulares) y el operador de BCR. Sistemas externos: STM, ANTA, MAMC, RNIC y la ECD, fuera del control de BCR.

En la reunión técnica del 6 de noviembre de 2026, BCR y el equipo acotaron el diseño a tres ASR críticos, con la disponibilidad como foco: D01, L04 y S06. Lo que no sirve a ellos sale de esta iteración. Las pruebas de concepto corren en local con Docker y software libre (R-12).

### 1.2 ASRs

| ASR | Atributo | Fuente | Estímulo | Artefacto | Ambiente | Respuesta | Medida | Prioridad | Impacto |
|---|---|---|---|---|---|---|---|---|---|
| ASR-D01 | Disponibilidad · recuperación | STM, ANTA, MAMC, RNIC o la ECD | Uno deja de responder, o responde con latencia extrema, durante horas | Validación y enriquecimiento de las declaraciones | C1: un sistema externo caído, con la carga de A | Sigue recibiendo y acusando; lo pendiente se reprocesa cada noche hasta que el externo vuelva; si no vuelve en los días de reintento, se notifica | 0 archivos válidos rechazados o sin resultado definitivo al cierre de la primera ventana nocturna tras el regreso del externo | Crítica | Alto |
| ASR-L04 | Latencia | Remitentes de archivos pequeños | Envían pequeños, con arribo estocástico, mientras 5 masivos están en proceso | Procesamiento completo de los archivos pequeños | A: 400 pequeños/min y 5 masivos, sin fallas | Cada pequeño se resuelve casi tan rápido como sin masivos | p99 ≤ 3 s por archivo pequeño, con 5 masivos en proceso | Crítica | Alto |
| ASR-S06 | Seguridad · prevención | Remitente, o tercero que lo suplanta | Envía un archivo cuya firma, firmante, cadena, estampa o revocación no es válida o no se puede verificar | Emisión del resultado definitivo | Carga de A, con la ECD disponible (A) o caída (C1) | Puede acusarse, pero no se acepta hasta verificarse; si falla, se rechaza con la causa | 0 archivos aceptados sin firma, firmante, cadena, estampa y revocación verificadas | Crítica | Alto |

### 1.3 Restricciones, concerns y supuestos

| ID | Tipo | Descripción | Origen |
|---|---|---|---|
| R-1 | restricción | Archivos XML firmados digitalmente y con estampa cronológica de una ECD acreditada | enunciado |
| R-2 | restricción | Archivos de 1 a 100 000 registros y hasta 50 MB | enunciado |
| R-4 | restricción | Las reglas dependen de STM, ANTA, MAMC y RNIC, que BCR no puede modificar | enunciado |
| R-7 | restricción | Aceptación total, parcial o rechazo; cada declaración se acepta o rechaza sola | enunciado |
| R-12 | restricción | Pruebas de concepto en local con Docker y software libre, sin nube ni licencias de pago | usuario |
| R-13 | restricción | La información es reportable a otras entidades: ningún archivo puede perderse ni descartarse | usuario |
| R-14 | restricción | La autenticación del canal (TLS mutuo, ADR-13 de BCR) queda fuera del alcance de esta iteración: la seguridad crítica es la firma del archivo | usuario |
| C-01 | concern | BCR aprobó una cola durable (Apache Kafka en modo KRaft) y PostgreSQL; en su operación real BCR usa colas con procesadores que escalan horizontalmente, sin Kafka | usuario |
| C-02 | concern | Un componente con arranque lento (del orden de un minuto, como un job tipo Glue) no puede estar en el camino de los archivos pequeños | usuario |
| C-03 | concern | BCR acepta pagar capacidad reservada para los masivos aunque quede ociosa en los valles | usuario |
| C-04 | concern | BCR pregunta qué evita el estallido sobre la base de datos cuando un tercero vuelve tras 12 horas con 500 000 declaraciones acumuladas (pregunta 6) | usuario |
| C-05 | concern | BCR aprobó que un masivo cuya verificación no quepa en el segundo reciba un acuse de custodia (ADR-04 de BCR) | usuario |
| SUP-01 | supuesto | Verificar la firma de un archivo de 50 MB en streaming cabe en 1 s en el hardware de la prueba | [SUPUESTO] |
| SUP-03 | supuesto | La ECD publica su lista de revocación al menos una vez al día | [SUPUESTO] |
| SUP-07 | supuesto | El tamaño en bytes de un archivo predice su número de registros lo bastante bien para elegir el carril | [SUPUESTO] |
| SUP-09 | supuesto | El XSD trae entidad, fecha, lote y consecutivo, y juntos identifican un archivo sin ambigüedad | [SUPUESTO] |
| SUP-10 | supuesto | Una caída de un externo que termina antes de la ventana nocturna no obliga a reprocesar antes de esa ventana: BCR exime el SLA de latencia con externos caídos | [SUPUESTO] |

## 2. ADRs, agrupados por ASR

### 2.1 ASR-D01 · Ningún archivo se rechaza por la caída de un sistema externo (foco)

Tres decisiones sostienen la disponibilidad: separar la recepción del procesamiento, dejar lo pendiente en espera durable con una ventana nocturna de reproceso, y no duplicar lo que se reenvía o se reprocesa. ADR-006, del grupo de seguridad, también contribuye: los archivos en verificación pendiente entran a la misma ventana.

#### ADR-001: Pipeline asíncrono por etapas con acuse temprano sobre una cola durable

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — estilo probado en la operación real de BCR, pero el acuse con verificación en streaming (SUP-01) no se ha medido

##### Contexto

ASR-D01 exige que ningún archivo acusado durante la caída de un tercero se pierda ni quede sin desenlace (C1), y R-13 prohíbe descartar archivos. ASR-L04 exige p99 ≤ 3 s por archivo pequeño con 5 masivos en proceso (A). Hoy todo pasa por un flujo síncrono: validar y enriquecer cien mil registros no cabe en el tiempo de un acuse, y un tercero caído detiene la respuesta. R-4 deja cuatro dependencias externas fuera del control de BCR. ¿Qué estilo global separa la recepción del procesamiento para que recibir y acusar no dependan del tamaño del archivo ni de los terceros?

##### Decisión

Adoptaremos un pipeline de etapas asíncronas comunicadas por una cola durable: la recepción custodia el archivo, lo verifica, lo acusa y publica un evento; el procesamiento, el enriquecimiento y el resultado consumen eventos a su propio ritmo. Usaremos el producto que BCR aprobó (C-01) como cola. Lo elegimos porque es la única opción que saca del camino del acuse todo lo que depende de terceros (ASR-D01) y deja un punto donde separar las cargas por tamaño (ASR-L04).

##### Consecuencias

- (+) ASR-D01: el archivo queda custodiado y en la cola antes del acuse; una caída posterior no lo pierde.
- (+) ASR-L04: cada etapa consume de su propio tópico, así que los carriles pueden separarse (ADR-002).
- (−) El remitente ve un acuse, no un resultado: el resultado definitivo llega después (consistencia eventual).
- (−) Operación: una cola que monitorear y un identificador de correlación que propagar de punta a punta.
- (±) La entrega de la cola es al menos una vez; exige consumidores idempotentes (ADR-004).

##### Anexo de trazabilidad

**Y-statement:** En el contexto de un sistema que recibe archivos de 1 a 100 000 registros con cuatro terceros fuera de control, frente a ASR-D01 y ASR-L04, decidimos un pipeline asíncrono con acuse temprano y descartamos el monolito síncrono escalado y los servicios síncronos encadenados para lograr una recepción independiente del tamaño y de terceros, aceptando consistencia eventual y la operación de una cola.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos sin desenlace en C1 | contribuye | primario |
| ASR-L04 | Latencia | p99 ≤ 3 s por pequeño con 5 masivos | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| EST-07 | — | — | Pipe and Filter (pipeline por etapas) | Estilo |
| ESC-05 | — | — | Asincronía y colas | Escalabilidad · ritmo |

**A3. Alternativas**

Drivers: ASR-D01 (Crítica), ASR-L04 (Crítica), R-4, R-12, R-13 — fijados antes de evaluar.

| Criterio | Opción A: Pipeline asíncrono (elegida) | Opción B: Monolito síncrono escalado | Opción C: Servicios síncronos encadenados |
|---|---|---|---|
| ASR-D01 sin pérdidas con tercero caído | ++ el archivo queda en la cola antes del acuse | −− el tercero caído bloquea la respuesta y el archivo | −− la falla se propaga por la cadena |
| ASR-L04 aislamiento | + un tópico por carga | −− todo comparte el mismo proceso | + un servicio por carga |
| R-4 terceros fuera de control | + quedan detrás de una etapa | − llamadas en línea por registro | − llamadas en línea por servicio |
| R-12 prueba local | − una cola que montar | ++ un solo proceso | − varios servicios y su red |

Descartes: B — KO: con el tercero caído, o rechaza o deja colgado el archivo (ASR-D01, R-13). C — la cadena síncrona propaga la falla del tercero hasta el remitente (ASR-D01).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-001a | Sensibilidad | El tiempo del acuse depende de la escritura durable en la custodia y en la cola | La custodia y la cola están cerca de la recepción |
| TO-001a | Trade-off | Asincronía: disponibilidad ▲, inmediatez del resultado ▼ | BCR acepta acuse inmediato y resultado posterior |
| R-001a | Riesgo | Sin correlación de punta a punta, un archivo retenido horas es imposible de seguir | Se propaga un identificador de correlación en cada evento |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Matar un tercero simulado durante la carga y conciliar acusados contra la cola | 0 archivos acusados que no estén en la cola o en el repositorio |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: — · conflicto-con: —


#### ADR-003: Monitor de terceros, pendientes durables y ventana nocturna de reproceso con reintento diario y notificación

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — mecanismo acordado con BCR el 6-nov, pero la hora de la ventana y el número de días de reintento siguen abiertos

##### Contexto

ASR-D01 exige que ningún archivo acusado durante una caída de horas de STM, ANTA, MAMC, RNIC o la ECD termine perdido o sin desenlace: al cierre de la primera ventana de reproceso tras el regreso del tercero, cada uno tiene resultado definitivo o fue notificado (C1). R-13 prohíbe descartar archivos, y BCR preguntó qué evita el estallido sobre la base de datos cuando un tercero vuelve tras 12 horas con 500 000 declaraciones acumuladas. ADR-001 sacó el enriquecimiento del camino del acuse. ¿Cómo se detecta que un tercero cayó, dónde espera el trabajo que lo necesita y cuándo y cómo se reprocesa?

##### Decisión

Un monitor sondeará a cada tercero y publicará su estado. El orquestador consulta ese estado antes de llamar: si el tercero está caído, o si su adaptador vence el timeout, la declaración queda pendiente en un tópico durable. Un reprocesador corre una vez por noche, a hora fija, y reintenta todo lo pendiente. Si el tercero sigue caído, lo reprograma para la noche siguiente; al agotar los días de reintento, lo manda a notificar al remitente y al operador. Lo hacemos porque cumple ASR-D01 sin perder nada, y porque reprocesar en una ventana a hora fija y con ritmo propio evita el estallido que preguntó BCR.

##### Consecuencias

- (+) ASR-D01: la falla del tercero nunca llega al acuse ni produce un rechazo; todo lo pendiente tiene una fecha de reproceso y un desenlace.
- (+) El reproceso corre en el valle nocturno, lejos del pico, con un ritmo que fija el reprocesador y no el regreso del tercero.
- (−) Un archivo pendiente espera hasta la noche aunque el tercero vuelva a los 10 minutos; BCR exime el SLA de latencia con externos caídos (SUP-10).
- (−) El monitor es un componente más que también puede fallar; si cae, el orquestador sigue protegido por el timeout de cada adaptador.
- (±) La hora de la ventana y el número de días de reintento son parámetros que fija BCR (§7).

##### Anexo de trazabilidad

**Y-statement:** En el contexto de cuatro terceros y una ECD fuera del control de BCR que caen durante horas, frente a ASR-D01, decidimos un monitor de terceros con pendientes durables y una ventana nocturna de reproceso con reintento diario y notificación, y descartamos el circuit breaker con drenaje inmediato y el reintento síncrono para lograr cero archivos sin desenlace sin estallido al volver el tercero, aceptando que lo pendiente espere hasta la noche.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos sin desenlace en C1 | satisface | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-03 | Health Monitor | — | Monitor (estado de cada tercero) | Disponibilidad · detectar |
| DIS-04 | Timeout | timeout | Excepción / timeout (en cada adaptador) | Disponibilidad · detectar |
| DIS-12 | Retry (+ Idempotent Receiver) | diferido-por-cola | Reintento diferido a la ventana nocturna | Disponibilidad · recuperar |
| DIS-14 | Graceful Degradation (dependencia dura → blanda) | fallback | Degradación con gracia (acusar sin el tercero) | Disponibilidad · recuperar |

**A3. Alternativas**

Drivers: ASR-D01 (Crítica), R-4, R-13, R-12, C-04, SUP-10 — fijados antes de evaluar.

| Criterio | Opción A: Monitor + pendientes + ventana nocturna (elegida) | Opción B: Circuit breaker por adaptador + drenaje inmediato con tasa limitada | Opción C: Reintento síncrono con espera creciente |
|---|---|---|---|
| ASR-D01 sin desenlace | ++ cada pendiente tiene ventana, reintento y notificación | + queda pendiente, pero sin desenlace si el tercero no vuelve | − tras agotar reintentos, rechaza o bloquea el hilo |
| R-13 nada se descarta | ++ todo queda en el tópico durable | ++ todo queda en el tópico durable | −− lo que agota reintentos se pierde o se rechaza |
| C-04 estallido al volver | ++ el ritmo lo fija la ventana | + la tasa limitada lo contiene, si se calibra | −− todos reintentan a la vez |
| SUP-10 inmediatez al volver el tercero | − espera hasta la noche | ++ reprocesa apenas vuelve | + reintenta en minutos |
| R-12 prueba local | + un planificador y un monitor | − breaker por adaptador y gestor de drenaje | ++ una política de reintento |

Descartes: B — mejor en inmediatez, pero no da desenlace a lo que sigue pendiente después de días, y BCR acordó la ventana nocturna en la reunión del 6-nov. C — KO: con caídas de horas agota los hilos y termina rechazando (ASR-D01, R-13).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-003a | Sensibilidad | El tiempo hasta dejar de llamar a un tercero caído depende del periodo de sondeo del monitor y del timeout del adaptador | BCR define qué latencia cuenta como extrema |
| TO-003a | Trade-off | Ventana nocturna: control del ritmo y simplicidad ▲, inmediatez del resultado al volver el tercero ▼ | SUP-10 |
| R-003a | Riesgo | Con una caída de varios días, lo pendiente se acumula y la ventana de una noche no alcanza a reprocesarlo | El reprocesador procesa en lotes con ritmo propio y la ventana tiene duración suficiente |
| R-003b | Riesgo | Un monitor que reporta «disponible» a un tercero que responde con datos errados no lo detecta | La detección de fallas de contenido queda sin ASR (§7) |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Caer el tercero simulado, enviar archivos, disparar la ventana con el tercero arriba y con el tercero caído, y conciliar | 0 archivos acusados sin resultado definitivo ni notificación al cierre de la primera ventana tras el regreso |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: ADR-004 · conflicto-con: —


#### ADR-004: Llave de idempotencia por archivo, registrada antes del acuse

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — depende de SUP-09: que el XSD traiga los cuatro campos de la llave

##### Contexto

La historia HU-06 pide que, si el remitente reenvía el mismo archivo mientras el original está pendiente, el reenvío se rechace como duplicado sin tocar el original, y el reproceso nocturno de ASR-D01 no puede aplicar dos veces una declaración. ADR-001 dejó una cola con entrega al menos una vez, y ADR-003 reprocesa lo pendiente cada noche. En la reunión se propuso la llave entidad + fecha + lote + consecutivo. ¿Cómo se reconoce un archivo ya recibido y se evita aplicar dos veces lo que se reprocesa?

##### Decisión

Registraremos la llave del archivo (entidad, fecha, lote y consecutivo) en el repositorio, con restricción de unicidad, en la misma transacción local que registra el archivo, antes de emitir el acuse. Un archivo cuya llave ya existe se rechaza como duplicado. Cada declaración lleva la llave (archivo, declaración), y el resultado se escribe una sola vez por esa llave, así que un evento repetido o un reproceso no producen un segundo resultado. Lo hacemos porque cumple ASR-D01 sin transacciones distribuidas y con una sola consulta por archivo.

##### Consecuencias

- (+) ASR-D01: un reenvío no duplica el archivo y un reproceso no duplica el resultado.
- (−) Una escritura más en la recepción, antes del acuse.
- (−) Si la llave del negocio no identifica el archivo sin ambigüedad (SUP-09), dos archivos distintos chocarían; el remitente recibiría un falso duplicado.
- (±) Un archivo distinto del mismo remitente tiene otra llave y se recibe normalmente.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de una cola al menos una vez y un reproceso nocturno, frente a ASR-D01, decidimos una llave de negocio con unicidad registrada antes del acuse y descartamos el hash del contenido y la deduplicación en la cola para lograr que reenvíos y reprocesos no dupliquen nada, aceptando una escritura más en la recepción.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos sin desenlace en C1 | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-17 | Transaction (ACID) | local | Transacciones (archivo y llave en una sola transacción) | Disponibilidad · prevenir |

**A3. Alternativas**

Drivers: ASR-D01 (Crítica), R-13, R-12 — fijados antes de evaluar.

| Criterio | Opción A: Llave de negocio con unicidad (elegida) | Opción B: Hash del contenido | Opción C: Deduplicación en la cola |
|---|---|---|---|
| ASR-D01 reenvío rechazado | ++ la llave identifica el lote del remitente | + idéntico byte a byte; un reenvío con otra firma pasa como nuevo | − la cola deduplica en una ventana corta, no durante días |
| ASR-D01 reproceso sin duplicar | ++ la llave de declaración es estable | + estable | − no cubre el reproceso nocturno |
| R-13 nada se descarta | + el duplicado se rechaza con causa | + igual | − el duplicado se descarta en silencio |
| R-12 prueba local | + una restricción de unicidad | + un hash por archivo | ++ se configura en la cola, sin código |

Descartes: B — un remitente que vuelve a firmar el mismo lote cambia los bytes y se colaría como archivo nuevo. C — la ventana de deduplicación de la cola no cubre esperas de días y descarta sin avisar (R-13).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-004a | Trade-off | Unicidad antes del acuse: corrección ▲, una escritura más en el camino del acuse ▼ | La escritura cabe en el tiempo del acuse |
| R-004a | Riesgo | Falsos duplicados si la llave no es única en el negocio | SUP-09 |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Reenviar un archivo pendiente y repetir eventos del reproceso | 0 archivos o resultados duplicados |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —


### 2.2 ASR-L04 · Aislamiento de carga entre archivos masivos y pequeños

Una decisión: dos carriles aislados con un enrutador por tamaño, sobre el pipeline de ADR-001.

#### ADR-002: Dos carriles aislados, A para pequeños y B para masivos, con enrutador por tamaño al recibir

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — el aislamiento se argumenta por construcción, pero el umbral de enrutamiento no está fijado y el quiebre con más de 5 masivos no se ha medido

##### Contexto

ASR-L04 exige p99 ≤ 3 s por archivo pequeño con 5 masivos en proceso (A). En la reunión del 6-nov se acordó que una ráfaga masiva de un remitente grande no puede degradar la respuesta a los demás, y C-02 prohíbe componentes de arranque lento en el camino de los pequeños. ADR-001 dejó etapas que consumen de la cola, pero no dijo si todos los archivos comparten la misma. ¿Cómo se reparte la capacidad para que un masivo nunca quite capacidad a un pequeño?

##### Decisión

Separaremos dos carriles con tópico, pool y despliegue propios: el carril A procesa archivos pequeños en un flujo rápido, y el carril B procesa masivos en un flujo tipo batch. Un enrutador dentro de la recepción clasifica cada archivo por su tamaño en bytes, que conoce al terminar de recibirlo sin abrirlo (SUP-07), y lo publica en el tópico de su carril. El umbral entre carriles y el carril de los medianos quedan abiertos (§7). Lo hacemos porque el aislamiento por construcción es la única forma de sostener los 3 s de ASR-L04 con 5 masivos en proceso sin depender de cómo se comporte la carga ajena.

##### Consecuencias

- (+) ASR-L04: un masivo no puede consumir capacidad del carril A aunque lo intente.
- (+) Una saturación del carril B no afecta al carril A.
- (−) Capacidad ociosa en el carril B cuando no hay masivos; BCR la acepta (C-03).
- (−) Operación: dos despliegues y dos pools que dimensionar, y un umbral que calibrar.
- (±) Un archivo mal clasificado solo cambia de carril; no se pierde.

##### Anexo de trazabilidad

**Y-statement:** En el contexto del pipeline de ADR-001, frente a ASR-L04, decidimos dos carriles aislados con un enrutador por tamaño y descartamos un pool compartido con prioridad y uno con cuotas para lograr un aislamiento que no depende de la carga ajena, aceptando capacidad ociosa y dos despliegues.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-L04 | Latencia | p99 ≤ 3 s por pequeño con 5 masivos | satisface | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-22 | — | — | Bulkhead (compartimentos) | Disponibilidad · prevenir (contener) |
| LAT-03 | — | — | Priorizar eventos (enrutar por tamaño al recibir) | Desempeño · controlar la demanda |

**A3. Alternativas**

Drivers: ASR-L04 (Crítica), C-02, C-03 — fijados antes de evaluar.

| Criterio | Opción A: Carriles dedicados (elegida) | Opción B: Pool compartido con prioridad | Opción C: Pool compartido con cuotas |
|---|---|---|---|
| ASR-L04 3 s con masivos | ++ aislamiento por construcción | − un masivo en curso retiene hilos que la prioridad no libera | + la cuota protege si se dimensiona bien |
| C-02 sin arranque lento en el camino rápido | ++ el carril A no comparte componentes con el B | − el mismo pool sirve a los dos | − el mismo pool sirve a los dos |
| C-03 capacidad ociosa | − capacidad reservada en los valles | ++ sin capacidad ociosa | + poca capacidad ociosa |

Descartes: B — la prioridad no expropia un masivo que ya ocupa el pool, y ASR-L04 queda sujeto a la carga ajena. C — la cuota es un parámetro sensible que hay que reajustar con cada cambio de carga.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-002a | Sensibilidad | El p99 de los pequeños depende del tamaño del pool del carril A | La carga normal es de 400 pequeños/min |
| TO-002a | Trade-off | Carriles dedicados: latencia de pequeños ▲, eficiencia de capacidad ▼ | BCR acepta pagar capacidad ociosa (C-03) |
| R-002a | Riesgo | Con más de 5 masivos el carril B se satura, aunque el A no lo note | Nadie ha dicho qué pasa con el sexto masivo |
| NR-002a | No-riesgo | Enrutar por bytes no exige abrir el archivo | SUP-07 |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Experimento | Misma carga de pequeños con 0 y con 5 masivos, y luego con más de 5 hasta el quiebre | p99 ≤ 3 s por pequeño con 5 masivos, y el margen frente a la corrida sin masivos como complemento |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —


### 2.3 ASR-S06 · Ningún archivo se acepta sin validación criptográfica completa

Tres decisiones: verificar todo en la recepción y retener el resultado, comprobar la revocación sin depender en línea de la ECD, y conservar la evidencia que da el no repudio.

#### ADR-005: Verificación criptográfica completa en la recepción, con resultado retenido hasta verificar

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Baja — depende de SUP-01: verificar 50 MB en streaming dentro del segundo no se ha medido

##### Contexto

ASR-S06 exige que ningún archivo llegue a aceptación sin firma, firmante, estampa, cadena y revocación del certificado verificadas (A y C1). R-1 fija que el archivo llega firmado en XML, y la firma XML obliga a canonicalizar y resumir el documento. BCR aprobó que un masivo cuya verificación no quepa en el segundo reciba un acuse de custodia, y R-14 deja el canal fuera del alcance. ¿Qué se verifica antes del acuse, y cómo se impide que un archivo sin verificar llegue a resultado definitivo?

##### Decisión

Verificaremos en la recepción, con un parser en streaming sin DTD ni entidades externas y sobre los mismos bytes que se custodian y se procesan: la firma, que el firmante sea el remitente, la cadena del certificado hasta una raíz aceptada, la estampa (con tolerancia de desfase y antigüedad máxima) y la revocación. Si la verificación cabe en el segundo, el acuse dice que el archivo es auténtico; si no, sale un acuse de custodia y la verificación sigue en paralelo. En los dos casos, el resultado definitivo queda retenido hasta que la verificación termine bien. Lo hacemos porque cumple ASR-S06 sin frenar la recepción, y porque procesar los mismos bytes verificados cierra la firma envuelta.

##### Consecuencias

- (+) ASR-S06: ningún camino llega a aceptación sin verificación completa.
- (−) Un acuse de custodia no prueba autenticidad: el remitente tiene que esperar el resultado.
- (−) Verificar en streaming un XML de 50 MB es la parte más incierta (SUP-01, pregunta 1 de BCR).
- (±) El estado «verificación pendiente» lo comparten ADR-005 y ADR-006.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de archivos XML firmados de hasta 50 MB, frente a ASR-S06, decidimos verificar firma, firmante, cadena, estampa y revocación en streaming en la recepción y retener el resultado hasta verificar, y descartamos verificar siempre antes del acuse y exigir firma separada sobre el hash para lograr que nada sin verificar se acepte, aceptando acuses de custodia en masivos.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | satisface | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| SEG-02 | Authenticator | un-factor | Autenticar actores (firma digital y firmante del archivo) | Seguridad · resistir |
| SEG-11 | — | — | Verificar integridad (lo verificado es lo procesado) | Seguridad · detectar |
| SEG-08 | — | — | Validar la entrada (parser sin DTD ni entidades externas) | Seguridad · resistir |

**A3. Alternativas**

Drivers: ASR-S06 (Crítica), R-1, R-14, C-05 — fijados antes de evaluar.

| Criterio | Opción A: Streaming + resultado retenido (elegida) | Opción B: Verificación completa siempre antes del acuse | Opción C: Firma separada sobre el hash del archivo |
|---|---|---|---|
| ASR-S06 nada sin verificar | ++ retiene el resultado | ++ verifica antes de todo | + verifica el hash |
| C-05 recepción sin bloqueo en masivos | + nunca espera | −− el acuse de un masivo espera la verificación | + nunca espera |
| R-1 XML firmado | + respeta el formato | + respeta el formato | KO cambia el contrato con los remitentes |

Descartes: B — un masivo dejaría al remitente sin acuse mientras se verifica. C — KO: viola R-1, porque exige a los remitentes otra forma de firmar.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-005a | Sensibilidad | El tiempo de verificación crece con el tamaño del archivo | SUP-01 |
| TO-005a | Trade-off | Acuse de custodia: recepción sin bloqueo ▲, prueba de autenticidad en el acuse ▼ | BCR acepta que el acuse de custodia no certifique la firma |
| R-005a | Riesgo | Un parser que construye el árbol completo reabre la firma envuelta y agota la memoria | El parser procesa en streaming y verifica el elemento firmado que se procesa |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Catálogo de archivos con firma alterada, firma envuelta, firmante distinto, certificado fuera de la cadena, estampa desfasada y estampa vencida | 0 archivos aceptados sin verificación completa |
| Experimento | Verificación en streaming de archivos firmados de 1 registro a 50 MB, midiendo tiempo y memoria (SUP-01) | Ningún archivo de 50 MB carga el árbol completo en memoria; el tiempo por tamaño se reporta para decidir el acuse de custodia |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —


#### ADR-006: Información de revocación en caché local, con verificación pendiente que se reprocesa en la ventana nocturna

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — la ventana de revocación depende de SUP-03

##### Contexto

ASR-S06 exige que nada se acepte sin comprobar la revocación, y ASR-D01 cuenta a la ECD entre los terceros cuya caída no puede dejar archivos sin desenlace (C1). Consultar a la ECD en línea por cada archivo ata la recepción a su disponibilidad. ADR-005 retiene el resultado hasta verificar, y ADR-003 ya reprocesa lo pendiente cada noche. ¿Cómo se comprueba la revocación sin depender en línea de la ECD, y qué pasa con un archivo cuya revocación no se pudo comprobar?

##### Decisión

Mantendremos localmente la lista de revocación de la ECD y las respuestas OCSP por certificado, refrescadas periódicamente. Si la información está vigente, la verificación es local. Si la ECD está caída y la información venció, el archivo recibe acuse en estado «verificación pendiente», no llega a resultado definitivo y entra a la ventana nocturna de ADR-003 como cualquier otro pendiente. Lo hacemos porque cumple ASR-S06 sin consultar a la ECD por archivo y reutiliza el desenlace que ASR-D01 ya exige.

##### Consecuencias

- (+) ASR-S06: nada se acepta con la revocación sin comprobar.
- (+) ASR-D01: la caída de la ECD no detiene la recepción, y sus pendientes tienen el mismo desenlace que los de los demás terceros.
- (−) Ventana de revocación: un certificado revocado después del último refresco se acepta hasta el siguiente (R-006a).
- (−) Con la ECD caída varios días, los archivos se acumulan en verificación pendiente.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de la verificación de ADR-005, frente a ASR-S06 y ASR-D01, decidimos información de revocación en caché local con verificación pendiente reprocesada en la ventana nocturna, y descartamos la consulta OCSP en línea y confiar en la información embebida en la estampa para lograr verificación local sin aceptar nada sin verificar, aceptando una ventana de revocación.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | contribuye | primario |
| ASR-D01 | Disponibilidad | 0 archivos sin desenlace en C1 | contribuye | afectado |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| LAT-10 | — | — | Mantener múltiples copias de datos (lista de revocación en caché) | Desempeño · gestionar recursos |
| DIS-14 | Graceful Degradation (dependencia dura → blanda) | fallback | Degradación con gracia (verificación pendiente) | Disponibilidad · recuperar |

**A3. Alternativas**

Drivers: ASR-S06 (Crítica), ASR-D01 (Crítica) — fijados antes de evaluar.

| Criterio | Opción A: Caché local + pendiente (elegida) | Opción B: OCSP en línea por archivo | Opción C: Información de revocación embebida en la estampa |
|---|---|---|---|
| ASR-S06 nada sin verificar | + pendiente hasta verificar | ++ siempre información fresca | − depende de que la ECD la incluya |
| ASR-D01 ECD caída | + recibe y deja pendiente con desenlace | −− la caída de la ECD frena la recepción | + no depende de la ECD |

Descartes: B — ata la recepción a la disponibilidad de la ECD (ASR-D01). C — solo sirve si la ECD embebe la información en cada estampa, y el enunciado no lo garantiza.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-006a | Trade-off | Periodo de refresco: disponibilidad ▲, ventana de revocación ▼ | SUP-03 |
| R-006a | Riesgo | Un certificado robado y revocado se acepta durante la ventana | SUP-03 |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | ECD simulada caída con la información vencida | 0 archivos aceptados sin verificación completa |

**A6. Relaciones:** reemplaza: — · refina: ADR-005 · depende-de: ADR-003 · conflicto-con: —


#### ADR-007: Registro de evidencia de la verificación, de solo anexar y firmado por BCR

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — patrón conocido; el volumen es de una entrada por archivo, no por declaración

##### Contexto

El enunciado pide garantizar el no repudio. ASR-S06 lo sostiene, porque nada se acepta sin verificación completa, pero esa verificación solo sirve de prueba si queda como evidencia que el remitente no pueda negar. En la reunión del 6-nov se fijó la táctica del curso «recuperarse de un ataque → registro de auditoría / no repudio», con la verificación de integridad como complemento. ADR-005 verifica la firma, pero si el resultado solo queda en el repositorio transaccional, quien lo administra puede cambiarlo. ¿Dónde y cómo se conserva la prueba de que un archivo firmado por un remitente llegó y se verificó?

##### Decisión

Por cada archivo, la recepción anexará a un registro de evidencia, separado del repositorio transaccional, una entrada con el hash de los bytes custodiados, el certificado del firmante, la estampa y el resultado de cada comprobación. BCR firma cada entrada, y el registro no admite modificar ni borrar. Lo hacemos porque cumple el no repudio de ASR-S06 con una sola entrada por archivo, sin cargar el camino de las declaraciones.

##### Consecuencias

- (+) ASR-S06: el remitente no puede negar un envío cuya firma, firmante y estampa quedaron verificados y registrados.
- (−) Una escritura y una firma más por archivo en la recepción.
- (−) Almacenamiento que crece sin borrar; la retención la fija la norma.
- (±) La detección de alteraciones del registro (S04) no se desarrolla en esta iteración.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de la verificación de ADR-005, frente a ASR-S06, decidimos un registro de evidencia de solo anexar firmado por BCR y descartamos guardar el resultado solo en el repositorio y encadenar por hash cada evento para lograr no repudio con una entrada por archivo, aceptando una escritura y una firma más por archivo.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| SEG-18 | Audit Trail | firma | Registro de auditoría / no repudio | Seguridad · recuperar |

**A3. Alternativas**

Drivers: ASR-S06 (Crítica), R-12 — fijados antes de evaluar.

| Criterio | Opción A: Evidencia de solo anexar y firmada (elegida) | Opción B: Resultado solo en el repositorio | Opción C: Cadena de hash por cada evento |
|---|---|---|---|
| ASR-S06 no repudio | ++ cada entrada es prueba por sí misma | − el administrador puede cambiarla sin rastro | ++ cualquier alteración rompe la cadena |
| R-12 prueba local y costo | + una entrada firmada por archivo | ++ nada extra | − una entrada por evento y un secuenciador de la cadena en paralelo |

Descartes: B — no da no repudio: la prueba la puede alterar quien administra la base. C — resuelve también S04, que no es crítico, a costa de secuenciar la cadena en paralelo (pregunta 2 de BCR).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-007a | Trade-off | Firma por entrada: no repudio ▲, costo de recepción ▼ | Una entrada por archivo, no por declaración |
| R-007a | Riesgo | Si la llave de firma de BCR se compromete, la evidencia pierde valor | La llave vive fuera de la aplicación |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inspección | Para cada archivo aceptado del experimento, existe una entrada firmada con el mismo hash de los bytes procesados | 100 % de los archivos aceptados con entrada y firma válidas |

**A6. Relaciones:** reemplaza: — · refina: ADR-005 · depende-de: — · conflicto-con: —


## 3. Matriz ASR × ADR

| ASR | Prioridad | ADR-001 | ADR-002 | ADR-003 | ADR-004 | ADR-005 | ADR-006 | ADR-007 | Hueco |
|---|---|---|---|---|---|---|---|---|---|
| ASR-D01 | Crítica | C | — | S | C | — | C | — | — |
| ASR-L04 | Crítica | C | S | — | — | — | — | — | — |
| ASR-S06 | Crítica | — | — | — | — | S | C | C | — |

## 4. Abierto

**Preguntas:** ¿A qué hora corre la ventana nocturna, y cuánto dura? ¿Cuántos días se reintenta un pendiente y qué pasa después: se notifica cada día, se rechaza con causa o pasa a un operador? ¿Cuál es el umbral en bytes entre el carril A y el carril B, y a qué carril van los medianos (51 a 5 000 registros)? ¿Qué latencia de un tercero cuenta como «extrema» para que el monitor lo marque degradado? ¿Qué desfase de reloj y qué antigüedad de la estampa se toleran? ¿El XSD trae entidad, fecha, lote y consecutivo (SUP-09)? ¿Qué pasa con el sexto archivo masivo (R-002a)?

**Supuestos:** SUP-01 verificar 50 MB en streaming cabe en 1 s. SUP-03 la ECD publica su lista al menos una vez al día. SUP-07 el tamaño en bytes predice el número de registros. SUP-09 la llave de negocio identifica el archivo. SUP-10 un pendiente puede esperar a la ventana nocturna aunque el tercero vuelva antes.

**Riesgos abiertos:** R-003a una caída de varios días acumula más de lo que cabe en una ventana. R-003b el monitor no detecta un tercero que responde con datos errados. R-004a falsos duplicados. R-005a firma envuelta si el parser construye el árbol completo. R-006a ventana de revocación. R-007a compromiso de la llave de firma de BCR.

**Fuera de alcance:** Esta iteración solo desarrolla los tres ASR críticos acordados el 6-nov. Los ADR del registro anterior que servían a ASR no críticos salen de esta iteración y quedan en el historial de git: bloques paralelos del carril pesado (L03, L05), outbox transaccional (D02, D03), reglas y esquemas como datos y su activación (M01, M02, D04), autorización por remitente (S03), auditoría encadenada por hash (S04), modelo canónico y proyecciones (I02) y adaptador como capa anticorrupción con circuit breaker (I01, reemplazado para D01 por ADR-003). Seguridad del canal: la autenticación TLS mutua (ADR-13 de BCR) sigue aprobada por BCR, pero no entra en esta iteración (R-14). Disponibilidad física: la topología activo-activo (ADR-17 de BCR) es infraestructura. Integración con el Banco de la República: fuera del reto.

**Registro sin catálogos ni plan de diagramas.** Por decisión del equipo (2026-11-06), este registro no lleva catálogo de elementos, catálogo de conectores ni plan de diagramas: los elementos y sus relaciones los deriva la skill de diagramas de las decisiones y las tácticas, para no sesgarla. Por eso el registro ya no sigue completo el contrato `adr-log/v2`.
