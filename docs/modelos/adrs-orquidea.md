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

Ocho decisiones de arquitectura, agrupadas por el ASR crítico al que responden. Cada ADR dice qué se decidió, por qué, qué alternativas se descartaron y qué se acepta pagar. Cómo se dibuja cada decisión lo resuelven los diagramas.

## 0. Índice

| ADR | Grupo | Título | Estado | ASRs |
|---|---|---|---|---|
| ADR-001 | ASR-D01 | Pipeline asíncrono por etapas con acuse temprano sobre una cola durable | Propuesta | ASR-D01, ASR-L04 |
| ADR-002 | ASR-D01 | Detección de terceros caídos, lentos o de vuelta: monitor con ping/echo y timeout por llamada | Propuesta | ASR-D01 |
| ADR-003 | ASR-D01 | Pendientes durables y ventana nocturna de reproceso, con reintento diario y notificación | Propuesta | ASR-D01 |
| ADR-004 | ASR-D01 | Llave de idempotencia por archivo, registrada antes del acuse | Propuesta | ASR-D01 |
| ADR-005 | ASR-L04 | Dos carriles aislados con enrutador por tamaño al recibir | Propuesta | ASR-L04 |
| ADR-006 | ASR-S06 | Verificación criptográfica completa en la recepción, con resultado retenido hasta verificar | Propuesta | ASR-S06 |
| ADR-007 | ASR-S06 | Revocación en caché local, con verificación pendiente que se reprocesa en la ventana nocturna | Propuesta | ASR-S06, ASR-D01 |
| ADR-008 | ASR-S06 | Registro de evidencia de solo anexar, firmado por BCR | Propuesta | ASR-S06 |

## 1. Insumos

### 1.1 Contexto

Orquídea es la plataforma por la que los IMC y los titulares de cuentas de compensación reportan a BCR sus operaciones de divisas, en archivos XML firmados de 1 a 100 000 registros. Reemplaza un sistema existente, en un dominio maduro.

Funcionales primarios: recibir y acusar el archivo; verificar su firma; validar cada declaración y enriquecerla con STM, ANTA, MAMC y RNIC; persistir y notificar el resultado definitivo.

Actores: los remitentes y el operador de BCR. Sistemas externos fuera del control de BCR: STM, ANTA, MAMC, RNIC y la ECD.

La reunión técnica del 6 de noviembre de 2026 acotó el diseño a tres ASR críticos, con la disponibilidad como foco. Las pruebas de concepto corren en local con Docker y software libre (R-12).

### 1.2 ASRs

| ASR | Atributo | Fuente | Estímulo | Artefacto | Ambiente | Respuesta | Medida | Prioridad | Impacto |
|---|---|---|---|---|---|---|---|---|---|
| ASR-D01 | Disponibilidad · recuperación | STM, ANTA, MAMC, RNIC o la ECD | Uno deja de responder, o responde con latencia extrema | Orquídea, todo el sistema | C1 - Sistema externo degradado, con la carga de A | Sigue recibiendo y acusando; lo pendiente se reprocesa cada noche hasta que el externo vuelva; si no vuelve en los días de reintento, se notifica y sigue pendiente | 0 archivos válidos sin aceptar al cierre de la primera ventana nocturna tras el regreso del externo | Crítica | Alto |
| ASR-L04 | Latencia | Remitentes de archivos pequeños | Envían pequeños, con arribo estocástico, mientras 5 masivos están en proceso | Servicio de procesamiento de archivos | A - Operación normal: 400 pequeños/min y 5 masivos, sin fallas | Cada pequeño se resuelve sin esperar a los masivos | p99 ≤ 3 s por archivo pequeño | Crítica | Alto |
| ASR-S06 | Seguridad · prevención | Un remitente, o un tercero que lo suplanta | Envía un archivo cuya firma, firmante, cadena, estampa o revocación no es válida o no se puede verificar | Servicio de verificación criptográfica | Carga de A, con la ECD disponible (A) o caída (C1) | Puede acusarse, pero no se acepta hasta verificarse; si falla, se rechaza con la causa | 0 archivos aceptados sin firma, firmante, cadena, estampa y revocación verificadas | Crítica | Alto |

### 1.3 Restricciones, concerns y supuestos

| ID | Tipo | Descripción | Origen |
|---|---|---|---|
| R-1 | restricción | Archivos XML firmados, con estampa cronológica de una ECD acreditada | enunciado |
| R-2 | restricción | Archivos de 1 a 100 000 registros y hasta 50 MB | enunciado |
| R-4 | restricción | Las reglas dependen de STM, ANTA, MAMC y RNIC, que BCR no puede modificar | enunciado |
| R-7 | restricción | Aceptación total, parcial o rechazo; cada declaración se decide sola | enunciado |
| R-12 | restricción | Pruebas de concepto en local con Docker y software libre | usuario |
| R-13 | restricción | La información es reportable a otras entidades: ningún archivo puede perderse ni descartarse | usuario |
| R-14 | restricción | La autenticación del canal (TLS mutuo, ADR-13 de BCR) queda fuera de esta iteración | usuario |
| C-01 | concern | BCR aprobó una cola durable (Kafka en modo KRaft) y PostgreSQL; en su operación real usa colas sin Kafka | usuario |
| C-02 | concern | Nada con arranque lento (del orden de un minuto) puede estar en el camino de los pequeños | usuario |
| C-03 | concern | BCR acepta pagar capacidad reservada para los masivos aunque quede ociosa | usuario |
| C-04 | concern | BCR pregunta qué evita el estallido sobre la base de datos cuando un tercero vuelve tras 12 h con 500 000 declaraciones acumuladas | usuario |
| C-05 | concern | BCR aprobó que un masivo cuya verificación no quepa en el segundo reciba un acuse de custodia (ADR-04 de BCR) | usuario |
| SUP-01 | supuesto | Verificar en streaming la firma de un archivo de 50 MB cabe en 1 s | [SUPUESTO] |
| SUP-03 | supuesto | La ECD publica su lista de revocación al menos una vez al día | [SUPUESTO] |
| SUP-07 | supuesto | El tamaño en bytes predice el número de registros lo bastante bien para elegir carril | [SUPUESTO] |
| SUP-09 | supuesto | El XSD trae entidad, fecha, lote y consecutivo, y juntos identifican un archivo | [SUPUESTO] |
| SUP-10 | supuesto | Lo pendiente puede esperar a la ventana nocturna aunque el externo vuelva antes: BCR exime el SLA de latencia con externos caídos | [SUPUESTO] |

## 2. ADRs, agrupados por ASR

### 2.1 ASR-D01 · Ningún archivo se rechaza por la caída de un sistema externo (foco)

Cuatro decisiones, en el orden del ciclo de una falla: separar la recepción del procesamiento (ADR-001), detectar que un tercero cayó y que volvió (ADR-002), recuperarse dejando lo pendiente en espera durable hasta la ventana nocturna (ADR-003) y no duplicar lo que se reenvía o se reprocesa (ADR-004). ADR-007, del grupo de seguridad, también contribuye: la verificación pendiente entra a la misma ventana.

#### ADR-001: Pipeline asíncrono por etapas con acuse temprano sobre una cola durable

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — BCR opera este estilo, pero el acuse con verificación en streaming (SUP-01) no se ha medido

##### Contexto

ASR-D01 exige que, con un sistema externo caído, todo archivo válido termine aceptado tras la primera ventana nocturna posterior a su regreso, y R-13 prohíbe descartar archivos. ASR-L04 exige p99 ≤ 3 s por archivo pequeño con 5 masivos en proceso. Hoy todo pasa por un flujo síncrono: un tercero caído detiene la respuesta, y validar cien mil registros no cabe en el tiempo de un acuse. ¿Qué estilo separa la recepción del procesamiento, para que recibir y acusar no dependan del tamaño del archivo ni de los terceros?

##### Decisión

Adoptaremos un pipeline de etapas asíncronas comunicadas por una cola durable. La recepción custodia el archivo, lo verifica, lo acusa y publica un evento; el procesamiento, el enriquecimiento y el resultado consumen a su propio ritmo. La cola es la que aprobó BCR (C-01). Lo elegimos porque saca del camino del acuse todo lo que depende de terceros (ASR-D01) y deja un punto donde separar las cargas por tamaño (ASR-L04).

##### Consecuencias

- (+) ASR-D01: el archivo queda custodiado y encolado antes del acuse; una caída posterior no lo pierde.
- (+) ASR-L04: cada etapa consume de su propio tópico, y eso permite separar carriles (ADR-005).
- (−) El remitente recibe un acuse, no un resultado: el resultado definitivo llega después.
- (−) Una cola que operar y un identificador de correlación que propagar de punta a punta.
- (±) La cola entrega al menos una vez; los consumidores tienen que ser idempotentes (ADR-004).

##### Anexo de trazabilidad

**Y-statement:** En el contexto de archivos de 1 a 100 000 registros que dependen de cuatro terceros fuera de control, frente a ASR-D01 y ASR-L04, decidimos un pipeline asíncrono con acuse temprano, y descartamos el monolito síncrono escalado y los servicios síncronos encadenados, para lograr una recepción independiente del tamaño y de los terceros, aceptando consistencia eventual y la operación de una cola.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos válidos sin aceptar tras la ventana | contribuye | primario |
| ASR-L04 | Latencia | p99 ≤ 3 s por pequeño | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| EST-07 | Pipe and Filter | — | Pipe and Filter | Estilo |
| ESC-05 | Asynchrony / queues | — | Asincronía y colas | Escalabilidad · ritmo |

**A3. Alternativas**

Drivers: ASR-D01, ASR-L04, R-4, R-12, R-13, fijados antes de evaluar.

| Criterio | A: Pipeline asíncrono (elegida) | B: Monolito síncrono escalado | C: Servicios síncronos encadenados |
|---|---|---|---|
| ASR-D01 con un tercero caído | ++ el archivo queda en la cola antes del acuse | −− el tercero bloquea la respuesta | −− la falla se propaga por la cadena |
| ASR-L04 aislamiento | + un tópico por carga | −− todo comparte el mismo proceso | + un servicio por carga |
| R-12 prueba local | − una cola que montar | ++ un solo proceso | − varios servicios y su red |

Descartes: B, KO: con el tercero caído rechaza o deja colgado el archivo (ASR-D01, R-13). C: la cadena síncrona propaga la caída del tercero hasta el remitente (ASR-D01).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-001a | Sensibilidad | El tiempo del acuse depende de la escritura durable en la custodia y en la cola | La custodia y la cola están cerca de la recepción |
| TO-001a | Trade-off | Asincronía: disponibilidad ▲, inmediatez del resultado ▼ | BCR acepta acuse inmediato y resultado posterior |
| R-001a | Riesgo | Sin correlación de punta a punta, un archivo retenido horas no se puede seguir | Cada evento lleva el identificador de correlación |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Caer un tercero simulado durante la carga y conciliar acusados contra lo encolado | 0 archivos acusados fuera de la cola o del repositorio |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: — · conflicto-con: —

#### ADR-002: Detección de terceros caídos, lentos o de vuelta: monitor con ping/echo y timeout por llamada

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — tácticas conocidas, pero el periodo de sondeo y el timeout dependen de qué latencia cuenta como extrema, que BCR no ha fijado

##### Contexto

ASR-D01 es de recuperación, y para recuperarse primero hay que saber que algo falló: sin detectar que un tercero cayó, la declaración se queda esperando o se rechaza; sin detectar que volvió, nada sabe cuándo reprocesar. BCR pide tratar un tercero lento igual que uno caído. STM, ANTA, MAMC, RNIC y la ECD están fuera del control de BCR (R-4): no se les puede pedir que emitan señales propias. ¿Cómo se detecta a tiempo que un tercero cayó, que está lento y que ya volvió?

##### Decisión

Un monitor de terceros sondeará a cada uno con ping/echo a un periodo fijo y publicará su estado: disponible, caído o de vuelta. Cada adaptador, además, vence un timeout en cada llamada y reporta al monitor la llamada vencida, así que la lentitud cuenta como caída. Antes de llamar, el orquestador consulta el estado; el reprocesador de ADR-003 lo consulta antes de cada ventana. Lo hacemos porque detectar la caída y el regreso, sin depender de que el tercero coopere, es la condición para recuperarse (ASR-D01).

##### Consecuencias

- (+) ASR-D01: la caída se detecta antes de que una declaración la sufra, y el regreso habilita el reproceso.
- (+) Un tercero lento no retiene hilos: el timeout corta la espera.
- (−) Tráfico de sondeo constante hacia cada tercero.
- (−) El monitor también puede fallar; si cae, el timeout de cada adaptador sigue protegiendo.
- (±) El periodo de sondeo y el timeout dependen de qué latencia es extrema (§4).

##### Anexo de trazabilidad

**Y-statement:** En el contexto de cuatro terceros y una ECD fuera del control de BCR, frente a ASR-D01, decidimos un monitor que sondea con ping/echo y un timeout en cada adaptador, y descartamos detectar solo por timeout y el heartbeat, para lograr detectar la caída, la lentitud y el regreso de cada tercero, aceptando tráfico de sondeo y un monitor que vigilar.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos válidos sin aceptar tras la ventana | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-01 | Ping/Echo | — | Ping / echo | Disponibilidad · detectar |
| DIS-03 | Monitor | — | Monitor | Disponibilidad · detectar |
| DIS-04 | Timeouts | timeout | Excepción / timeout | Disponibilidad · detectar |

**A3. Alternativas**

Drivers: ASR-D01, R-4, R-12, fijados antes de evaluar.

| Criterio | A: Monitor con ping/echo + timeout (elegida) | B: Solo timeout por llamada | C: Heartbeat del tercero |
|---|---|---|---|
| Detecta la caída | ++ antes de que una declaración la sufra | + cuando una declaración la sufre | ++ por ausencia del latido |
| Detecta el regreso | ++ el sondeo lo ve | −− solo llamando de nuevo, con declaraciones reales | ++ vuelve el latido |
| Lentitud como caída | ++ el timeout la corta | ++ el timeout la corta | − el latido puede seguir aunque responda lento |
| R-4 tercero fuera de control | ++ no le pide nada al tercero | ++ no le pide nada | KO el tercero tendría que emitir latidos |

Descartes: B: no sabe cuándo volvió el tercero sin arriesgar declaraciones reales, y la ventana de ADR-003 necesita ese dato. C, KO: viola R-4, porque obliga a los terceros a emitir latidos.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-002a | Sensibilidad | El tiempo de detección es aproximadamente el periodo de sondeo por los fallos seguidos que declaran la caída | BCR define qué latencia es extrema |
| TO-002a | Trade-off | Periodo de sondeo corto: detección rápida ▲, tráfico hacia los terceros ▲ | Los terceros toleran el sondeo |
| R-002a | Riesgo | El monitor no detecta a un tercero que responde a tiempo con datos errados; el libro pide para eso una verificación de sanidad (DIS-07) | La detección de fallas de contenido queda sin ASR (§4) |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Cortar el tercero simulado y agregarle una latencia mayor que el timeout; después restablecerlo | El monitor lo marca caído y luego de vuelta; ninguna declaración se rechaza ni deja hilos bloqueados; se reporta el tiempo de detección |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

#### ADR-003: Pendientes durables y ventana nocturna de reproceso, con reintento diario y notificación

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — mecanismo acordado con BCR el 6-nov; la hora de la ventana y los días de reintento siguen abiertos

##### Contexto

ASR-D01 exige que todo archivo válido recibido durante la caída de un tercero quede aceptado al cierre de la primera ventana nocturna tras su regreso. Las caídas pueden durar horas, R-13 prohíbe descartar, y BCR pregunta qué evita el estallido cuando un tercero vuelve con 500 000 declaraciones acumuladas (C-04). ADR-001 sacó el enriquecimiento del camino del acuse, y ADR-002 dice cuándo un tercero cayó y cuándo volvió. ¿Dónde espera lo que necesita al tercero caído, y cuándo y cómo se reprocesa?

##### Decisión

Cuando ADR-002 marca al tercero caído, o su adaptador vence el timeout, la declaración queda pendiente en un tópico durable y el archivo sigue acusado: no se rechaza. Un reprocesador corre cada noche a hora fija; si el tercero ya volvió, reprocesa lo pendiente a ritmo propio, y si sigue caído, lo reprograma para la noche siguiente. Al agotar los días de reintento, notifica al remitente sin sacar el archivo de los pendientes, y lo sigue reprocesando hasta que el tercero vuelva. Lo hacemos porque cumple ASR-D01 sin perder nada, y porque reprocesar en una ventana fija, a ritmo propio, evita el estallido (C-04).

##### Consecuencias

- (+) ASR-D01: la caída nunca produce un rechazo; todo pendiente tiene ventana, reintento y desenlace.
- (+) El reproceso corre en el valle nocturno, a un ritmo que fija el reprocesador y no el regreso del tercero.
- (−) Lo pendiente espera hasta la noche aunque el tercero vuelva en minutos (SUP-10).
- (−) Los pendientes de una caída larga ocupan almacenamiento hasta que el tercero vuelve.
- (±) La hora de la ventana y los días de reintento los fija BCR (§4).

##### Anexo de trazabilidad

**Y-statement:** En el contexto de terceros que caen durante horas y de la detección de ADR-002, frente a ASR-D01, decidimos pendientes durables con una ventana nocturna de reproceso, reintento diario y notificación sin abandonar el archivo, y descartamos el drenaje inmediato al volver el tercero y el reintento síncrono, para lograr que todo archivo válido termine aceptado sin estallido, aceptando que lo pendiente espere hasta la noche.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos válidos sin aceptar tras la ventana | satisface | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-12 | Retry | diferido-por-cola | Reintento | Disponibilidad · recuperar |
| DIS-14 | Graceful Degradation | fallback | Degradación con gracia | Disponibilidad · recuperar |

**A3. Alternativas**

Drivers: ASR-D01, R-13, C-04, SUP-10, fijados antes de evaluar.

| Criterio | A: Pendientes + ventana nocturna (elegida) | B: Drenaje inmediato con tasa limitada al volver | C: Reintento síncrono con espera creciente |
|---|---|---|---|
| ASR-D01 todo válido aceptado | ++ cada pendiente tiene ventana, reintento y notificación | + se reprocesa al volver, sin desenlace si no vuelve | − al agotar reintentos, rechaza o bloquea |
| C-04 estallido al volver | ++ el ritmo lo fija la ventana | + la tasa limitada lo contiene, si se calibra | −− todos reintentan a la vez |
| SUP-10 inmediatez al volver | − espera hasta la noche | ++ reprocesa apenas vuelve | + reintenta en minutos |
| R-12 prueba local | + un planificador | − un gestor de drenaje con su tasa | ++ una política de reintento |

Descartes: B: gana en inmediatez, pero no da desenlace a lo que sigue pendiente después de días, y BCR acordó la ventana nocturna el 6-nov. C, KO: con caídas de horas agota los hilos y termina rechazando (ASR-D01, R-13).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-003a | Trade-off | Ventana nocturna: control del ritmo ▲, inmediatez al volver el tercero ▼ | SUP-10 |
| R-003a | Riesgo | Tras una caída de varios días, una sola ventana no alcanza a reprocesar lo acumulado | El reprocesador trabaja por lotes y la ventana dura lo suficiente |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Caer el tercero simulado, enviar archivos válidos, disparar la ventana con el tercero caído y de vuelta, agotar los reintentos y restablecerlo | 0 archivos válidos sin aceptar al cierre de la primera ventana tras el regreso, también los notificados |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: ADR-002, ADR-004 · conflicto-con: —

#### ADR-004: Llave de idempotencia por archivo, registrada antes del acuse

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — depende de que el XSD traiga los cuatro campos de la llave (SUP-09)

##### Contexto

El reproceso nocturno de ADR-003 y la entrega al menos una vez de ADR-001 pueden aplicar dos veces la misma declaración, y ASR-D01 cuenta como fallo un archivo válido que no termina aceptado una sola vez. La historia HU-06 pide además rechazar como duplicado el reenvío de un archivo pendiente, sin tocar el original. En la reunión se propuso la llave entidad + fecha + lote + consecutivo. ¿Cómo se reconoce un archivo ya recibido, y cómo se evita aplicar dos veces lo reprocesado?

##### Decisión

Registraremos la llave del archivo, con restricción de unicidad, en la misma transacción local que registra el archivo, antes del acuse. Un archivo cuya llave ya existe se rechaza como duplicado. Cada declaración lleva la llave (archivo, declaración), y su resultado se escribe una sola vez por esa llave. Lo hacemos porque evita duplicados sin transacciones distribuidas y con una sola consulta por archivo.

##### Consecuencias

- (+) ASR-D01: un evento repetido o un reproceso no producen un segundo resultado.
- (+) HU-06: el reenvío se rechaza como duplicado y el original sigue su curso.
- (−) Una escritura más en la recepción, antes del acuse.
- (−) Si la llave no identifica el archivo sin ambigüedad, dos archivos distintos chocarían (SUP-09).

##### Anexo de trazabilidad

**Y-statement:** En el contexto de una cola al menos una vez y un reproceso nocturno, frente a ASR-D01, decidimos una llave de negocio única registrada antes del acuse, y descartamos el hash del contenido y la deduplicación en la cola, para lograr que reenvíos y reprocesos no dupliquen nada, aceptando una escritura más en la recepción.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | 0 archivos válidos sin aceptar tras la ventana | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-17 | Transaction (ACID) | local | Transacciones | Disponibilidad · prevenir |

**A3. Alternativas**

Drivers: ASR-D01, R-13, HU-06, fijados antes de evaluar.

| Criterio | A: Llave de negocio única (elegida) | B: Hash del contenido | C: Deduplicación en la cola |
|---|---|---|---|
| Reproceso sin duplicar | ++ la llave de declaración es estable | + estable | − la cola no cubre el reproceso nocturno |
| Reenvío rechazado | ++ la llave identifica el lote | − un reenvío firmado de nuevo cambia los bytes y pasa | − la ventana de la cola es corta, no de días |
| R-13 nada se descarta en silencio | + el duplicado se rechaza con causa | + igual | − el duplicado desaparece sin aviso |

Descartes: B: un lote firmado de nuevo cambia los bytes y se cuela como archivo nuevo. C: su ventana no cubre esperas de días y descarta sin avisar (R-13).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-004a | Trade-off | Unicidad antes del acuse: corrección ▲, una escritura más en el acuse ▼ | La escritura cabe en el tiempo del acuse |
| R-004a | Riesgo | Falsos duplicados si la llave no es única en el negocio | SUP-09 |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Reenviar un archivo pendiente y repetir eventos del reproceso | 0 archivos o resultados duplicados |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

### 2.2 ASR-L04 · Aislamiento de carga entre archivos masivos y pequeños

Una decisión: dos carriles aislados, sobre el pipeline de ADR-001.

#### ADR-005: Dos carriles aislados con enrutador por tamaño al recibir

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — el aislamiento se sostiene por construcción, pero el umbral entre carriles no está fijado y el quiebre con más de 5 masivos no se ha medido

##### Contexto

ASR-L04 exige p99 ≤ 3 s por archivo pequeño con 5 masivos en proceso. En la reunión del 6-nov se acordó que los masivos de un remitente grande no pueden degradar la respuesta a los demás, y C-02 prohíbe componentes de arranque lento en el camino de los pequeños. ADR-001 dejó etapas que consumen de una cola, pero no dijo si todos los archivos comparten la misma. ¿Cómo se reparte la capacidad para que un masivo nunca le quite capacidad a un pequeño?

##### Decisión

Separaremos dos carriles, cada uno con su tópico, su pool y su despliegue: el carril A procesa pequeños en un flujo rápido, y el carril B procesa masivos por lotes. Al terminar de recibir un archivo, la recepción lo clasifica por su tamaño en bytes, sin abrirlo (SUP-07), y lo publica en el tópico de su carril. Lo hacemos porque el aislamiento por construcción es la única forma de sostener los 3 s de ASR-L04 sin depender de la carga ajena.

##### Consecuencias

- (+) ASR-L04: un masivo no puede consumir capacidad del carril A.
- (+) Una saturación del carril B no llega al carril A.
- (−) Capacidad ociosa en el carril B cuando no hay masivos; BCR la acepta (C-03).
- (−) Dos despliegues y dos pools que dimensionar, y un umbral que calibrar.
- (±) Un archivo mal clasificado solo cambia de carril; no se pierde.

##### Anexo de trazabilidad

**Y-statement:** En el contexto del pipeline de ADR-001, frente a ASR-L04, decidimos dos carriles aislados con enrutador por tamaño, y descartamos un pool compartido con prioridad y uno con cuotas, para lograr un aislamiento que no depende de la carga ajena, aceptando capacidad ociosa y dos despliegues.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-L04 | Latencia | p99 ≤ 3 s por pequeño | satisface | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| DIS-22 | Bulkhead | — | Bulkhead | Disponibilidad · prevenir |
| LAT-03 | Prioritize events | — | Priorizar eventos | Desempeño · controlar la demanda |
| LAT-12 | Schedule resources | — | Programar los recursos | Desempeño · gestionar recursos |

**A3. Alternativas**

Drivers: ASR-L04, C-02, C-03, fijados antes de evaluar.

| Criterio | A: Carriles dedicados (elegida) | B: Pool compartido con prioridad | C: Pool compartido con cuotas |
|---|---|---|---|
| ASR-L04 3 s con masivos | ++ aislamiento por construcción | − un masivo en curso retiene hilos que la prioridad no libera | + la cuota protege si se dimensiona bien |
| C-02 sin arranque lento en el camino rápido | ++ los carriles no comparten nada | − el mismo pool sirve a los dos | − el mismo pool sirve a los dos |
| C-03 capacidad ociosa | − capacidad reservada en los valles | ++ sin capacidad ociosa | + poca capacidad ociosa |

Descartes: B: la prioridad no expropia a un masivo que ya ocupa el pool, y ASR-L04 queda a merced de la carga ajena. C: la cuota es un parámetro sensible que hay que reajustar con cada cambio de carga.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-005a | Sensibilidad | El p99 de los pequeños depende del tamaño del pool del carril A | La carga normal es de 400 pequeños/min |
| TO-005a | Trade-off | Carriles dedicados: latencia de pequeños ▲, eficiencia de capacidad ▼ | C-03 |
| R-005a | Riesgo | Con más de 5 masivos el carril B se satura, aunque el A no lo note | Nadie ha dicho qué pasa con el sexto masivo |
| NR-005a | No-riesgo | Enrutar por bytes no exige abrir el archivo | SUP-07 |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Experimento | Pequeños con 5 masivos en proceso; después, con más de 5 hasta el quiebre | p99 ≤ 3 s por pequeño con 5 masivos |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

### 2.3 ASR-S06 · Ningún archivo se acepta sin validación criptográfica completa

Tres decisiones: verificar todo en la recepción y retener el resultado (ADR-006), comprobar la revocación sin depender en línea de la ECD (ADR-007) y conservar la evidencia que da el no repudio (ADR-008).

#### ADR-006: Verificación criptográfica completa en la recepción, con resultado retenido hasta verificar

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Baja — verificar 50 MB en streaming dentro del segundo no se ha medido (SUP-01)

##### Contexto

ASR-S06 exige que ningún archivo se acepte sin firma, firmante, cadena, estampa y revocación verificadas. R-1 fija la firma XML, que obliga a canonicalizar y resumir el documento. BCR aprobó que un masivo cuya verificación no quepa en el segundo reciba un acuse de custodia (C-05), y R-14 deja el canal fuera. ¿Qué se verifica antes del acuse, y cómo se impide que un archivo sin verificar llegue a resultado definitivo?

##### Decisión

Verificaremos en la recepción, con un parser en streaming sin DTD ni entidades externas, sobre los mismos bytes que se custodian y se procesan: la firma, que el firmante sea el remitente, la cadena hasta una raíz aceptada, la estampa y la revocación. Si la verificación cabe en el segundo, el acuse confirma la autenticidad; si no, sale un acuse de custodia y la verificación sigue en paralelo. En los dos casos, el resultado definitivo queda retenido hasta que la verificación termine bien. Lo hacemos porque cumple ASR-S06 sin frenar la recepción, y porque procesar los mismos bytes verificados cierra la firma envuelta.

##### Consecuencias

- (+) ASR-S06: ningún camino llega a aceptación sin verificación completa.
- (−) Un acuse de custodia no prueba autenticidad: el remitente espera el resultado.
- (−) Verificar en streaming un XML de 50 MB es lo más incierto del diseño (SUP-01).
- (±) El estado «verificación pendiente» lo comparten ADR-006 y ADR-007.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de archivos XML firmados de hasta 50 MB, frente a ASR-S06, decidimos verificar firma, firmante, cadena, estampa y revocación en streaming en la recepción y retener el resultado hasta verificar, y descartamos verificar siempre antes del acuse y exigir firma separada sobre el hash, para lograr que nada sin verificar se acepte, aceptando acuses de custodia en los masivos.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | satisface | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| SEG-02 | Authenticator | un-factor | Autenticar actores | Seguridad · resistir |
| SEG-11 | Verify message integrity | — | Verificar integridad | Seguridad · detectar |
| SEG-08 | Validate input | — | Validar la entrada | Seguridad · resistir |

**A3. Alternativas**

Drivers: ASR-S06, R-1, R-14, C-05, fijados antes de evaluar.

| Criterio | A: Streaming + resultado retenido (elegida) | B: Verificación completa antes del acuse | C: Firma separada sobre el hash |
|---|---|---|---|
| ASR-S06 nada sin verificar | ++ retiene el resultado | ++ verifica antes de todo | + verifica el hash |
| C-05 recepción sin bloqueo en masivos | + nunca espera | −− el acuse del masivo espera la verificación | + nunca espera |
| R-1 XML firmado | + respeta el formato | + respeta el formato | KO cambia el contrato con los remitentes |

Descartes: B: deja a un remitente de masivos sin acuse mientras se verifica. C, KO: viola R-1, porque obliga a los remitentes a firmar de otra forma.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-006a | Sensibilidad | El tiempo de verificación crece con el tamaño del archivo | SUP-01 |
| TO-006a | Trade-off | Acuse de custodia: recepción sin bloqueo ▲, autenticidad en el acuse ▼ | BCR acepta que ese acuse no certifique la firma |
| R-006a | Riesgo | Un parser que arma el árbol completo reabre la firma envuelta y agota la memoria | El parser trabaja en streaming sobre el elemento firmado |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Catálogo con firma alterada, firma envuelta, firmante distinto, certificado fuera de la cadena, estampa desfasada y estampa vencida | 0 archivos aceptados sin verificación completa |
| Experimento | Verificación en streaming de archivos de 1 registro a 50 MB, con tiempo y memoria (SUP-01) | Ningún archivo de 50 MB carga el árbol completo; el tiempo por tamaño decide el acuse de custodia |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

#### ADR-007: Revocación en caché local, con verificación pendiente que se reprocesa en la ventana nocturna

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — la ventana de revocación depende de SUP-03

##### Contexto

ASR-S06 exige comprobar la revocación antes de aceptar, y ASR-D01 cuenta a la ECD entre los sistemas cuya caída no puede dejar archivos válidos sin aceptar. Consultar a la ECD en línea por cada archivo ata la recepción a su disponibilidad. ADR-006 retiene el resultado hasta verificar, y ADR-003 ya reprocesa lo pendiente cada noche. ¿Cómo se comprueba la revocación sin depender en línea de la ECD, y qué pasa con un archivo cuya revocación no se pudo comprobar?

##### Decisión

Mantendremos localmente la lista de revocación de la ECD y las respuestas OCSP por certificado, refrescadas periódicamente. Si la información está vigente, la verificación es local. Si la ECD está caída y la información venció, el archivo recibe acuse en «verificación pendiente», no llega a resultado definitivo y entra a la ventana nocturna de ADR-003. Lo hacemos porque cumple ASR-S06 sin consultar a la ECD por archivo y reutiliza el desenlace que ASR-D01 ya exige.

##### Consecuencias

- (+) ASR-S06: nada se acepta con la revocación sin comprobar.
- (+) ASR-D01: la caída de la ECD no detiene la recepción, y sus pendientes siguen el mismo camino que los demás.
- (−) Un certificado revocado después del último refresco se acepta hasta el siguiente (R-007a).
- (−) Con la ECD caída varios días, los archivos se acumulan en verificación pendiente.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de la verificación de ADR-006, frente a ASR-S06 y ASR-D01, decidimos la revocación en caché local con verificación pendiente reprocesada en la ventana nocturna, y descartamos la consulta OCSP en línea y la información embebida en la estampa, para lograr una verificación local que no acepta nada sin verificar, aceptando una ventana de revocación.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | contribuye | primario |
| ASR-D01 | Disponibilidad | 0 archivos válidos sin aceptar tras la ventana | contribuye | afectado |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| LAT-10 | Maintain multiple copies of data | — | Mantener múltiples copias de datos | Desempeño · gestionar recursos |
| DIS-14 | Graceful Degradation | fallback | Degradación con gracia | Disponibilidad · recuperar |

**A3. Alternativas**

Drivers: ASR-S06, ASR-D01, fijados antes de evaluar.

| Criterio | A: Caché local + pendiente (elegida) | B: OCSP en línea por archivo | C: Revocación embebida en la estampa |
|---|---|---|---|
| ASR-S06 nada sin verificar | + pendiente hasta verificar | ++ información siempre fresca | − depende de que la ECD la incluya |
| ASR-D01 con la ECD caída | + recibe y deja pendiente | −− la caída de la ECD frena la recepción | + no depende de la ECD |

Descartes: B: ata la recepción a la disponibilidad de la ECD (ASR-D01). C: solo sirve si la ECD embebe la información en cada estampa, y nada lo garantiza.

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-007a | Trade-off | Periodo de refresco: disponibilidad ▲, ventana de revocación ▼ | SUP-03 |
| R-007a | Riesgo | Un certificado robado y revocado se acepta durante la ventana | SUP-03 |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | ECD simulada caída con la información vencida | 0 archivos aceptados sin verificación completa |

**A6. Relaciones:** reemplaza: — · refina: ADR-006 · depende-de: ADR-003 · conflicto-con: —

#### ADR-008: Registro de evidencia de solo anexar, firmado por BCR

**Estado:** Propuesta · **Fecha:** 2026-11-06 · **Iteración ADD:** 1 · **Confianza:** Media — patrón conocido, con una entrada por archivo y no por declaración

##### Contexto

El enunciado pide garantizar el no repudio. ASR-S06 lo sostiene, porque nada se acepta sin verificación completa, pero esa verificación solo prueba algo si queda como evidencia que el remitente no pueda negar. En la reunión del 6-nov se fijó la táctica del curso «registro de auditoría / no repudio». Si el resultado de ADR-006 solo queda en el repositorio, quien lo administra puede cambiarlo. ¿Dónde y cómo se conserva la prueba de que un archivo firmado llegó y se verificó?

##### Decisión

Por cada archivo, la recepción anexará a un registro de evidencia, separado del repositorio, una entrada con el hash de los bytes custodiados, el certificado del firmante, la estampa y el resultado de cada comprobación. BCR firma cada entrada, y el registro no admite modificar ni borrar. Lo hacemos porque da el no repudio con una sola entrada por archivo, sin cargar el camino de las declaraciones.

##### Consecuencias

- (+) ASR-S06: el remitente no puede negar un envío cuya firma, firmante y estampa quedaron verificados y registrados.
- (−) Una escritura y una firma más por archivo en la recepción.
- (−) Almacenamiento que crece sin borrar; la retención la fija la norma.
- (±) Detectar alteraciones del registro (S04) no se desarrolla en esta iteración.

##### Anexo de trazabilidad

**Y-statement:** En el contexto de la verificación de ADR-006, frente a ASR-S06, decidimos un registro de evidencia de solo anexar firmado por BCR, y descartamos guardar el resultado solo en el repositorio y encadenar por hash cada evento, para lograr no repudio con una entrada por archivo, aceptando una escritura y una firma más por archivo.

**A1. ASRs atendidos**

| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | contribuye | primario |

**A2. Tácticas**

| ID | Patrón | Variante | Táctica (curso) | Familia |
|---|---|---|---|---|
| SEG-18 | Security Logger and Auditor | firma | Registro de auditoría | Seguridad · recuperar |

**A3. Alternativas**

Drivers: ASR-S06, R-12, fijados antes de evaluar.

| Criterio | A: Evidencia de solo anexar, firmada (elegida) | B: Resultado solo en el repositorio | C: Cadena de hash por evento |
|---|---|---|---|
| ASR-S06 no repudio | ++ cada entrada prueba por sí sola | − el administrador la cambia sin rastro | ++ cualquier alteración rompe la cadena |
| R-12 costo en la prueba local | + una entrada firmada por archivo | ++ nada extra | − una entrada por evento y una cadena que secuenciar en paralelo |

Descartes: B: no da no repudio, porque quien administra la base puede alterar la prueba. C: resuelve también S04, que no es crítico, a costa de secuenciar la cadena en paralelo (pregunta 2 de BCR).

**A4. Sensibilidad, trade-offs y riesgos**

| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-008a | Trade-off | Firma por entrada: no repudio ▲, costo de recepción ▼ | Una entrada por archivo |
| R-008a | Riesgo | Si la llave de firma de BCR se compromete, la evidencia pierde valor | La llave vive fuera de la aplicación |

**A5. Confirmación**

| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inspección | Cada archivo aceptado del experimento tiene su entrada firmada, con el hash de los bytes procesados | 100 % de los aceptados con entrada y firma válidas |

**A6. Relaciones:** reemplaza: — · refina: ADR-006 · depende-de: — · conflicto-con: —

## 3. Matriz ASR × ADR

S: satisface · C: contribuye.

| ASR | Prioridad | ADR-001 | ADR-002 | ADR-003 | ADR-004 | ADR-005 | ADR-006 | ADR-007 | ADR-008 | Hueco |
|---|---|---|---|---|---|---|---|---|---|---|
| ASR-D01 | Crítica | C | C | S | C | — | — | C | — | — |
| ASR-L04 | Crítica | C | — | — | — | S | — | — | — | — |
| ASR-S06 | Crítica | — | — | — | — | — | S | C | C | — |

## 4. Abierto

**Preguntas a BCR:**

- ¿A qué hora corre la ventana nocturna y cuánto dura?
- ¿Cuántos días se reintenta un pendiente, y qué pasa después?
- ¿Cuál es el umbral en bytes entre los carriles, y a cuál van los medianos?
- ¿Qué latencia de un tercero cuenta como extrema?
- ¿Qué desfase y qué antigüedad de la estampa se toleran?
- ¿El XSD trae entidad, fecha, lote y consecutivo (SUP-09)?
- ¿Qué pasa con el sexto masivo (R-005a)?

**Supuestos:** SUP-01, SUP-03, SUP-07, SUP-09 y SUP-10 (§1.3).

**Riesgos abiertos:** R-002a el monitor no detecta datos errados. R-003a una caída de varios días desborda una ventana. R-004a falsos duplicados. R-006a firma envuelta con un parser de árbol completo. R-007a ventana de revocación. R-008a compromiso de la llave de firma de BCR.

**Fuera de alcance:** solo se desarrollan los tres ASR críticos del 6-nov; las decisiones de los demás ASR quedan en el historial de git. La autenticación TLS mutua (ADR-13 de BCR) sigue aprobada, pero no entra en esta iteración (R-14). La topología activo-activo (ADR-17 de BCR) es infraestructura. La integración con el Banco de la República queda fuera del reto.

**Nombres de las tácticas.** En cada A2, «Táctica (curso)» es el nombre exacto del catálogo del curso, y «Patrón» el nombre canónico en inglés: el de la ficha de la táctica o, si no tiene ficha, el de Bass. Cómo se aplica cada táctica lo dice la decisión del ADR.

**Sin catálogos ni plan de diagramas.** Por decisión del equipo (2026-11-06), el registro no lleva catálogo de elementos ni de conectores, plan de diagramas ni evidencia visual: los diagramas derivan sus elementos de las decisiones y las tácticas. Por eso no cumple completo el contrato `adr-log/v2`.
