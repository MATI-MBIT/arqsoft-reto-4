---
title: Vistas de arquitectura
nav_order: 8
has_children: true
---

# Vistas de arquitectura de Orquídea, guiadas por los cuatro ASR críticos

Esta sección reúne los diagramas que hacen visibles las decisiones de arquitectura de Orquídea. Las decisiones están en los [ADR](adrs-orquidea.md) y los requisitos, en la [página de ASR](../quality-attributes.md).

Cada vista es un archivo de draw.io con un máximo de tres páginas, y cada página profundiza la anterior. El diseño sigue el orden de importancia de los ASR críticos: primero el aislamiento de carga (ASR-L04), luego el aislamiento de fallas de los sistemas externos (ASR-D01), después la validación criptográfica completa (ASR-S06) y al final la regla cambiaria en 4 h-persona (ASR-M01).

## Las seis vistas

| Vista | Archivo | Páginas | Qué responde |
|---|---|---|---|
| Contexto | [`vista-contexto.drawio`](drawio/vista-contexto.drawio) | DG-CMP-001 | Con quién habla Orquídea y qué intercambia con cada uno |
| Funcional | [`vista-funcional.drawio`](drawio/vista-funcional.drawio) | DG-CMP-002 · DG-CST-001 · DG-CST-002 | Qué componentes tiene Orquídea y cómo están construidos por dentro los que sostienen L04, S06 y D01 |
| Dinámica y de concurrencia | [`vista-dinamica-concurrencia.drawio`](drawio/vista-dinamica-concurrencia.drawio) | DG-CON-001 · DG-SEQ-001 · DG-SEQ-002 | Qué corre en paralelo, qué se sincroniza y cómo se recorre cada componente clave paso a paso |
| Información | [`vista-informacion.drawio`](drawio/vista-informacion.drawio) | DG-CLS-001 · DG-STM-001 · DG-STM-002 | Qué guarda Orquídea por archivo y por qué estados pasan el archivo y el circuito de cada sistema externo |
| Despliegue y topología de red | [`vista-despliegue.drawio`](drawio/vista-despliegue.drawio) | DG-DEP-001 | Dónde corre cada etapa, con cuántas réplicas y por qué protocolo |
| Desarrollo | [`vista-desarrollo.drawio`](drawio/vista-desarrollo.drawio) | DG-CST-003 · DG-SEQ-003 · DG-CLS-002 | Dónde vive una regla cambiaria, cómo se pone en producción y cómo se revierte |

Las cuatro primeras y la de despliegue son las que BCR pidió en su respuesta, más la vista funcional. La de desarrollo reúne lo que demuestra ASR-M01: una regla nueva es un dato, no código.

Los componentes clave se abren de caja negra a caja blanca, y su comportamiento se dibuja como secuencia interna. Recepción y verificación aloja la clasificación por tamaño (L04) y la verificación criptográfica (S06). Adaptadores de sistemas externos aloja el adaptador y el circuit breaker (D01). Configuración versionada aloja el catálogo de reglas y la activación (M01).

| Componente clave | Caja blanca (N1) | Secuencia interna (N2) | Por qué es clave |
|---|---|---|---|
| Recepción y verificación | DG-CST-001 | DG-SEQ-001 | Aloja las tácticas de L04 (LAT-03) y de S06 (SEG-02, SEG-08, SEG-11), y está en el camino del acuse de D01 |
| Adaptadores de sistemas externos | DG-CST-002 | DG-SEQ-002 | Aloja las tácticas de D01 (INT-01, DIS-21) |
| Configuración versionada | DG-CST-003 | DG-SEQ-003 | Aloja las tácticas de M01 (MOD-09, MOD-12) |

Los carriles liviano y pesado quedan como caja negra. Su táctica, el bulkhead, se demuestra con los dos pools de DG-CON-001 y los dos nodos de DG-DEP-001, no con su estructura interna.

## Dónde se ve cada táctica de los ASR críticos

| ASR | Táctica | ADR | Diagrama principal | Qué hay que buscar | También en |
|---|---|---|---|---|---|
| L04 | DIS-22 Bulkhead | ADR-002 | DG-CON-001 | Dos pools `[1..*]` que no comparten hilos ni tópico | DG-DEP-001 (un nodo por carril), DG-CMP-002 |
| L04 | LAT-03 Clasificar al recibir | ADR-002 | DG-CST-001 | La parte `clasificador` que elige el carril por el tamaño en bytes | DG-SEQ-001, DG-CON-001 |
| D01 | DIS-21 Circuit breaker | ADR-006 | DG-STM-002 | Cerrado, Abierto y Semiabierto con `after(espera)` | DG-CST-002, DG-SEQ-002 (`alt` Cerrado / Abierto) |
| D01 | INT-01 Adaptador | ADR-006 | DG-CST-002 | `target`, `adapter` y `supplier`, con el adaptado ligado por la colaboración | DG-SEQ-002 |
| D01 | DIS-12 Reintento diferido por cola | ADR-006 | DG-STM-001 | EsperandoExterno y Apartado: la falla de un externo nunca lleva a Rechazado | DG-CON-001 (`retryQueue`, `deadLetterChannel`), DG-CLS-001 |
| D01 | LAT-02 Limitar la respuesta | ADR-006 | DG-CON-001 | El `limitador : TokenBucket {guarded}` del gestor de pendientes | DG-STM-001 (reanudar con tasa limitada) |
| D01 | ESC-05 Asincronía y colas | ADR-001 | DG-SEQ-001 | El acuse sale sin esperar la validación ni a ningún externo | DG-CMP-002 (el log como bus) |
| S06 | SEG-02 Autenticar (firma) | ADR-004 | DG-CST-001 | `authenticator`, `proofOfIdentity` y la información de revocación por el puerto | DG-SEQ-001, DG-STM-001 |
| S06 | SEG-11 Verificar integridad | ADR-004 | DG-CST-001 | El `custodio` con el hash de los bytes que se custodian y se procesan | DG-SEQ-001, DG-CLS-001 (`hashSHA256`) |
| S06 | SEG-08 Validar la entrada | ADR-004 | DG-CST-001 | El `receptor` sin DTD ni entidades externas | — |
| S06 | DIS-14 Degradación (verificación pendiente) | ADR-005 | DG-STM-001 | ResultadoRetenido: a Aceptado solo se llega con la verificación completa y válida | DG-SEQ-001 (`alt` ECD caída), DG-CLS-001, DG-CMP-002 |
| M01 | MOD-09 Reglas como datos | ADR-008 | DG-CLS-002 | Una regla nueva es una instancia de `ReglaCambiaria`, no una subclase | DG-CST-003 (`configurator`, `configuration`) |
| M01 | MOD-12 Activación reversible | ADR-009 | DG-SEQ-003 | `critical` de la activación y `opt` de la reversión: el mismo paso | DG-CST-003 (`conmutador`) |
| M01 | MOD-03 API estable versionada | ADR-008 | DG-CLS-002 | `IValidacion {v = 1}` realizada por el motor de reglas | DG-CMP-002 |

## Qué muestra cada diagrama

**DG-CMP-001 · Contexto.** Muestra a Orquídea como caja negra frente al remitente, el Equipo de TI, los cuatro sistemas externos, la ECD y las áreas consumidoras. Refleja ADR-001: el acuse no espera a ningún sistema que BCR no controle. No muestra componentes internos.

**DG-CMP-002 · Arquitectura general (N0).** Muestra los componentes en cuatro paquetes, el log distribuido como bus con sus tópicos y el protocolo hacia cada externo, con la marca de la táctica principal de cada componente. Refleja ADR-001, 002, 004, 005, 006 y 008. Para caber en una página, fusiona el autorizador y los proyectores con Resultado y la auditoría con el repositorio. Sus tácticas (SEG-03, MOD-04 y SEG-18) no se dibujan aquí.

**DG-CST-001 · Recepción y verificación (N1).** Muestra los puertos, la delegación y cinco partes: receptor, custodio, authenticator, proofOfIdentity y clasificador. Refleja ADR-002 y ADR-004: se clasifica sin abrir el archivo, y se verifica sobre los mismos bytes que se custodian. No muestra el verificador de revocación por dentro, que se ve solo por su puerto.

**DG-CST-002 · Adaptadores de sistemas externos (N1).** Muestra el circuit breaker, su estado por externo y el adaptador que traduce al contrato de cada externo, con el cliente con timeout. Refleja ADR-006: la falla se queda en el adaptador y un contrato nuevo cambia una sola parte. No muestra los cuatro adaptadores por separado: `AdaptadorPorExterno` tiene una instancia por externo.

**DG-CON-001 · Carriles y pendientes.** Muestra los dos pools de hilos del bulkhead, el tópico de pendientes con su gestor y su limitador, y lo que se sincroniza en el repositorio. Refleja ADR-002 y ADR-006: un masivo no quita hilos a un pequeño, y un externo caído no bloquea ningún hilo. No muestra el planificador por turnos del carril pesado (ADR-003, que no atiende un ASR crítico).

**DG-SEQ-001 · Recepción y verificación (N2).** Muestra la recepción con tres caminos: firma válida, ECD caída con la información vencida y firma o certificado inválidos. Refleja ADR-004 y ADR-005, y anota la medida del acuse (p99 ≤ 1 s). No muestra lo que pasa después del acuse.

**DG-SEQ-002 · Adaptadores de sistemas externos (N2).** Muestra una consulta con el circuito cerrado (respuesta o timeout) y con el circuito abierto (falla inmediata). Refleja ADR-006. No muestra el drenaje de pendientes, que está en DG-CON-001.

**DG-CLS-001 · Modelo de información.** Muestra el archivo con su verificación criptográfica, su versión fijada, sus declaraciones, sus pendientes y su resultado definitivo. Refleja ADR-004, 006 y 008, con la restricción que impide una aceptación sin verificación. No muestra el modelo canónico ni la auditoría.

**DG-STM-001 · Ciclo de vida del archivo.** Muestra los estados del archivo del último byte al resultado definitivo. Refleja ADR-004, 005 y 006: a Aceptado solo se llega con la verificación completa, y la falla de un externo lleva a esperar, nunca a rechazar. No muestra los estados de cada declaración por separado.

**DG-STM-002 · Circuito por sistema externo.** Muestra Cerrado, Abierto y Semiabierto, y los eventos que avisan al gestor de pendientes. Refleja ADR-006. No fija el umbral ni la espera, que siguen en `TBD`.

**DG-DEP-001 · Despliegue y topología de red.** Muestra los nodos con sus réplicas: dos de recepción, un nodo por carril, dos de enriquecimiento, tres del log y uno de datos, con el protocolo de cada ruta. Refleja ADR-001, 002, 005, 006 y 010. Que el enriquecimiento tenga nodo propio es una propuesta, no una decisión registrada.

**DG-CST-003 · Configuración versionada (N1).** Muestra el validador que liga la regla, el banco de pruebas, el conmutador de versión, el catálogo, el registro de esquemas y el fijador de versión. Refleja ADR-008 y ADR-009. No muestra cómo se escribe el lenguaje de reglas.

**DG-SEQ-003 · Configuración versionada (N2).** Muestra cómo se carga, se prueba, se activa y se revierte una regla, y cómo el núcleo pide la versión por la hora de recepción. Refleja ADR-008 y ADR-009, y anota la medida (≤ 4 h-persona). No muestra la aprobación regulatoria, que no cuenta en las 4 h.

**DG-CLS-002 · Reglas como datos.** Muestra el catálogo con sus versiones y sus reglas, el motor detrás de `IValidacion {v = 1}` y la versión fijada. Refleja ADR-008. No muestra los XSD.

## ASR, ADR y diagramas

| ASR | ADR | Diagramas | Hueco |
|---|---|---|---|
| ASR-L04 | ADR-002 | DG-CON-001, DG-CST-001, DG-SEQ-001, DG-DEP-001, DG-CMP-002 | El planificador del carril pesado (ADR-003) no se dibuja |
| ASR-D01 | ADR-001, ADR-005, ADR-006 | DG-CST-002, DG-SEQ-002, DG-STM-002, DG-CON-001, DG-STM-001, DG-SEQ-001 | Ritmo de drenaje y `maxAttempts` sin cifra (`TBD`) |
| ASR-S06 | ADR-004, ADR-005 | DG-CST-001, DG-SEQ-001, DG-STM-001, DG-CLS-001 | El verificador de revocación queda como caja negra |
| ASR-M01 | ADR-008, ADR-009 | DG-CST-003, DG-SEQ-003, DG-CLS-002 | El motor de reglas (MOD-03) queda como caja negra |

## Lo que estos diagramas todavía no muestran

- Los ASR no críticos que no tienen diagrama propio: S03 (autorización por remitente), S04 (auditoría encadenada), I02 (proyectores por área), D02 y D03 (outbox y retoma de un nodo caído) y L03 y L05 (bloques y planificación del carril pesado).
- Los parámetros que BCR no ha fijado, marcados `TBD`: umbral y espera del circuit breaker, `T_call`, `maxAttempts`, tasa de drenaje y tamaño de cada pool y de cada carril.
- La alta disponibilidad del nodo de datos: es un riesgo de infraestructura sin ADR.

## Diagramas afectados, para las consecuencias de cada ADR

- ADR-001: Diagramas afectados: DG-CMP-001, DG-CMP-002, DG-SEQ-001, DG-DEP-001
- ADR-002: Diagramas afectados: DG-CMP-002, DG-CST-001, DG-CON-001, DG-SEQ-001, DG-DEP-001
- ADR-004: Diagramas afectados: DG-CST-001, DG-SEQ-001, DG-CLS-001, DG-STM-001
- ADR-005: Diagramas afectados: DG-CMP-002, DG-SEQ-001, DG-STM-001
- ADR-006: Diagramas afectados: DG-CMP-002, DG-CST-002, DG-SEQ-002, DG-CON-001, DG-STM-001, DG-STM-002, DG-CLS-001, DG-DEP-001
- ADR-008: Diagramas afectados: DG-CMP-002, DG-CST-003, DG-SEQ-003, DG-CLS-001, DG-CLS-002
- ADR-009: Diagramas afectados: DG-CST-003, DG-SEQ-003
