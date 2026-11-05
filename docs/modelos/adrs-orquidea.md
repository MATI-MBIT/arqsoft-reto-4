---
contrato: adr-log/v2
sistema: Orquídea
fecha: 2026-11-04
alcance_atributos: [latencia, escalabilidad, disponibilidad, seguridad, modificabilidad, interoperabilidad]
title: Decisiones de arquitectura (ADR)
parent: Vistas de arquitectura
nav_order: 0
---
# ADRs — Orquídea

## 0. Índice
| ADR | Título | Estado | Confianza | Iteración | ASRs | Diagramas |
|---|---|---|---|---|---|---|
| ADR-001 | Pipeline asíncrono por etapas con acuse temprano sobre un log distribuido | Propuesta | Media | 1 | ASR-L01, ASR-E01, ASR-D01 | DG-CMP-001, DG-CMP-002, DG-SEQ-001, DG-DEP-001 |
| ADR-002 | Dos carriles aislados por tamaño, clasificados al recibir | Propuesta | Media | 1 | ASR-L04, ASR-E01, ASR-L05 | DG-CON-001, DG-SEQ-002 |
| ADR-003 | Bloques paralelos con planificación equitativa entre archivos en el carril pesado | Propuesta | Baja | 2 | ASR-L03, ASR-L05 | DG-CON-001, DG-SEQ-003 |
| ADR-004 | Verificación criptográfica en streaming en la recepción, con resultado retenido hasta verificar | Propuesta | Baja | 1 | ASR-S06, ASR-S01, ASR-L01 | DG-SEQ-001, DG-STM-001 |
| ADR-005 | Información de revocación en caché local con estado de verificación pendiente | Propuesta | Media | 2 | ASR-S01, ASR-S06, ASR-D01 | DG-SEQ-004, DG-STM-001 |
| ADR-006 | Adaptador por sistema externo con circuit breaker, caché y pendientes de enriquecimiento | Propuesta | Media | 1 | ASR-D01, ASR-I01 | DG-CMP-001, DG-SEQ-005, DG-STM-001 |
| ADR-007 | Entrega al menos una vez con consumidores idempotentes y outbox transaccional | Propuesta | Media | 2 | ASR-D03, ASR-D02 | DG-SEQ-006, DG-CLS-001 |
| ADR-008 | Reglas y esquemas como datos versionados con vigencia, fijados al recibir | Propuesta | Media | 1 | ASR-M01, ASR-M02 | DG-CMP-003, DG-CLS-001 |
| ADR-009 | Activación de versiones por configuración, con convivencia y reversión | Propuesta | Media | 2 | ASR-D04, ASR-M01 | DG-SEQ-007 |
| ADR-010 | Identidad del remitente por certificado y autorización por remitente en el acceso a datos | Propuesta | Alta | 2 | ASR-S03 | DG-SEQ-008, DG-DEP-001 |
| ADR-011 | Auditoría de solo anexar encadenada por hash, con una cadena por partición | Propuesta | Media | 2 | ASR-S04 | DG-SEQ-009, DG-CLS-001 |
| ADR-012 | Modelo canónico publicado como eventos y una proyección por área consumidora | Propuesta | Alta | 2 | ASR-I02 | DG-CMP-004, DG-CLS-001 |

## 1. Insumos
### 1.1 Contexto
Orquídea es la plataforma de BCR por la que los IMC y los titulares de cuentas de compensación reportan todas las operaciones de divisas del país. Es el reemplazo de un sistema existente, en un dominio maduro con referente real (el Sistema de Información Cambiaria del Banco de la República).
Funcionales primarios: recibir archivos XML firmados de 1 a 100 000 registros y acusarlos; verificar firma, estampa y certificado; validar cada declaración contra el XSD y las reglas cambiarias; enriquecerla con STM, ANTA, MAMC y RNIC; transformarla al modelo canónico, persistirla y entregarla a las áreas de BCR; notificar el resultado definitivo.
Actores: IMC y titulares (remitentes), Área de regulación cambiaria, Equipo de TI, Auditoría y áreas consumidoras de BCR. Sistemas externos: STM, ANTA, MAMC, RNIC y la entidad de certificación digital (ECD).
Hoy los masivos bloquean a los pequeños, la falla de un externo se vuelve falla de Orquídea y cada cambio de la Circular exige un redespliegue largo. Las pruebas de concepto corren en local con software libre (R-12).

### 1.2 ASRs
| ASR | Atributo | Fuente | Estímulo | Artefacto | Ambiente | Respuesta | Medida | Prioridad | Impacto |
|---|---|---|---|---|---|---|---|---|---|
| ASR-L04 | Latencia | Remitentes de archivos pequeños y masivos | Llegan archivos pequeños mientras 5 masivos están en proceso | Procesamiento completo de los archivos pequeños | A: 400 pequeños/min y 5 masivos | Los pequeños se procesan sin esperar a los masivos | p99 con 5 masivos ≤ 1,10 × p99 sin masivos, del último byte al resultado definitivo, en A | Crítica | Alto |
| ASR-D01 | Disponibilidad | STM, ANTA, MAMC, RNIC o la ECD | Uno deja de responder o responde con lentitud extrema, durante horas | Recepción y acuse de archivos | C1: un sistema externo degradado, carga de A | Se recibe y se acusa como siempre; ningún archivo se rechaza por la falla | p99 ≤ 1 s del último byte al acuse, en C1 | Crítica | Alto |
| ASR-S06 | Seguridad | Remitente con un archivo inválido o sin poder verificarse | La verificación criptográfica falla o queda incompleta | Paso de un archivo con acuse a resultado definitivo | A, y C1 con la ECD caída | No se acepta hasta verificar; si falla, se rechaza | 0 archivos aceptados sin firma, estampa y revocación verificadas, en A y C1 | Crítica | Alto |
| ASR-M01 | Modificabilidad · adaptativo | Junta Directiva de BCR | Regla cambiaria nueva sobre datos que ya llegan, con fecha de vigencia | Aplicación de reglas cambiarias | Ligado en despliegue, sin recompilar ni detener el flujo | Se implementa, se prueba y se pone en producción | ≤ 4 h-persona de la especificación aprobada a producción | Crítica | Alto |
| ASR-S01 | Seguridad | Remitente legítimo o con certificado vencido o revocado | Envía un archivo firmado de hasta 50 MB | Validación de firma, estampa y certificado | A: 400 pequeños/min y 5 masivos | Determina si todo es válido y vigente | p99 ≤ 200 ms del último byte al resultado de la validación criptográfica, en el 100 % de los archivos, en A | Alta | Alto |
| ASR-E01 | Escalabilidad | Remitentes de archivos pequeños | 1 200 pequeños/min durante 45 min, en ráfaga, con 5 masivos | Procesamiento completo de los pequeños | B: carga pico de 45 min | Todos se procesan | p99 ≤ 5 s del último byte al resultado definitivo, en cada minuto de B | Alta | Alto |
| ASR-I01 | Interoperabilidad | Área de TI de BCR | Un sistema externo publica una versión nueva de su contrato de API | Enriquecimiento y validación con ese sistema externo | Desarrollo · socio conocido · tercero fuera de control | Se interpreta como el externo quiso o se rechaza con diagnóstico | ≤ 4 h-persona de la recepción del contrato a producción | Alta | Alto |
| ASR-D03 | Disponibilidad | Nodo interno de procesamiento | Falla con archivos con acuse en curso | Declaraciones con acuse emitido | C2 sobre B: falla durante la carga pico | Cada declaración termina con exactamente un resultado | 0 declaraciones con acuse sin resultado o con más de uno, en C2 sobre B | Alta | Alto |
| ASR-L01 | Latencia | Remitente | Termina de enviar un archivo de hasta 50 MB | Recepción y acuse | A: 400 pequeños/min y 5 masivos | Entrega el acuse que confirma el inicio del procesamiento | p99 ≤ 1 s del último byte al acuse, en A | Alta | Alto |
| ASR-S03 | Seguridad | Remitente autenticado, con o sin malicia | Consulta, corrige o anula operaciones que reportó otro remitente | Consulta, corrección y anulación de declaraciones | A | Solo toca lo suyo; lo demás se niega y se audita | 0 accesos efectivos a operaciones de otro remitente, en A | Alta | Medio |
| ASR-D04 | Disponibilidad | Equipo de TI de BCR | Activa o revierte una regla, un XSD o un contrato de API | Recepción y acuse de archivos | E: cambio en producción con carga de A | Sigue recibiendo y acusando; nada se rechaza por el cambio | p99 ≤ 1 s del último byte al acuse durante el cambio, en E | Alta | Medio |
| ASR-L03 | Latencia | Remitente | Envía un masivo de 100 000 registros con otros 4 en proceso | Procesamiento completo del archivo masivo | A: 400 pequeños/min y 5 masivos | Resuelve todas sus declaraciones | ≤ 5 min por archivo masivo, del último byte al resultado definitivo, en A | Media | Alto |
| ASR-D02 | Disponibilidad | Nodo interno de procesamiento | Se cae con declaraciones en curso | Declaraciones que tenía en curso | C2: falla interna, carga de A | Otro nodo las retoma sin intervención manual | ≤ 10 s de la falla a que la última declaración afectada vuelve a avanzar, en C2 | Media | Medio |
| ASR-L05 | Latencia | Remitente | Envía un mediano de 51 a 5 000 registros con 5 masivos en proceso | Procesamiento completo del archivo mediano | A: 400 pequeños/min y 5 masivos | Deja disponible su resultado | p99 ≤ 30 s del último byte al resultado definitivo, en A | Media | Medio |
| ASR-M02 | Modificabilidad · adaptativo | Junta Directiva de BCR | Versión nueva del XSD sin tocar el modelo canónico | Validación de estructura | Ligado en despliegue | Se pone en producción y la versión anterior sigue validándose | ≤ 4 h-persona del XSD aprobado a producción | Media | Medio |
| ASR-S04 | Seguridad | Alguien con acceso al almacenamiento | Modifica, borra o reordena registros de auditoría | Auditoría de recepción, acuses y resultados | A | La verificación de integridad señala la alteración | 100 % de las alteraciones del catálogo detectadas, en A | Media | Medio |
| ASR-I02 | Integración | Área de BCR | Pide los datos en un formato nuevo | Entrega de datos transformados | Desarrollo · socio conocido · propio | Recibe el modelo canónico en su formato, con el mismo significado | 0 funciones del flujo principal modificadas | Baja | Bajo |

### 1.3 Restricciones, concerns y supuestos
| ID | Tipo | Descripción | Origen |
|---|---|---|---|
| R-1 | restricción | Archivos XML firmados digitalmente y con estampa cronológica de una ECD acreditada | enunciado |
| R-2 | restricción | Archivos de 1 a 100 000 registros y hasta 50 MB | enunciado |
| R-4 | restricción | Las reglas dependen de STM, ANTA, MAMC y RNIC, que BCR no puede modificar | enunciado |
| R-7 | restricción | Aceptación total, parcial o rechazo; cada declaración se acepta o rechaza sola | enunciado |
| R-8 | restricción | Se aplica la regla vigente en la fecha y hora de recepción del archivo | usuario |
| R-9 | restricción | Retención de 90 días en consulta inmediata y 10 años en almacenamiento no modificable | usuario |
| R-10 | restricción | Cifrado en tránsito y en reposo | enunciado |
| R-12 | restricción | Pruebas de concepto en local, con software libre, sin nube ni licencias de pago | usuario |
| C-01 | concern | BCR aprobó Apache Kafka en modo KRaft y PostgreSQL; se usan como productos de la prueba de concepto, no como decisiones cerradas | usuario |
| C-02 | concern | BCR pide trazar cada archivo del acuse a la notificación, también tras horas de espera | usuario |
| C-03 | concern | BCR acepta pagar capacidad reservada para los masivos aunque quede ociosa en los valles | usuario |
| SUP-01 | supuesto | Verificar la firma de un archivo de 50 MB en streaming cabe en 1 s en el hardware de la prueba | [SUPUESTO] |
| SUP-02 | supuesto | Los sistemas externos no aceptan consultas por lote; las tasas de STM cambian por publicación | [SUPUESTO] |
| SUP-03 | supuesto | La ECD publica su lista de revocación al menos una vez al día | [SUPUESTO] |
| SUP-04 | supuesto | Las reglas cambiarias nuevas sobre datos existentes se expresan con el lenguaje de reglas sin código nuevo | [SUPUESTO] |
| SUP-05 | supuesto | Un bloque de 1 000 registros es una unidad de trabajo adecuada para el carril pesado | [SUPUESTO] |
| SUP-06 | supuesto | La identidad de los ciudadanos se valida al registrar al actor, no en cada declaración | [SUPUESTO] |
| SUP-07 | supuesto | El tamaño en bytes de un archivo predice su número de registros lo bastante bien para elegir el carril | [SUPUESTO] |
| SUP-08 | supuesto | El tiempo de sesión del consumidor del log se configura en pocos segundos, no en el valor por defecto | [SUPUESTO] |

## 2. Catálogo de elementos
| EL | Nombre | Tipo UML | Estereotipo | Responsabilidad | Estado que posee | Eclosión | Introducido por |
|---|---|---|---|---|---|---|---|
| EL-01 | Remitente | actor | «external» | Envía archivos firmados, recibe acuses y resultados, consulta lo propio | ninguno | exento («external») | contexto |
| EL-02 | Recepción y verificación | component | «component» | Recibe el archivo, lo custodia, verifica firma y estampa, acusa y lo clasifica por tamaño | ninguno (stateless) | obligatoria | ADR-001, ADR-002, ADR-004 |
| EL-03 | Custodia de archivos | datastore | «database» | Guarda cifrados los bytes recibidos, que son los mismos que se procesan | archivos recibidos | exento («COTS») | ADR-001, ADR-004 |
| EL-04 | Log distribuido de eventos | queue | «queue» «COTS» | Tópicos durables por carril, pendientes, resultados y eventos canónicos | eventos con su posición por consumidor | exento («COTS») | ADR-001 |
| EL-05 | Carril liviano | component | «tactic» {id = DIS-22} | Procesa archivos pequeños con su propio pool | ninguno (stateless) | obligatoria | ADR-002 |
| EL-06 | Carril pesado | component | «tactic» {id = DIS-22} | Divide medianos y masivos en bloques, los planifica entre archivos y agrega el resultado | progreso de bloques en EL-04 | obligatoria | ADR-002, ADR-003 |
| EL-07 | Núcleo de validación | component | «component» | Valida XSD y reglas con la versión fijada, transforma al modelo canónico y aplica idempotencia | ninguno (stateless) | obligatoria | ADR-007, ADR-008 |
| EL-08 | Configuración versionada | component | «tactic» {id = MOD-09, variante = despliegue} | Registro de XSD, catálogo de reglas con vigencia, registro de conectores, activación y reversión | versiones y su vigencia | obligatoria | ADR-008, ADR-009 |
| EL-09 | Orquestador de enriquecimiento | component | «component» | Pide a cada adaptador lo que una declaración necesita y decide si queda pendiente | caché de referencia con vigencia | obligatoria | ADR-006 |
| EL-10 | Adaptadores de sistemas externos | component | «tactic» {id = INT-01, variante = interfaz-común} | Uno por externo: traduce su contrato de API y lo protege con timeout y circuit breaker | estado del circuito | obligatoria | ADR-006 |
| EL-11 | Gestor de pendientes | component | «component» | Reanuda las declaraciones pendientes con tasa limitada cuando el externo vuelve | ninguno; pendientes en EL-04 | obligatoria | ADR-006 |
| EL-12 | Resultado y notificación | component | «component» | Consolida el resultado por archivo, lo firma, lo notifica y lo expone a consulta | ninguno (stateless) | obligatoria | ADR-001 |
| EL-13 | Repositorio de declaraciones | datastore | «database» «COTS» | Persiste declaraciones, resultados y el outbox en una transacción, particionado por remitente | declaraciones, resultados, outbox | exento («COTS») | ADR-007, ADR-010 |
| EL-14 | Autorizador por remitente | component | «tactic» {id = SEG-03, variante = acl} | Filtra toda consulta y corrección por el remitente autenticado | ninguno (stateless) | obligatoria | ADR-010 |
| EL-15 | Auditoría inmutable | component | «tactic» {id = SEG-18, variante = cadena-hash} | Anexa eventos encadenados por hash y verifica la integridad | cadenas por partición | obligatoria | ADR-011 |
| EL-16 | Verificador de revocación | component | «component» | Responde la revocación desde la lista y las respuestas en caché, y las refresca | lista de revocación y respuestas en caché | obligatoria | ADR-005 |
| EL-17 | Proyectores por área | component | «component» | Convierten los eventos canónicos al formato de cada área | posición de lectura | obligatoria | ADR-012 |
| EL-18 | Sistemas externos (STM, ANTA, MAMC, RNIC) | actor | «external» | Entregan tasas, datos tributarios y aduaneros, migratorios e identidad | — | exento («external») | contexto |
| EL-19 | Entidad de certificación digital (ECD) | actor | «external» | Publica la lista de revocación y responde consultas de certificados | — | exento («external») | contexto |
| EL-20 | Áreas consumidoras de BCR | actor | «external» | Usan los datos en bases analíticas, motores de búsqueda y archivos planos | — | exento («external») | contexto |
| EL-21 | Equipo de TI de BCR | actor | «external» | Activa y revierte versiones de reglas, esquemas y conectores | — | exento («external») | contexto |

## 3. Catálogo de conectores
| CN | Desde | Hacia | Tipo | Protocolo o estereotipo | Introducido por |
|---|---|---|---|---|---|
| CN-01 | EL-01 | EL-02 | síncrono | «HTTPS» con TLS mutuo | ADR-001, ADR-010 |
| CN-02 | EL-02 | EL-03 | síncrono | «escritura durable» cifrada | ADR-001 |
| CN-03 | EL-02 | EL-16 | síncrono | «consulta local de revocación» | ADR-005 |
| CN-04 | EL-16 | EL-19 | asíncrono | «descarga de lista de revocación y OCSP» periódica | ADR-005 |
| CN-05 | EL-02 | EL-04 | asíncrono | «pub/sub» al tópico del carril | ADR-001, ADR-002 |
| CN-06 | EL-04 | EL-05 | asíncrono | «consumir» tópico liviano | ADR-002 |
| CN-07 | EL-04 | EL-06 | asíncrono | «consumir» tópico pesado por bloques | ADR-002, ADR-003 |
| CN-08 | EL-05 | EL-07 | síncrono | llamada local | ADR-002 |
| CN-09 | EL-06 | EL-07 | síncrono | llamada local por bloque | ADR-003 |
| CN-10 | EL-07 | EL-08 | síncrono | «leer versión fijada al recibir» | ADR-008 |
| CN-11 | EL-07 | EL-09 | síncrono | «enriquecer declaración» | ADR-006 |
| CN-12 | EL-09 | EL-10 | síncrono | «IConsultaExterna» | ADR-006 |
| CN-13 | EL-10 | EL-18 | síncrono | «HTTPS» con timeout y circuit breaker | ADR-006 |
| CN-14 | EL-09 | EL-04 | asíncrono | «pub/sub» tópico de pendientes | ADR-006 |
| CN-15 | EL-11 | EL-09 | asíncrono | «reanudar» con tasa limitada | ADR-006 |
| CN-16 | EL-07 | EL-13 | síncrono | «JDBC» transacción con outbox | ADR-007 |
| CN-17 | EL-13 | EL-04 | asíncrono | «outbox → pub/sub» eventos canónicos y resultados | ADR-007, ADR-012 |
| CN-18 | EL-04 | EL-12 | asíncrono | «consumir» resultados | ADR-001 |
| CN-19 | EL-12 | EL-01 | asíncrono | «notificación firmada» y consulta | ADR-001 |
| CN-20 | EL-04 | EL-17 | asíncrono | «consumir» eventos canónicos | ADR-012 |
| CN-21 | EL-17 | EL-20 | flujo de datos | formato de cada área | ADR-012 |
| CN-22 | EL-14 | EL-13 | síncrono | «consulta filtrada por remitente» | ADR-010 |
| CN-23 | EL-01 | EL-14 | síncrono | «HTTPS» con TLS mutuo | ADR-010 |
| CN-24 | EL-02 | EL-15 | asíncrono | «evento de auditoría» | ADR-011 |
| CN-25 | EL-12 | EL-15 | asíncrono | «evento de auditoría» | ADR-011 |
| CN-26 | EL-04 | EL-11 | asíncrono | «consumir» tópico de pendientes | ADR-006 |
| CN-27 | EL-21 | EL-08 | síncrono | «activar / revertir versión» | ADR-009 |

## 4. ADRs

### ADR-001: Pipeline asíncrono por etapas con acuse temprano sobre un log distribuido

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 1 · **Confianza:** Media — estilo probado en el referente real, pero SUP-01 y la carga de A no se han medido

#### Contexto
ASR-L01 exige el acuse en p99 ≤ 1 s desde el último byte (Ambiente A), aunque el archivo traiga 100 000 registros. ASR-E01 exige p99 ≤ 5 s para los pequeños durante 45 minutos de carga pico (B), y ASR-D01 exige el mismo acuse en p99 ≤ 1 s con un sistema externo caído (C1). Hoy todo pasa por un único flujo síncrono, y validar y enriquecer cien mil registros no cabe en un segundo. R-4 deja cuatro dependencias externas fuera del control de BCR. ¿Qué estilo global separa la recepción del procesamiento para que el acuse no dependa ni del tamaño del archivo ni de los sistemas externos?

#### Decisión
Adoptaremos un pipeline de etapas asíncronas comunicadas por un log distribuido durable: la recepción solo custodia el archivo, lo verifica y acusa, y publica un evento; validación, enriquecimiento, resultado y distribución consumen eventos a su propio ritmo. Usaremos Apache Kafka en modo KRaft para el log (C-01). Lo elegimos porque es la única opción que saca del camino del acuse todo lo que depende del tamaño o de terceros (ASR-L01, ASR-D01) y convierte el pico en profundidad de cola (ASR-E01).

#### Consecuencias
- (+) ASR-L01 y ASR-D01: el acuse solo depende de la custodia y la verificación, no de la validación ni de los externos.
- (+) ASR-E01: el log absorbe el pico y cada etapa escala por separado.
- (−) El remitente ve un acuse, no un resultado: el resultado definitivo llega después (consistencia eventual).
- (−) Operación: un log que hay que monitorear (rezago por tópico) y trazar de punta a punta (C-02).
- (±) La entrega del log es al menos una vez; exige consumidores idempotentes (ADR-007).

#### Anexo de trazabilidad

**Y-statement:** En el contexto de un sistema que recibe archivos de 1 a 100 000 registros con cuatro dependencias externas, frente a ASR-L01, ASR-E01 y ASR-D01, decidimos un pipeline asíncrono con acuse temprano y descartamos el monolito síncrono escalado y los microservicios síncronos para lograr un acuse independiente del tamaño y de terceros, aceptando consistencia eventual y la operación de un log.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-L01 | Latencia | p99 ≤ 1 s al acuse en A | satisface | primario |
| ASR-E01 | Escalabilidad | p99 ≤ 5 s en B | contribuye | primario |
| ASR-D01 | Disponibilidad | p99 ≤ 1 s al acuse en C1 | contribuye | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| EST-07 | — | — | Pipe and Filter (pipeline por etapas) | Estilo | EL-02, EL-05, EL-06, EL-12 | «tactic» {id = EST-07} |
| ESC-05 | — | — | Asincronía y colas | Escalabilidad · ritmo | EL-04 | «tactic» {id = ESC-05} |

**A3. Alternativas**
Drivers: ASR-L01 (Alta), ASR-E01 (Alta), ASR-D01 (Crítica), R-4, R-12 — fijados antes de evaluar.
| Criterio | Opción A: Pipeline asíncrono (elegida) | Opción B: Monolito síncrono escalado | Opción C: Microservicios síncronos |
|---|---|---|---|
| ASR-L01 acuse ≤ 1 s | ++ el acuse no espera la validación | KO validar 100 000 registros no cabe en 1 s | − cadena de llamadas antes de responder |
| ASR-E01 pico ≤ 5 s | + el pico se vuelve profundidad de cola | − escala el monolito completo | + escala por servicio |
| ASR-D01 externo caído | + el acuse no toca externos | −− el externo caído bloquea la respuesta | −− la falla se propaga por la cadena |
| R-4 externos fuera de control | + los externos quedan detrás de una etapa | − llamadas en línea por registro | − llamadas en línea por servicio |
| R-12 prueba de concepto local | − un log distribuido que montar y operar | ++ un solo proceso | − varios servicios y su red |
Descartes: B — KO: no cumple ASR-L01 con archivos masivos. C — la cadena síncrona propaga la falla de un externo hasta el acuse (ASR-D01).

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-001a | Sensibilidad | El p99 del acuse depende de la escritura durable en la custodia y en el log | La custodia y el log están en el mismo segmento de red que la recepción |
| TO-001a | Trade-off | Asincronía: escalabilidad y disponibilidad ▲, inmediatez del resultado ▼ | BCR acepta acuse inmediato y resultado posterior |
| R-001a | Riesgo | Sin trazas de punta a punta, un archivo retenido horas es imposible de seguir (C-02) | Se propaga un identificador de correlación en cada evento |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Prueba de carga | Mezcla de tamaños en A, midiendo del último byte al acuse | p99 ≤ 1 s del último byte al acuse, en A |
| Inyección de fallas | Cortar cada externo durante la carga de A | p99 ≤ 1 s del último byte al acuse, en C1 |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-CMP-001, DG-CMP-002, DG-SEQ-001, DG-DEP-001 (detalle en §6)

### ADR-002: Dos carriles aislados por tamaño, clasificados al recibir

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 1 · **Confianza:** Media — el aislamiento se argumenta por construcción, pero el punto de quiebre con más de 5 masivos no se ha medido

#### Contexto
ASR-L04 exige que, con 5 masivos en proceso, el p99 de los pequeños suba como máximo 10 % (Ambiente A). ASR-E01 exige p99 ≤ 5 s para los pequeños en la carga pico, y ASR-L05 exige p99 ≤ 30 s para los medianos con 5 masivos en proceso. ADR-001 dejó el procesamiento en etapas que consumen del log, pero no dijo si todos los archivos comparten la misma etapa. ¿Cómo se reparte la capacidad de procesamiento para que un masivo nunca quite capacidad a un pequeño?

#### Decisión
Separaremos dos carriles con tópico, pool y despliegue propios: uno liviano para archivos pequeños y uno pesado para medianos y masivos. La recepción clasifica por el tamaño en bytes, que conoce al terminar de recibir el archivo sin abrirlo, con un umbral equivalente a 50 registros (SUP-07), porque el aislamiento por construcción es la única forma de sostener el +10 % de ASR-L04 sin depender de cómo se comporte la carga ajena.

#### Consecuencias
- (+) ASR-L04 y ASR-E01: un masivo no puede consumir capacidad del carril liviano aunque lo intente.
- (+) Una falla o saturación del carril pesado no afecta al liviano.
- (−) Capacidad ociosa en el carril pesado cuando no hay masivos; BCR la acepta por escrito.
- (−) Operación: dos despliegues y dos pools que dimensionar.
- (±) Los medianos van al carril pesado; su plazo (ASR-L05) depende de cómo se planifiquen los bloques (ADR-003).

#### Anexo de trazabilidad

**Y-statement:** En el contexto del pipeline de ADR-001, frente a ASR-L04 y ASR-E01, decidimos dos carriles aislados por tamaño y descartamos un pool compartido con prioridad y uno con cuotas para lograr un aislamiento que no depende de la carga ajena, aceptando capacidad ociosa y dos despliegues.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-L04 | Latencia | p99 con masivos ≤ 1,10 × p99 sin masivos | satisface | primario |
| ASR-E01 | Escalabilidad | p99 ≤ 5 s en B | satisface | primario |
| ASR-L05 | Latencia | p99 ≤ 30 s en A | contribuye | afectado |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| DIS-22 | — | — | Bulkhead (compartimentos) | Disponibilidad · prevenir (contener) | EL-05, EL-06 | «tactic» {id = DIS-22} |
| LAT-03 | — | — | Priorizar eventos (clasificar al recibir) | Desempeño · controlar la demanda | EL-02 | «tactic» {id = LAT-03} |

**A3. Alternativas**
Drivers: ASR-L04 (Crítica), ASR-E01 (Alta), ASR-L05 (Media), C-03 — fijados antes de evaluar.
| Criterio | Opción A: Carriles dedicados (elegida) | Opción B: Pool compartido con prioridad | Opción C: Pool compartido con cuotas |
|---|---|---|---|
| ASR-L04 +10 % | ++ aislamiento por construcción | − un masivo en curso retiene hilos que la prioridad no libera | + la cuota protege si se dimensiona bien |
| ASR-E01 pico | + el carril liviano escala solo | + prioridad atiende primero a los pequeños | 0 la cuota fija limita el pico |
| ASR-L05 medianos | 0 depende de ADR-003 | + el mediano compite con prioridad media | + cuota propia |
| C-03 capacidad ociosa | − capacidad reservada en los valles | ++ sin capacidad ociosa | + poca capacidad ociosa |
Descartes: B — la prioridad no expropia un masivo que ya ocupa el pool, y ASR-L04 queda sujeto a la carga ajena. C — mejor en eficiencia, pero la cuota es un parámetro sensible que hay que ajustar con cada cambio de carga; se compara contra A en la prueba de concepto.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-002a | Sensibilidad | El p99 de ASR-E01 depende del tamaño del pool del carril liviano | La carga pico es de 1 200 pequeños/min |
| TO-002a | Trade-off | Carriles dedicados: latencia de pequeños ▲, eficiencia de capacidad ▼ | BCR acepta pagar capacidad ociosa |
| R-002a | Riesgo | Con más de 5 masivos el carril pesado se satura, aunque el liviano no lo note | Nadie ha dicho qué pasa con el sexto masivo |
| NR-002a | No-riesgo | Clasificar por bytes no exige abrir el archivo, y un archivo mal clasificado solo cambia de carril | SUP-07 |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Experimento | Opción A contra opción C con 0, 5 y más masivos, misma carga de pequeños | p99 con 5 masivos ≤ 1,10 × p99 sin masivos, en A |
| Prueba de carga | 45 min a 1 200 pequeños/min con 5 masivos | p99 ≤ 5 s en cada minuto de B |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-CON-001, DG-SEQ-002 (detalle en §6)

### ADR-003: Bloques paralelos con planificación equitativa entre archivos en el carril pesado

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Baja — el tamaño de bloque (SUP-05) y la equidad entre archivos no se han medido

#### Contexto
ASR-L03 exige que cada uno de 5 masivos concurrentes termine en ≤ 5 min: unos 333 registros por segundo por archivo. ASR-L05 exige p99 ≤ 30 s para un mediano que llega con 5 masivos en proceso. ADR-002 puso medianos y masivos en el mismo carril pesado. Si ese carril atiende un archivo completo a la vez, el mediano espera detrás de los masivos. ¿Cómo procesa el carril pesado varios archivos a la vez sin que el más grande acapare la capacidad?

#### Decisión
Dividiremos cada archivo del carril pesado en bloques de K registros y los planificaremos por turnos entre archivos, de modo que cada archivo en curso avance un bloque por ronda. El agregador consolida el resultado cuando llegan todos los bloques. Lo hacemos porque la planificación por turnos da al mediano una fracción del carril desde que llega, que es lo que exige ASR-L05, mientras los masivos avanzan en paralelo (ASR-L03).

#### Consecuencias
- (+) ASR-L03: los bloques de un masivo se procesan en paralelo.
- (+) ASR-L05: un mediano avanza desde su primera ronda, sin esperar a que termine un masivo.
- (−) Consolidar el resultado por archivo exige contar bloques y tolerar que lleguen en desorden.
- (−) Un bloque con un registro corrupto no puede bloquear el tópico: hace falta aislarlo (R-003a).
- (±) K es un parámetro sensible (S-003a) que fija la prueba de concepto.

#### Anexo de trazabilidad

**Y-statement:** En el contexto del carril pesado de ADR-002, frente a ASR-L03 y ASR-L05, decidimos bloques paralelos con planificación por turnos entre archivos y descartamos un archivo por worker y bloques en orden de llegada para lograr que el mediano no espere a los masivos, aceptando el costo de consolidar bloques en desorden.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-L03 | Latencia | ≤ 5 min por masivo en A | satisface | primario |
| ASR-L05 | Latencia | p99 ≤ 30 s en A | satisface | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| ESC-06 | — | — | Dividir y paralelizar (splitter y aggregator) | Escalabilidad · ritmo | EL-06 | «tactic» {id = ESC-06} |
| LAT-12 | — | — | Programar los recursos (por turnos entre archivos) | Desempeño · gestionar recursos | EL-06 | «tactic» {id = LAT-12} |

**A3. Alternativas**
Drivers: ASR-L03 (Media), ASR-L05 (Media), R-7 — fijados antes de evaluar.
| Criterio | Opción A: Bloques por turnos (elegida) | Opción B: Un archivo por worker | Opción C: Bloques en orden de llegada |
|---|---|---|---|
| ASR-L03 ≤ 5 min | + bloques en paralelo | − 100 000 registros en secuencia | + bloques en paralelo |
| ASR-L05 ≤ 30 s | ++ el mediano avanza desde la primera ronda | −− espera a que se libere un worker | − espera detrás de los bloques de los masivos |
| R-7 resultado por declaración | − consolidar bloques que llegan en desorden | + un solo worker arma el resultado completo | − consolidar bloques que llegan en desorden |
Descartes: B — no cumple ASR-L05 cuando los workers están ocupados con masivos. C — cumple ASR-L03, pero el mediano espera detrás de cientos de bloques ya encolados.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-003a | Sensibilidad | El plazo de ASR-L05 y el de ASR-L03 dependen del tamaño de bloque K | SUP-05 |
| R-003a | Riesgo | Un bloque con un registro corrupto se reintenta sin fin y bloquea el tópico | Los registros tóxicos se apartan a un canal de mensajes muertos tras N intentos |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Experimento | Barrer K con 5 masivos de 100 000 registros y medianos de 5 000 llegando entre ellos | ≤ 5 min por masivo y p99 ≤ 30 s para medianos, en A |

**A6. Relaciones:** reemplaza: — · refina: ADR-002 · depende-de: ADR-007 · conflicto-con: —

**A7. Evidencia visual:** DG-CON-001, DG-SEQ-003 (detalle en §6)

### ADR-004: Verificación criptográfica en streaming en la recepción, con resultado retenido hasta verificar

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 1 · **Confianza:** Baja — depende de SUP-01: verificar 50 MB en streaming dentro del segundo no se ha medido

#### Contexto
ASR-S06 exige que ningún archivo llegue a aceptación sin firma, estampa y revocación verificadas (A y C1). ASR-S01 exige el resultado de esa validación en p99 ≤ 200 ms en el 100 % de los archivos, y ASR-L01, el acuse en p99 ≤ 1 s. R-1 fija que el archivo llega firmado en XML, y la firma XML obliga a canonicalizar y resumir el documento completo. BCR aprobó que un masivo cuya verificación no quepa en el segundo reciba un acuse de custodia. ¿Qué se verifica antes del acuse, y cómo se impide que un archivo sin verificar llegue a resultado definitivo?

#### Decisión
Verificaremos firma y estampa en la recepción con un parser en streaming, sin DTD ni entidades externas, sobre los mismos bytes que se custodian y se procesan. Si la verificación cabe en el segundo, el acuse dice que el archivo es auténtico; si no, sale un acuse de custodia y la verificación sigue en paralelo. En los dos casos, el resultado definitivo queda retenido hasta que la verificación termine bien. Lo hacemos porque cumple ASR-S06 sin romper ASR-L01, y porque procesar los mismos bytes verificados cierra la firma envuelta.

#### Consecuencias
- (+) ASR-S06: ningún camino llega a aceptación sin verificación completa.
- (+) ASR-L01: el acuse nunca espera una verificación que no cabe en el segundo.
- (−) ASR-S01: en masivos, los 200 ms pueden no cumplirse; se mide en la prueba de concepto y se acuerda con BCR.
- (−) Un acuse de custodia no prueba autenticidad: el remitente tiene que esperar el resultado.
- (±) El estado «verificación pendiente» lo comparten ADR-004 y ADR-005.

#### Anexo de trazabilidad

**Y-statement:** En el contexto de archivos XML firmados de hasta 50 MB, frente a ASR-S06, ASR-S01 y ASR-L01, decidimos verificar en streaming en la recepción y retener el resultado hasta verificar, y descartamos verificar siempre antes del acuse y exigir firma separada sobre el hash para lograr que nada sin verificar se acepte sin frenar el acuse, aceptando acuses de custodia en masivos.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | satisface | primario |
| ASR-S01 | Seguridad | p99 ≤ 200 ms en el 100 % de los archivos | contribuye | primario |
| ASR-L01 | Latencia | p99 ≤ 1 s al acuse en A | contribuye | afectado |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| SEG-02 | Authenticator | un-factor | Autenticar actores (firma digital del archivo) | Seguridad · resistir | EL-02 | «tactic» {id = SEG-02, variante = un-factor} |
| SEG-11 | — | — | Verificar integridad (lo verificado es lo procesado) | Seguridad · detectar | EL-02, EL-03 | «tactic» {id = SEG-11} |
| SEG-08 | — | — | Validar la entrada (parser sin DTD ni entidades externas) | Seguridad · resistir | EL-02 | «tactic» {id = SEG-08} |

**A3. Alternativas**
Drivers: ASR-S06 (Crítica), ASR-S01 (Alta), ASR-L01 (Alta), R-1 — fijados antes de evaluar.
| Criterio | Opción A: Streaming + resultado retenido (elegida) | Opción B: Verificación completa siempre antes del acuse | Opción C: Firma separada sobre el hash del archivo |
|---|---|---|---|
| ASR-S06 nada sin verificar | ++ retiene el resultado | ++ verifica antes de todo | + verifica el hash |
| ASR-S01 ≤ 200 ms | − masivos pueden excederlo | − masivos pueden excederlo | ++ verificar un hash es inmediato |
| ASR-L01 acuse ≤ 1 s | + nunca espera | −− el acuse de un masivo espera la verificación | + nunca espera |
| R-1 XML firmado | + respeta el formato | + respeta el formato | KO cambia el contrato con los remitentes |
Descartes: B — rompe ASR-L01 con archivos masivos. C — KO: viola R-1, porque exige a los remitentes otra forma de firmar.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-004a | Sensibilidad | El tiempo de verificación crece con el tamaño del archivo | SUP-01 |
| TO-004a | Trade-off | Acuse de custodia: latencia del acuse ▲, prueba de autenticidad en el acuse ▼ | BCR acepta que el acuse de custodia no certifique la firma |
| R-004a | Riesgo | Un parser que construye el árbol completo reabre la firma envuelta y agota la memoria | El parser procesa en streaming y verifica el elemento firmado que se procesa |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Experimento | Verificación en streaming de archivos firmados de 1 registro a 50 MB | p99 ≤ 200 ms del último byte al resultado de la validación criptográfica, en A |
| Inyección de fallas | Archivos con firma alterada, firma envuelta, estampa inválida y certificado revocado | 0 archivos aceptados sin verificación completa |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-SEQ-001, DG-STM-001 (detalle en §6)

### ADR-005: Información de revocación en caché local con estado de verificación pendiente

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Media — la ventana de revocación depende de SUP-03

#### Contexto
ASR-S01 exige la validación criptográfica en p99 ≤ 200 ms. ASR-D01 exige que la caída de la ECD no frene el acuse (C1), y ASR-S06, que nada se acepte sin comprobar la revocación. Consultar a la ECD en línea por cada archivo ata la verificación a su disponibilidad. ADR-004 retiene el resultado hasta verificar. ¿Cómo se comprueba la revocación de un certificado sin depender en línea de la ECD?

#### Decisión
Mantendremos localmente la lista de revocación descargada de la ECD y las respuestas OCSP por certificado, refrescadas periódicamente. Si la información está vigente, la verificación es local; si la ECD está caída y la información venció, el archivo recibe acuse en estado «verificación pendiente» y no llega a resultado definitivo hasta comprobarse. Lo hacemos porque es la única opción que cumple ASR-S01 sin consultar a la ECD y que no acepta nada sin verificar (ASR-S06).

#### Consecuencias
- (+) ASR-S01: la revocación se responde desde memoria.
- (+) ASR-D01: la caída de la ECD no detiene la recepción ni el acuse.
- (−) Ventana de revocación: un certificado revocado después del último refresco se acepta hasta el siguiente (R-005a).
- (−) Con la ECD caída mucho tiempo, los archivos se acumulan en verificación pendiente.
- (±) Detectar los archivos aceptados dentro de esa ventana queda abierto, sin umbral (§7).

#### Anexo de trazabilidad

**Y-statement:** En el contexto de la verificación de ADR-004, frente a ASR-S01, ASR-D01 y ASR-S06, decidimos información de revocación en caché local con estado pendiente y descartamos la consulta OCSP en línea y confiar en la información embebida en la estampa para lograr verificación local sin aceptar nada sin verificar, aceptando una ventana de revocación.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S01 | Seguridad | p99 ≤ 200 ms | satisface | primario |
| ASR-D01 | Disponibilidad | p99 ≤ 1 s al acuse en C1 | contribuye | primario |
| ASR-S06 | Seguridad | 0 archivos aceptados sin verificación completa | contribuye | afectado |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| LAT-10 | — | — | Mantener múltiples copias de datos (lista de revocación en caché) | Desempeño · gestionar recursos | EL-16 | «tactic» {id = LAT-10} |
| DIS-14 | Graceful Degradation (dependencia dura → blanda) | fallback | Degradación con gracia (verificación pendiente) | Disponibilidad · recuperar | EL-02, EL-16 | «tactic» {id = DIS-14, variante = fallback} |

**A3. Alternativas**
Drivers: ASR-S01 (Alta), ASR-D01 (Crítica), ASR-S06 (Crítica) — fijados antes de evaluar.
| Criterio | Opción A: Caché local + pendiente (elegida) | Opción B: OCSP en línea por archivo | Opción C: Información de revocación embebida en la estampa |
|---|---|---|---|
| ASR-S01 ≤ 200 ms | ++ consulta en memoria | − una llamada de red por archivo | ++ sin consulta |
| ASR-D01 ECD caída | + el acuse sigue | −− la caída de la ECD frena la verificación | + no depende de la ECD |
| ASR-S06 nada sin verificar | + pendiente hasta verificar | ++ siempre información fresca | − depende de que la ECD la incluya |
Descartes: B — ata la recepción a la disponibilidad de la ECD (ASR-D01). C — solo sirve si la ECD embebe la información en cada estampa, y el enunciado no lo garantiza.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-005a | Trade-off | Periodo de refresco: disponibilidad y latencia ▲, ventana de revocación ▼ | SUP-03 |
| R-005a | Riesgo | Un certificado robado y revocado se acepta durante la ventana | SUP-03 |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Prueba de carga | Verificación con información vigente bajo carga de A | p99 ≤ 200 ms del último byte al resultado de la validación criptográfica, en A |
| Inyección de fallas | ECD caída con información vencida | 0 archivos aceptados sin verificación completa |

**A6. Relaciones:** reemplaza: — · refina: ADR-004 · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-SEQ-004, DG-STM-001 (detalle en §6)

### ADR-006: Adaptador por sistema externo con circuit breaker, caché y pendientes de enriquecimiento

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 1 · **Confianza:** Media — SUP-02 y el ritmo de drenaje tras una caída larga no se han confirmado con BCR

#### Contexto
ASR-D01 exige que la falla o la lentitud de STM, ANTA, MAMC o RNIC no lleguen al acuse ni provoquen rechazos (C1), y BCR plantea una caída de 12 horas con 500 000 declaraciones acumuladas. ASR-I01 exige integrar una versión nueva del contrato de API de un externo con ≤ 4 h-persona. Consultar por registro genera más de 1 300 llamadas por segundo con un masivo, y R-4 deja los externos fuera del control de BCR. Con SUP-06, RNIC se consulta al registrar al actor y no por declaración; STM, ANTA y MAMC sí se consultan por declaración. ADR-001 sacó el enriquecimiento del camino del acuse. ¿Cómo se consulta a los sistemas externos sin que su falla, su lentitud o sus cambios se propaguen al resto de Orquídea?

#### Decisión
Pondremos cada sistema externo detrás de un adaptador propio con una interfaz común de consulta, timeout y circuit breaker. Un orquestador de enriquecimiento pide a cada adaptador lo que la declaración necesita, consulta primero una caché de referencia con vigencia y deduplica las consultas de un mismo archivo. Si el circuito está abierto, la declaración queda pendiente de enriquecimiento en un tópico durable, y un gestor la reanuda con tasa limitada cuando el externo vuelve. Lo hacemos porque contiene la falla en el adaptador (ASR-D01) y deja cada cambio de contrato en un solo lugar (ASR-I01).

#### Consecuencias
- (+) ASR-D01: la falla de un externo se queda en su adaptador; ninguna declaración se rechaza por ella.
- (+) ASR-I01: un contrato de API nuevo se integra cambiando solo su adaptador.
- (−) Aparece el estado «pendiente de enriquecimiento», que el negocio tiene que aceptar y operar.
- (−) La caché puede servir una tasa vieja si STM publica entre refrescos (R-006a).
- (±) El drenaje con tasa limitada evita saturar el repositorio cuando el externo vuelve tras horas; su ritmo es pregunta a BCR.
- (±) ASR-I01 se verifica por juicio de expertos, con el protocolo que declara el ASR (A5).

#### Anexo de trazabilidad

**Y-statement:** En el contexto de cuatro sistemas externos fuera del control de BCR, frente a ASR-D01 y ASR-I01, decidimos un adaptador por externo con circuit breaker, caché y pendientes durables, y descartamos el reintento síncrono y la consulta directa sin adaptador para lograr contener la falla y aislar el cambio de contrato, aceptando el estado pendiente y una caché que puede envejecer.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D01 | Disponibilidad | p99 ≤ 1 s al acuse en C1 | satisface | primario |
| ASR-I01 | Interoperabilidad | ≤ 4 h-persona | satisface | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| INT-01 | Adapter (GoF), también llamado Wrapper | interfaz-común | Wrapper (adaptador, capa anticorrupción) | Integración · localizar | EL-10 | «tactic» {id = INT-01, variante = interfaz-común} |
| DIS-21 | Circuit Breaker | temporizador | Circuit breaker | Disponibilidad · patrón: detección + prevención | EL-10 | «tactic» {id = DIS-21, variante = temporizador} |
| DIS-12 | Retry (+ Idempotent Receiver) | diferido-por-cola | Reintento diferido por cola (pendientes de enriquecimiento) | Disponibilidad · recuperar | EL-09, EL-11 | «tactic» {id = DIS-12, variante = diferido-por-cola} |
| LAT-02 | — | — | Limitar la respuesta a eventos (drenaje con tasa limitada) | Desempeño · controlar la demanda | EL-11 | «tactic» {id = LAT-02} |

**A3. Alternativas**
Drivers: ASR-D01 (Crítica), ASR-I01 (Alta), R-4, R-12 — fijados antes de evaluar.
| Criterio | Opción A: Adaptador + circuit breaker + pendientes (elegida) | Opción B: Reintento síncrono con espera | Opción C: Cliente directo sin adaptador |
|---|---|---|---|
| ASR-D01 sin rechazos | ++ la declaración queda pendiente | − tras agotar reintentos, rechaza o bloquea el hilo | −− la falla llega al flujo |
| ASR-I01 ≤ 4 h-persona | ++ el cambio queda en el adaptador | + si hay adaptador | −− el contrato se riega por el flujo |
| R-4 externos fuera de control | + capa anticorrupción | 0 no cambia | − el modelo del externo entra al dominio |
| R-12 prueba de concepto local | − adaptador, orquestador y gestor que construir | + una política de reintento | ++ una llamada directa |
Descartes: B — con caídas de horas, el reintento síncrono agota los hilos y termina rechazando (ASR-D01). C — el contrato del externo se filtra a la validación y las reglas, y cada cambio toca varias funciones (ASR-I01).

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-006a | Sensibilidad | El tiempo hasta abrir el circuito depende del timeout y del umbral de fallas | BCR define qué latencia cuenta como extrema |
| TO-006a | Trade-off | Caché de referencia: disponibilidad y latencia ▲, frescura del dato ▼ | SUP-02 |
| R-006a | Riesgo | Una tasa vieja en caché valida una declaración con un valor que ya cambió | STM publica sus tasas por evento conocido |
| R-006b | Riesgo | Al volver un externo tras 12 horas, 500 000 reanudaciones saturan el repositorio | El gestor limita la tasa de reanudación |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Cortar y ralentizar cada externo con carga de A | p99 ≤ 1 s del último byte al acuse, en C1 |
| Juicio de expertos | Integrar una versión nueva del contrato de API de ANTA, con el protocolo del ASR | ≤ 4 h-persona de la recepción del contrato a producción |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: ADR-007 · conflicto-con: —

**A7. Evidencia visual:** DG-CMP-001, DG-SEQ-005, DG-STM-001 (detalle en §6)

### ADR-007: Entrega al menos una vez con consumidores idempotentes y outbox transaccional

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Media — patrón conocido, pero el costo del outbox en el pico no se ha medido

#### Contexto
ASR-D03 exige 0 declaraciones con acuse sin resultado o con más de uno cuando un nodo falla durante la carga pico (C2 sobre B). ASR-D02 exige que otro nodo retome en ≤ 10 s lo que el nodo caído tenía en curso. ADR-001 dejó un log con entrega al menos una vez, así que un mismo evento puede llegar dos veces. ¿Cómo se garantiza que cada declaración tenga exactamente un resultado aunque el log entregue repetido y los nodos se caigan?

#### Decisión
Haremos idempotente cada consumidor con la clave (archivo, declaración): el resultado se escribe en el repositorio junto con el evento de salida en una sola transacción local (outbox), y un relevo publica el outbox al log. Los consumidores no guardan estado: su posición vive en el log, y otro nodo retoma desde la última posición confirmada. Lo hacemos porque cumple ASR-D03 sin transacciones distribuidas y deja la recuperación de ASR-D02 en manos del rebalanceo del log.

#### Consecuencias
- (+) ASR-D03: un evento repetido no produce un segundo resultado.
- (+) ASR-D02: sin estado en el nodo, otro consumidor retoma desde la posición confirmada.
- (−) Escrituras extra en el repositorio por el outbox, que hay que purgar sin degradar el pico (pregunta 5 de BCR).
- (−) El tiempo de rebalanceo del log consume parte de los 10 s de ASR-D02.
- (±) La clave de idempotencia obliga a que cada declaración tenga un identificador estable desde la recepción.

#### Anexo de trazabilidad

**Y-statement:** En el contexto del log con entrega al menos una vez de ADR-001, frente a ASR-D03 y ASR-D02, decidimos consumidores idempotentes con outbox transaccional y descartamos las transacciones distribuidas y la entrega como máximo una vez para lograr exactamente un resultado por declaración, aceptando escrituras extra y su purga.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D03 | Disponibilidad | 0 perdidas o duplicadas en C2 sobre B | satisface | primario |
| ASR-D02 | Disponibilidad | ≤ 10 s de la falla a que la última afectada avanza | satisface | primario |
| ASR-E01 | Escalabilidad | p99 ≤ 5 s en B | degrada (−) | afectado |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| DIS-17 | Transaction (ACID) · Two-Phase Commit · Transactional Outbox | outbox | Transacciones (outbox transaccional) | Disponibilidad · prevenir | EL-07, EL-13 | «tactic» {id = DIS-17, variante = outbox} |
| ESC-02 | — | — | Escalamiento horizontal con nodos stateless (consumidores sin estado) | Escalabilidad | EL-05, EL-06 | «tactic» {id = ESC-02} |

**A3. Alternativas**
Drivers: ASR-D03 (Alta), ASR-D02 (Media), ASR-E01 (Alta) — fijados antes de evaluar.
| Criterio | Opción A: Al menos una vez + idempotencia + outbox (elegida) | Opción B: Transacción distribuida entre log y repositorio | Opción C: Entrega como máximo una vez |
|---|---|---|---|
| ASR-D03 exactamente uno | ++ el repetido se descarta | ++ atómico | KO se pierden declaraciones |
| ASR-D02 ≤ 10 s | + rebalanceo del log | − el coordinador también hay que recuperarlo | + sin estado |
| ASR-E01 pico | − escrituras del outbox | −− el commit en dos fases suma latencia | ++ lo más liviano |
Descartes: B — el commit en dos fases suma latencia en el pico (ASR-E01) y un coordinador más que recuperar. C — KO: no cumple ASR-D03.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-007a | Trade-off | Outbox: corrección ▲ (ASR-D03), escrituras en el pico ▼ (ASR-E01) | La purga del outbox corre fuera del camino crítico |
| S-007a | Sensibilidad | El plazo de ASR-D02 depende del tiempo de detección y rebalanceo del log | SUP-08 |
| R-007a | Riesgo | Con el tiempo de sesión por defecto del log, de decenas de segundos, el solo rebalanceo supera los 10 s de ASR-D02 | SUP-08 |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Matar nodos durante la prueba de 45 min de E01 y conciliar | 0 declaraciones con acuse sin resultado o con más de uno |
| Inyección de fallas | Matar un nodo y medir cuándo avanza la última declaración afectada | ≤ 10 s de la falla a que la última declaración afectada avanza |

**A6. Relaciones:** reemplaza: — · refina: ADR-001 · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-SEQ-006, DG-CLS-001 (detalle en §6)

### ADR-008: Reglas y esquemas como datos versionados con vigencia, fijados al recibir

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 1 · **Confianza:** Media — depende de SUP-04: que las reglas probables se expresen sin código nuevo

#### Contexto
ASR-M01 exige que una regla cambiaria nueva sobre datos que ya llegan pase de la especificación a producción con ≤ 4 h-persona, ligada en despliegue. ASR-M02 exige lo mismo para una versión nueva del XSD, que convive con la anterior. R-8 fija que se aplica la regla vigente a la hora de recepción. Hoy reglas y esquemas están compilados en el mismo binario. ¿Dónde viven las reglas y los esquemas para que cambiarlos no obligue a recompilar ni a tocar el resto del flujo?

#### Decisión
Expresaremos las reglas cambiarias como datos en un lenguaje de reglas acotado sobre el modelo canónico, con fecha de vigencia, en un catálogo versionado; los XSD van en un registro de esquemas por versión. Al recibir un archivo se fija la versión de reglas y de esquema que le corresponde, y el núcleo de validación la lee por esa versión. Lo hacemos porque deja el cambio probable de ASR-M01 y ASR-M02 en un dato, no en código, y cumple R-8 por construcción.

#### Consecuencias
- (+) ASR-M01: una regla sobre datos existentes es un registro nuevo en el catálogo, sin compilar.
- (+) ASR-M02 y R-8: cada archivo queda atado a su versión, y dos versiones conviven.
- (−) Una regla que el lenguaje no exprese exige código nuevo y sale de las 4 h.
- (−) El catálogo es una pieza crítica: una regla mal cargada afecta a todos los archivos desde su vigencia (R-008a).
- (±) ASR-M01 se verifica por juicio de expertos, con el protocolo que declara el ASR (A5).

#### Anexo de trazabilidad

**Y-statement:** En el contexto de una Circular que cambia con frecuencia, frente a ASR-M01 y ASR-M02, decidimos reglas y esquemas como datos versionados fijados al recibir y descartamos reglas en código con despliegue rápido y plugins compilados para lograr cambios sin recompilar, aceptando un lenguaje de reglas acotado y un catálogo crítico.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-M01 | Modificabilidad | ≤ 4 h-persona | satisface | primario |
| ASR-M02 | Modificabilidad | ≤ 4 h-persona | satisface | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| MOD-09 | Configuración externalizada (archivo de recursos o almacén de configuración) leída por un configurador que valida y liga · plug-in cuando lo que se elige es una realización | despliegue | Parametrización y binding en configuración | Modificabilidad · diferir binding | EL-08 | «tactic» {id = MOD-09, variante = despliegue} |
| MOD-03 | Interfaz provista explícita que oculta un secreto de diseño (ocultamiento de información) · Facade cuando lo oculto es un subsistema o una librería | api-estable-versionada | Encapsular (motor de reglas detrás de una API versionada) | Modificabilidad · aumentar cohesión | EL-07 | «tactic» {id = MOD-03, variante = api-estable-versionada} |

**A3. Alternativas**
Drivers: ASR-M01 (Crítica), ASR-M02 (Media), R-8, SUP-04 — fijados antes de evaluar.
| Criterio | Opción A: Reglas como datos versionados (elegida) | Opción B: Reglas en código con despliegue rápido | Opción C: Plugins compilados cargados en caliente |
|---|---|---|---|
| ASR-M01 ≤ 4 h-persona | ++ un registro en el catálogo | − código, compilación y despliegue | + un plugin por regla |
| ASR-M02 convivencia de XSD | + registro por versión | − dos versiones en el mismo binario | + plugin por versión |
| R-8 vigencia a la recepción | ++ versión fijada al recibir | − hay que programarla | + si el cargador la respeta |
| SUP-04 regla que el lenguaje no expresa | −− exige código nuevo fuera del catálogo | + cualquier lógica cabe en el código | ++ un plugin nuevo sin tocar el núcleo |
Descartes: B — cada regla vuelve a pasar por compilación y despliegue, que es el costo que hoy se quiere eliminar. C — más poder expresivo, pero cada regla sigue siendo código que probar y desplegar; queda para la regla que el lenguaje no exprese.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-008a | Trade-off | Lenguaje acotado: modificabilidad ▲, poder expresivo ▼ | SUP-04 |
| R-008a | Riesgo | Una regla mal cargada rechaza archivos válidos desde su vigencia | Cada regla pasa pruebas con archivos de referencia antes de activarse |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Juicio de expertos | Escenarios de cambio de regla, con el protocolo del ASR | ≤ 4 h-persona de la especificación aprobada a producción |
| Juicio de expertos | Juicio de expertos (no va en esta encuesta), con inspección de BCR en la Fase 4 | ≤ 4 h-persona del XSD aprobado a producción |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-CMP-003, DG-CLS-001 (detalle en §6)

### ADR-009: Activación de versiones por configuración, con convivencia y reversión

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Media — la convivencia de versiones con archivos en curso no se ha probado

#### Contexto
ASR-D04 exige que, mientras se activa o se revierte una regla, un XSD o un contrato de API, el acuse siga en p99 ≤ 1 s (Ambiente E). ASR-M01 exige que la regla nueva llegue a producción sin detener el flujo. ADR-008 dejó reglas y esquemas como datos versionados fijados al recibir. El caso Knight Capital muestra que un cambio a medias puede producir comportamiento nuevo sin que nadie lo note. ¿Cómo se pone en producción una versión nueva sin detener la recepción y sin que un archivo quede evaluado con dos versiones?

#### Decisión
Activaremos cada versión cambiando la versión vigente en la configuración, sin redesplegar: los archivos recibidos antes de la activación siguen con su versión fijada y los nuevos toman la nueva. Revertir es activar la versión anterior. Los adaptadores nuevos se despliegan junto al anterior y el registro de conectores cambia cuál está activo. Lo hacemos porque el cambio no detiene la recepción (ASR-D04) y la versión fijada por archivo impide mezclarlas.

#### Consecuencias
- (+) ASR-D04: activar o revertir no reinicia la recepción.
- (+) ASR-M01: el último paso de una regla nueva es una activación, no un despliegue.
- (−) Dos versiones conviven en ejecución mientras haya archivos de la anterior en curso.
- (−) Operación: la activación necesita aprobación y auditoría para no repetir Knight Capital.
- (±) Un despliegue de código, cuando haga falta, sigue necesitando una estrategia progresiva aparte.

#### Anexo de trazabilidad

**Y-statement:** En el contexto de reglas y esquemas versionados (ADR-008), frente a ASR-D04 y ASR-M01, decidimos activar versiones por configuración con convivencia y reversión y descartamos el reemplazo azul-verde del despliegue y el reinicio progresivo para lograr cambios sin detener la recepción ni mezclar versiones, aceptando versiones conviviendo en ejecución.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-D04 | Disponibilidad | p99 ≤ 1 s al acuse en E | satisface | primario |
| ASR-M01 | Modificabilidad | ≤ 4 h-persona | contribuye | afectado |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| MOD-12 | — | — | Despliegue consistente y reversible | Modificabilidad · diferir binding | EL-08 | «tactic» {id = MOD-12} |

**A3. Alternativas**
Drivers: ASR-D04 (Alta), ASR-M01 (Crítica), R-8, R-12 — fijados antes de evaluar.
| Criterio | Opción A: Activación por configuración (elegida) | Opción B: Despliegue azul-verde | Opción C: Reinicio progresivo de nodos |
|---|---|---|---|
| ASR-D04 acuse en el cambio | ++ sin reiniciar nada | + conmutación entre entornos | − cada nodo se reinicia y el pool se reduce |
| ASR-M01 sin detener el flujo | ++ la regla entra como dato | − exige desplegar un entorno completo | − exige desplegar |
| R-8 una sola versión por archivo | + versión fijada por archivo | + un entorno por versión | −− nodos con versiones distintas a la vez |
| R-12 prueba de concepto local | − mecanismo de activación que construir | − dos entornos completos | ++ lo que ya trae el orquestador de contenedores |
Descartes: B — sirve para código, pero desplegar un entorno completo por cada regla contradice ADR-008. C — durante el reinicio conviven nodos con versiones distintas y un archivo puede evaluarse con las dos.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-009a | Trade-off | Convivencia de versiones: disponibilidad ▲ (ASR-D04), simplicidad del núcleo ▼ (cada cambio tiene que soportar dos versiones, ASR-M01) | Los archivos en curso de la versión anterior terminan en horas, no en días |
| R-009a | Riesgo | Una activación sin aprobación pone en producción una regla no probada | La activación exige aprobación y queda en la auditoría |
| NR-009a | No-riesgo | Ningún archivo se evalúa con dos versiones | La versión se fija al recibir (ADR-008) |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Activar y revertir una regla, un XSD y un adaptador con tráfico de A | p99 ≤ 1 s del último byte al acuse durante el cambio, en E |

**A6. Relaciones:** reemplaza: — · refina: ADR-008 · depende-de: ADR-008 · conflicto-con: —

**A7. Evidencia visual:** DG-SEQ-007 (detalle en §6)

### ADR-010: Identidad del remitente por certificado y autorización por remitente en el acceso a datos

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Alta — patrón de referencia con autenticación mutua y monitor de referencia

#### Contexto
ASR-S03 exige 0 accesos efectivos a operaciones de otro remitente (Ambiente A). Un IMC reporta operaciones de sus clientes, así que el límite es quién reportó la operación, no el declarante de cada registro. R-10 exige cifrado en tránsito. ¿De dónde sale la identidad del remitente y dónde se aplica el filtro para que ninguna vía de consulta o corrección lo salte?

#### Decisión
Autenticaremos al remitente con TLS mutuo y derivaremos su identidad del certificado de cliente, nunca de un campo del archivo o de la consulta. Toda consulta, corrección y anulación pasa por un autorizador que filtra por el remitente que reportó la operación, en la capa de acceso a datos, y el repositorio queda particionado por remitente. Lo hacemos porque un filtro obligatorio en la capa de datos cubre todas las vías a la vez (ASR-S03).

#### Consecuencias
- (+) ASR-S03: ninguna vía llega a los datos sin pasar por el filtro del remitente.
- (+) R-10: el canal va cifrado con TLS mutuo.
- (−) Las áreas internas de BCR necesitan otro modelo de autorización, por rol (R-010a).
- (−) Operación: gestionar los certificados de cliente de todos los remitentes.

#### Anexo de trazabilidad

**Y-statement:** En el contexto de remitentes que reportan operaciones propias y de sus clientes, frente a ASR-S03, decidimos identidad por certificado con autorización por remitente en la capa de datos y descartamos la identidad tomada del archivo y el filtro en cada pantalla para lograr que ninguna vía salte el filtro, aceptando dos modelos de autorización y la gestión de certificados.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S03 | Seguridad | 0 accesos efectivos a operaciones de otro remitente | satisface | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| SEG-03 | Reference Monitor + Authorizer (PEP/PDP) | acl | Autorizar actores (por remitente propietario) | Seguridad · resistir | EL-14, EL-13 | «tactic» {id = SEG-03, variante = acl} |
| SEG-02 | Authenticator | ligada-al-dispositivo | Autenticar actores (TLS mutuo con certificado de cliente) | Seguridad · resistir | EL-02, EL-14 | «tactic» {id = SEG-02, variante = ligada-al-dispositivo} |

**A3. Alternativas**
Drivers: ASR-S03 (Alta), R-10 — fijados antes de evaluar.
| Criterio | Opción A: Certificado + filtro en la capa de datos (elegida) | Opción B: Identidad tomada del archivo | Opción C: Filtro en cada vía de consulta |
|---|---|---|---|
| ASR-S03 0 accesos | ++ el filtro está en un solo punto obligatorio | KO basta escribir otro código de remitente | − una vía nueva puede olvidar el filtro |
| R-10 cifrado | + TLS mutuo | 0 no cambia | + TLS |
Descartes: B — KO: no cumple ASR-S03. C — el filtro se repite en cada vía y cualquier vía nueva lo puede omitir.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| R-010a | Riesgo | Un rol interno con acceso a todo expone datos de todos los remitentes | BCR define qué ve cada área por su rol |
| NR-010a | No-riesgo | La identidad no se puede falsificar desde el archivo | El certificado de cliente se valida contra la ECD |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Prueba de carga | Catálogo de pruebas de acceso cruzado por todas las vías | 0 accesos efectivos a operaciones de otro remitente |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-SEQ-008, DG-DEP-001 (detalle en §6)

### ADR-011: Auditoría de solo anexar encadenada por hash, con una cadena por partición

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Media — la escritura en orden bajo procesamiento paralelo no se ha probado

#### Contexto
ASR-S04 exige detectar el 100 % de las alteraciones de la auditoría (Ambiente A). R-9 exige conservarla 10 años en almacenamiento que no se puede modificar. BCR pregunta cómo encadenar por hash sin que el procesamiento paralelo convierta el registrador en un cuello de botella. ¿Cómo se registra la auditoría para que cualquier alteración se detecte sin frenar el procesamiento?

#### Decisión
Registraremos la auditoría en un log de solo anexar con una cadena de hash por partición: cada evento lleva el hash del anterior de su partición, y periódicamente se firma un ancla con los últimos hashes de todas las cadenas. La verificación recorre cada cadena y compara las anclas firmadas. Lo hacemos porque una cadena por partición se escribe en paralelo sin un único secuenciador, y cualquier cambio rompe la cadena o el ancla (ASR-S04).

#### Consecuencias
- (+) ASR-S04: modificar, borrar o reordenar un evento rompe su cadena o su ancla.
- (+) La escritura escala con las particiones, sin un registrador único.
- (−) El orden global entre particiones solo existe en las anclas, no evento por evento.
- (−) Almacenamiento extra por hashes y anclas durante 10 años (R-9).

#### Anexo de trazabilidad

**Y-statement:** En el contexto de una auditoría que se escribe en paralelo, frente a ASR-S04, decidimos un log de solo anexar con una cadena de hash por partición y anclas firmadas y descartamos una cadena global y el almacenamiento no modificable por sí solo para lograr detección completa sin un secuenciador único, aceptando orden global solo en las anclas.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-S04 | Seguridad | 100 % de las alteraciones detectadas | satisface | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| SEG-18 | Security Logger and Auditor (Secure Logger + Audit Interceptor) | cadena-hash | Registro de auditoría | Seguridad · detectar | EL-15 | «tactic» {id = SEG-18, variante = cadena-hash} |

**A3. Alternativas**
Drivers: ASR-S04 (Media), R-9 — fijados antes de evaluar.
| Criterio | Opción A: Cadena por partición + anclas (elegida) | Opción B: Cadena global única | Opción C: Solo almacenamiento no modificable |
|---|---|---|---|
| ASR-S04 100 % detectadas | + rompe la cadena o el ancla | ++ orden total | − no detecta lo alterado antes de llegar al almacenamiento no modificable |
| R-9 10 años | + hashes livianos | + hashes livianos | ++ el almacenamiento ya lo exige la norma |
Descartes: B — exige un secuenciador único que serializa todo el procesamiento. C — protege lo que ya está en el almacenamiento no modificable, pero no lo que alguien con acceso cambió durante los 90 días en consulta inmediata.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| S-011a | Sensibilidad | La ventana en que un borrado al final de una cadena pasa inadvertido depende del periodo de las anclas | Las anclas se firman con una clave fuera del alcance del equipo de TI |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Inyección de fallas | Alterar, borrar y reordenar eventos de prueba y correr la verificación | 100 % de las alteraciones del catálogo detectadas |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: — · conflicto-con: —

**A7. Evidencia visual:** DG-SEQ-009, DG-CLS-001 (detalle en §6)

### ADR-012: Modelo canónico publicado como eventos y una proyección por área consumidora

**Estado:** Propuesta · **Fecha:** 2026-11-04 · **Iteración ADD:** 2 · **Confianza:** Alta — patrón conocido; el outbox ya existe por ADR-007

#### Contexto
ASR-I02 exige integrar un área nueva de BCR, que pide los datos en un formato nuevo, con 0 funciones del flujo principal modificadas. Hoy hay un exportador por área dentro del procesamiento. ADR-007 dejó un outbox que publica los resultados al log. ¿Cómo reciben las áreas los datos sin que cada área nueva toque el flujo principal?

#### Decisión
Publicaremos cada declaración aceptada como un evento del modelo canónico versionado, desde el outbox de ADR-007, y cada área tendrá un proyector propio que consume esos eventos y los convierte a su formato. Lo hacemos porque un área nueva es un proyector nuevo, y el flujo principal no se entera (ASR-I02).

#### Consecuencias
- (+) ASR-I02: integrar un área es agregar un consumidor, sin tocar la recepción, la validación ni la persistencia.
- (−) Las áreas ven los datos con un retraso respecto al registro (consistencia eventual).
- (−) El modelo canónico es un contrato con todas las áreas: cambiarlo exige versionarlo.
- (±) ASR-I02 se verifica por juicio de expertos (no va en esta encuesta) y por la inspección de BCR en la Fase 4.

#### Anexo de trazabilidad

**Y-statement:** En el contexto del outbox de ADR-007, frente a ASR-I02, decidimos publicar el modelo canónico como eventos con un proyector por área y descartamos un exportador por área dentro del flujo y una base compartida para lograr integrar áreas sin tocar el flujo principal, aceptando consistencia eventual y un contrato canónico que versionar.

**A1. ASRs atendidos**
| ASR | Atributo | Medida | Efecto esperado | Rol |
|---|---|---|---|---|
| ASR-I02 | Integración | 0 funciones del flujo principal modificadas | satisface | primario |

**A2. Tácticas**
| ID | Patrón | Variante | Táctica (curso) | Familia | Elemento(s) | Estereotipo en diagrama |
|---|---|---|---|---|---|---|
| INT-04 | Documentación de interfaz (Views and Beyond) con Design by Contract y especificación legible por máquina | cdm | Contrato de interfaz explícito (modelo canónico) | Integración · localizar | EL-07, EL-17 | «tactic» {id = INT-04, variante = cdm} |
| MOD-04 | Message Channel (Publish-Subscribe o Point-to-Point) sobre un broker con canal durable (Guaranteed Delivery) | pub-sub | Usar intermediarios (eventos canónicos) | Modificabilidad · reducir acoplamiento | EL-04, EL-17 | «tactic» {id = MOD-04, variante = pub-sub} |

**A3. Alternativas**
Drivers: ASR-I02 (Baja), R-12 — fijados antes de evaluar.
| Criterio | Opción A: Eventos canónicos + proyectores (elegida) | Opción B: Exportador por área en el flujo | Opción C: Base de datos compartida |
|---|---|---|---|
| ASR-I02 0 funciones modificadas | ++ un consumidor nuevo | −− cada área toca el flujo | + el área lee directo |
| R-12 prueba de concepto local | − un proyector por área que construir | ++ el exportador ya existe en el sistema actual | + una vista sobre la base |
Descartes: B — cada área nueva modifica el flujo principal (ASR-I02). C — cumple ASR-I02 hoy, pero ata a todas las áreas al esquema interno y cualquier cambio lo rompe.

**A4. Sensibilidad, trade-offs y riesgos**
| ID | Tipo | Descripción | Supuesto del que depende |
|---|---|---|---|
| TO-012a | Trade-off | Proyecciones asíncronas: modificabilidad ▲, frescura del dato en las áreas ▼ | BCR acepta un retraso para las estadísticas |

**A5. Confirmación**
| Tipo | Qué se prueba | Umbral |
|---|---|---|
| Juicio de expertos | Juicio de expertos (no va en esta encuesta) e inspección de BCR en la Fase 4 | 0 funciones del flujo principal modificadas |

**A6. Relaciones:** reemplaza: — · refina: — · depende-de: ADR-007 · conflicto-con: —

**A7. Evidencia visual:** DG-CMP-004, DG-CLS-001 (detalle en §6)

## 5. Matriz ASR × ADR
| ASR | Prioridad | ADR-001 | ADR-002 | ADR-003 | ADR-004 | ADR-005 | ADR-006 | ADR-007 | ADR-008 | ADR-009 | ADR-010 | ADR-011 | ADR-012 | Hueco |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| ASR-L04 | Crítica | — | S | — | — | — | — | — | — | — | — | — | — | — |
| ASR-D01 | Crítica | C | — | — | — | C | S | — | — | — | — | — | — | — |
| ASR-S06 | Crítica | — | — | — | S | C | — | — | — | — | — | — | — | — |
| ASR-M01 | Crítica | — | — | — | — | — | — | — | S | C | — | — | — | — |
| ASR-S01 | Alta | — | — | — | C | S | — | — | — | — | — | — | — | — |
| ASR-E01 | Alta | C | S | — | — | — | — | D | — | — | — | — | — | — |
| ASR-I01 | Alta | — | — | — | — | — | S | — | — | — | — | — | — | — |
| ASR-D03 | Alta | — | — | — | — | — | — | S | — | — | — | — | — | — |
| ASR-L01 | Alta | S | — | — | C | — | — | — | — | — | — | — | — | — |
| ASR-S03 | Alta | — | — | — | — | — | — | — | — | — | S | — | — | — |
| ASR-D04 | Alta | — | — | — | — | — | — | — | — | S | — | — | — | — |
| ASR-L03 | Media | — | — | S | — | — | — | — | — | — | — | — | — | — |
| ASR-D02 | Media | — | — | — | — | — | — | S | — | — | — | — | — | — |
| ASR-L05 | Media | — | C | S | — | — | — | — | — | — | — | — | — | — |
| ASR-M02 | Media | — | — | — | — | — | — | — | S | — | — | — | — | — |
| ASR-S04 | Media | — | — | — | — | — | — | — | — | — | — | S | — | — |
| ASR-I02 | Baja | — | — | — | — | — | — | — | — | — | — | — | S | — |

## 6. Plan de diagramas
| DG | Tipo | Pregunta que responde | ASR | ADR | Elementos | Conectores | Táctica visible | Precio a anotar | Medida visible | Camino de fallo | Vista R&W |
|---|---|---|---|---|---|---|---|---|---|---|---|
| DG-CMP-002 | componentes | ¿Con quién habla Orquídea y qué intercambia con cada uno? (contexto) | ASR-L01, ASR-D01 | ADR-001 | EL-01, EL-18, EL-19, EL-20, EL-21 | CN-01, CN-19, CN-13, CN-04, CN-21, CN-27 | «tactic» {id = EST-07} | Cuatro sistemas externos y una ECD fuera del control de BCR | — | no aplica | funcional |
| DG-CMP-001 | componentes | ¿Qué etapas tiene Orquídea y qué etapa toca cada sistema externo? | ASR-L01, ASR-E01, ASR-D01 | ADR-001, ADR-006 | EL-01, EL-02, EL-03, EL-04, EL-05, EL-06, EL-07, EL-09, EL-10, EL-12, EL-13, EL-18 | CN-01, CN-02, CN-05, CN-06, CN-07, CN-08, CN-09, CN-11, CN-12, CN-13, CN-16, CN-18 | «tactic» {id = EST-07}, «tactic» {id = ESC-05}, «tactic» {id = INT-01, variante = interfaz-común}, «tactic» {id = DIS-21, variante = temporizador} | Resultado eventual; log que operar (TO-001a) | — | no aplica | funcional |
| DG-SEQ-001 | secuencia | ¿Cómo sale el acuse en menos de un segundo sin esperar la validación ni a los externos? | ASR-L01, ASR-S06, ASR-S01 | ADR-001, ADR-004 | EL-01, EL-02, EL-03, EL-16, EL-04 | CN-01, CN-02, CN-03, CN-05 | «tactic» {id = ESC-05}, «tactic» {id = SEG-02, variante = un-factor}, «tactic» {id = SEG-11}, «tactic» {id = SEG-08} | Acuse de custodia en masivos (TO-004a) | p99 ≤ 1 s al acuse; p99 ≤ 200 ms a la validación | sí — firma inválida: rechazo con la causa | concurrencia |
| DG-CON-001 | objetos-activos | ¿Qué capacidad tiene cada carril y cómo se reparten los bloques entre archivos? | ASR-L04, ASR-E01, ASR-L03, ASR-L05 | ADR-002, ADR-003 | EL-02, EL-04, EL-05, EL-06, EL-07 | CN-05, CN-06, CN-07, CN-08, CN-09 | «tactic» {id = DIS-22}, «tactic» {id = LAT-03}, «tactic» {id = ESC-06}, «tactic» {id = LAT-12} | Capacidad ociosa del carril pesado (TO-002a); consolidación de bloques | pool por carril; bloques de K registros [SUPUESTO] | no aplica | concurrencia |
| DG-SEQ-002 | secuencia | ¿Espera un archivo pequeño detrás de los masivos? | ASR-L04, ASR-E01 | ADR-002 | EL-01, EL-02, EL-04, EL-05, EL-06, EL-07 | CN-01, CN-05, CN-06, CN-07, CN-08 | «tactic» {id = DIS-22}, «tactic» {id = LAT-03} | Capacidad ociosa del carril pesado (TO-002a) | p99 con masivos ≤ 1,10 × p99 sin masivos | no aplica | concurrencia |
| DG-SEQ-003 | secuencia | ¿Cómo avanza un mediano mientras cinco masivos están en proceso? | ASR-L03, ASR-L05 | ADR-003 | EL-04, EL-06, EL-07, EL-13 | CN-07, CN-09, CN-16 | «tactic» {id = ESC-06}, «tactic» {id = LAT-12} | Bloques en desorden y registro tóxico (R-003a) | ≤ 5 min por masivo; p99 ≤ 30 s el mediano | no aplica | concurrencia |
| DG-SEQ-004 | secuencia | ¿Qué pasa con un archivo si la ECD está caída y la información de revocación venció? | ASR-S01, ASR-S06, ASR-D01 | ADR-005 | EL-01, EL-02, EL-16, EL-19, EL-12 | CN-01, CN-03, CN-04, CN-19 | «tactic» {id = LAT-10}, «tactic» {id = DIS-14, variante = fallback} | Ventana de revocación (R-005a) | p99 ≤ 200 ms a la validación; 0 aceptados sin verificar | sí — ECD caída: verificación pendiente | concurrencia |
| DG-SEQ-005 | secuencia | ¿Qué pasa con una declaración si un sistema externo se cae, y cómo se drena al volver? | ASR-D01, ASR-I01 | ADR-006 | EL-07, EL-09, EL-10, EL-18, EL-04, EL-11 | CN-11, CN-12, CN-13, CN-14, CN-26, CN-15 | «tactic» {id = INT-01, variante = interfaz-común}, «tactic» {id = DIS-21, variante = temporizador}, «tactic» {id = DIS-12, variante = diferido-por-cola}, «tactic» {id = LAT-02} | Estado pendiente; drenaje con tasa limitada (R-006b) | p99 ≤ 1 s al acuse en C1 | sí — circuito abierto: pendiente de enriquecimiento | concurrencia |
| DG-STM-001 | estados | ¿Por qué estados pasa un archivo desde que llega hasta su resultado definitivo? | ASR-S06, ASR-D01 | ADR-004, ADR-005, ADR-006 | EL-02, EL-07, EL-12 | CN-05, CN-18 | «tactic» {id = DIS-14, variante = fallback}, «tactic» {id = DIS-12, variante = diferido-por-cola}, «tactic» {id = SEG-11} | Archivos retenidos en verificación pendiente | 0 aceptados sin verificación completa | sí — verificación fallida y externo caído | información |
| DG-SEQ-006 | secuencia | ¿Cómo se retoma lo que tenía un nodo caído sin perder ni duplicar declaraciones? | ASR-D03, ASR-D02 | ADR-007 | EL-04, EL-05, EL-07, EL-13 | CN-06, CN-08, CN-16, CN-17 | «tactic» {id = DIS-17, variante = outbox}, «tactic» {id = ESC-02} | Escrituras del outbox en el pico (TO-007a) | ≤ 10 s a que avanza la última afectada; 0 perdidas o duplicadas | sí — nodo caído a mitad de un bloque | concurrencia |
| DG-CLS-001 | clases | ¿Qué datos guarda Orquídea y cómo se atan a su versión, su resultado y su auditoría? | ASR-D03, ASR-M01, ASR-S04, ASR-I02 | ADR-007, ADR-008, ADR-011, ADR-012 | EL-07, EL-08, EL-13, EL-15, EL-17 | CN-10, CN-16, CN-17 | «tactic» {id = DIS-17, variante = outbox}, «tactic» {id = MOD-09, variante = despliegue}, «tactic» {id = SEG-18, variante = cadena-hash}, «tactic» {id = INT-04, variante = cdm} | Retención de 10 años (R-9) | clave de idempotencia (archivo, declaración) | no aplica | información |
| DG-CMP-003 | componentes | ¿Dónde viven las reglas y los esquemas, y qué toca un cambio de regla? | ASR-M01, ASR-M02 | ADR-008 | EL-07, EL-08, EL-21 | CN-10, CN-27 | «tactic» {id = MOD-09, variante = despliegue}, «tactic» {id = MOD-03, variante = api-estable-versionada} | Lenguaje de reglas acotado (TO-008a) | ≤ 4 h-persona | no aplica | funcional |
| DG-SEQ-007 | secuencia | ¿Cómo entra una regla nueva sin detener la recepción ni mezclar versiones? | ASR-D04, ASR-M01 | ADR-009 | EL-21, EL-08, EL-01, EL-02, EL-07 | CN-27, CN-01, CN-10 | «tactic» {id = MOD-12} | Versiones conviviendo en ejecución | p99 ≤ 1 s al acuse durante el cambio | sí — reversión de una regla defectuosa | concurrencia |
| DG-SEQ-008 | secuencia | ¿Puede un remitente ver o corregir una operación que reportó otro? | ASR-S03 | ADR-010 | EL-01, EL-14, EL-13, EL-15 | CN-23, CN-22 | «tactic» {id = SEG-03, variante = acl}, «tactic» {id = SEG-02, variante = ligada-al-dispositivo} | Dos modelos de autorización (R-010a) | 0 accesos efectivos a operaciones ajenas | sí — acceso cruzado negado y auditado | funcional |
| DG-SEQ-009 | secuencia | ¿Cómo se detecta que alguien alteró la auditoría? | ASR-S04 | ADR-011 | EL-02, EL-12, EL-15 | CN-24, CN-25 | «tactic» {id = SEG-18, variante = cadena-hash} | Orden global solo en las anclas | 100 % de las alteraciones detectadas | sí — registro alterado: cadena rota | información |
| DG-CMP-004 | componentes | ¿Cómo recibe un área nueva los datos sin tocar el flujo principal? | ASR-I02 | ADR-012 | EL-13, EL-04, EL-17, EL-20 | CN-17, CN-20, CN-21 | «tactic» {id = INT-04, variante = cdm}, «tactic» {id = MOD-04, variante = pub-sub} | Datos con retraso en las áreas (TO-012a) | 0 funciones del flujo principal modificadas | no aplica | funcional |
| DG-DEP-001 | despliegue | ¿Dónde corre cada etapa, qué carril se despliega aparte y por dónde entra el remitente? | ASR-L01, ASR-L04, ASR-S03 | ADR-001, ADR-010 | EL-01, EL-02, EL-04, EL-05, EL-06, EL-09, EL-10, EL-13, EL-18 | CN-01, CN-05, CN-06, CN-07, CN-23 | «tactic» {id = EST-07}, «tactic» {id = SEG-02, variante = ligada-al-dispositivo} | Dos carriles que dimensionar; certificados de cliente | dos carriles con despliegue propio | no aplica | despliegue |

## 7. Abierto
**Preguntas:** ¿Qué pasa con el sexto archivo masivo cuando ya hay cinco en proceso (R-002a)? ¿A qué ritmo deben drenarse los pendientes cuando un sistema externo vuelve tras horas (R-006b)? ¿Cada cuánto publica la ECD su lista de revocación, y en cuánto tiempo hay que detectar un archivo aceptado dentro de la ventana (R-005a)? ¿S01 aplica también a los masivos que reciben acuse de custodia? ¿Qué ve cada área de BCR según su rol (R-010a)?
**Supuestos:** SUP-01 verificar 50 MB en streaming cabe en 1 s. SUP-02 los externos no aceptan lotes y las tasas cambian por publicación. SUP-03 la ECD publica su lista al menos una vez al día. SUP-04 las reglas probables se expresan sin código nuevo. SUP-05 bloques de 1 000 registros. SUP-06 la identidad se valida al registrar al actor. SUP-07 el tamaño en bytes predice el número de registros. SUP-08 la sesión del consumidor del log se configura en pocos segundos.
**Riesgos abiertos:** R-002a saturación con más de 5 masivos. R-007a rebalanceo del log más lento que los 10 s de ASR-D02. Un solo nodo de base de datos es punto único de falla; su alta disponibilidad es infraestructura y no tiene ADR. R-004a firma envuelta si el parser construye el árbol completo. R-005a ventana de revocación. R-006b estallido al drenar 500 000 pendientes.
**Fuera de alcance:** Disponibilidad física — la topología activo-activo entre zonas que aprobó BCR es infraestructura y no tiene ADR de software. Funcional — la notificación por webhooks y la consulta de resultados no las mueve ningún ASR.
