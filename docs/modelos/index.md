---
title: Vistas de arquitectura
nav_order: 8
has_children: true
---

# Vistas de arquitectura de Orquídea

Los diagramas hacen visibles las decisiones de arquitectura. Cada táctica de un [ADR](adrs-orquidea.md) es un cambio en algún diagrama: una parte con su marca `«tactic» {id = …}`, una réplica, un fragmento de secuencia, un estado o una clase. Su precio va en una nota junto al elemento. Todos los diagramas están en un solo archivo de draw.io, [`orquidea.drawio`](drawio/orquidea.drawio), con una página por diagrama.

| Vista | Qué responde |
|---|---|
| [Vista de contexto](vista-contexto.md) | Con quién habla Orquídea y qué intercambia con cada uno. |
| [Vista funcional](vista-funcional.md) | Qué componentes tiene Orquídea, dónde vive cada táctica y cómo funciona por dentro cada componente clave. |
| [Vista dinámica y de concurrencia](vista-concurrencia.md) | Qué corre en paralelo y qué se sincroniza, y las secuencias que demuestran cada ASR con su camino de fallo. |
| [Vista de información](vista-informacion.md) | Qué datos guarda Orquídea, cómo se atan a su versión, su resultado y su auditoría, y por qué estados pasan. |
| [Vista de despliegue](vista-despliegue.md) | Dónde corre cada etapa, cuántas réplicas tiene y por qué protocolo se comunica. |

## Dónde se ve cada táctica

Una táctica no se ve igual en todos los tipos de diagrama. Un patrón de diseño, como el adaptador o el modelo canónico, se lee en un diagrama de clases. Un mecanismo con memoria, como el circuit breaker o la cadena de auditoría, se lee en una máquina de estados. Un mecanismo que depende del orden de los mensajes, como el acuse temprano o el reintento diferido, se lee en una secuencia. El reparto de hilos y colas se lee en la vista de concurrencia, y las réplicas, en el despliegue.

La tabla dice, por táctica, en qué diagrama se demuestra mejor (**primario**) y en cuáles más aparece. La columna «Qué se ve» nombra el elemento que hay que buscar en el diagrama primario.

| Táctica | ADR | Primario | Qué se ve | También en |
|---|---|---|---|---|
| EST-07 Pipe and Filter | ADR-001 | DG-CMP-001 (componentes) | Etapas encadenadas por el log, cada una consume a su ritmo | DG-CMP-002, DG-DEP-001 |
| ESC-05 Asincronía y colas | ADR-001 | DG-SEQ-001 (secuencia) | La publicación asíncrona al log antes del acuse; nada de la validación en ese camino | DG-CMP-001 (el bus), DG-CON-001 (tópicos «queue») |
| DIS-22 Bulkhead | ADR-002 | DG-CON-001 (concurrencia) | Dos procesos con su propio pool, que no comparten hilos | DG-DEP-001 (un nodo por carril), DG-SEQ-002 (`par`), DG-CST-002 |
| LAT-03 Clasificar al recibir | ADR-002 | DG-CST-001 (caja blanca) | La parte `clasificador` que elige el tópico por el tamaño en bytes | DG-CON-001, DG-SEQ-002 |
| ESC-06 Dividir y paralelizar | ADR-003 | DG-CST-002 (caja blanca) | El `divisor` en bloques de K registros y el `agregador` que cuenta | DG-SEQ-102, DG-CON-001 (`workerBloque [1..*]`), DG-SEQ-003 |
| LAT-12 Planificar por turnos | ADR-003 | DG-SEQ-003 (secuencia) | El `loop` que da un bloque a cada archivo por ronda | DG-CST-002 (`planificador`), DG-CON-001 |
| SEG-02 Autenticar (firma) | ADR-004 | DG-CST-001 (caja blanca) | El `authenticator` con su información de autenticación y su prueba de identidad | DG-SEQ-101, DG-STM-001 (la guarda) |
| SEG-11 Verificar integridad | ADR-004 | DG-SEQ-101 (secuencia) | El hash de los bytes custodiados entra a la verificación | DG-CST-001, DG-CLS-001 (`Archivo.hashSHA256`) |
| SEG-08 Validar la entrada | ADR-004 | DG-CST-001 (caja blanca) | El `receptor` con `{sin DTD ni entidades externas}` antes de toda lógica | — |
| LAT-10 Copia local | ADR-005 | DG-SEQ-110 (secuencia) | El `alt` copia vigente / vencida, con la ventana de revocación anotada | DG-CST-010, DG-CMP-001 |
| DIS-14 Degradación (fallback) | ADR-005 | DG-STM-001 (estados) | El estado `VerificacionPendiente`, que nunca lleva a Aceptado sin verificar | DG-CST-010, DG-SEQ-110, DG-SEQ-004 |
| INT-01 Adaptador | ADR-006 | DG-CLS-002 (clases) | `IConsultaExterna`, la clase abstracta protegida y una subclase por externo | DG-CST-003, DG-SEQ-010, DG-CMP-001 |
| DIS-21 Circuit breaker | ADR-006 | DG-STM-002 (estados) | Cerrado, Abierto y Semiabierto, con `after(espera)` | DG-CST-003, DG-SEQ-103, DG-CLS-002, DG-SEQ-005 |
| DIS-12 Reintento diferido por cola | ADR-006 | DG-STM-003 (estados) | Pendiente → reanudar, y Apartada tras `maxAttempts` | DG-CST-008, DG-SEQ-108, DG-CON-002, DG-SEQ-005 |
| LAT-02 Limitar la respuesta | ADR-006 | DG-CON-002 (concurrencia) | El `limitador : TokenBucket {guarded}` que da una ficha por reanudación | DG-SEQ-005 (`loop` de drenaje), DG-STM-003 |
| DIS-17 Outbox transaccional | ADR-007 | DG-SEQ-106 (secuencia) | El fragmento `critical` con clave, resultado y evento en una sola transacción | DG-CLS-001 (`EntradaOutbox`), DG-CST-006, DG-STM-004, DG-CON-002, DG-SEQ-006 |
| ESC-02 Nodos sin estado | ADR-007 | DG-SEQ-006 (secuencia) | `nodoB` retoma la partición de `nodoA` sin estado propio | DG-DEP-001 (réplicas), DG-CON-001 (`worker [1..*]`) |
| MOD-09 Binding en configuración | ADR-008 | DG-CLS-004 (clases) | `CatalogoDeReglas` con `{binding = despliegue}` y las reglas como instancias | DG-CMP-003, DG-CST-004, DG-CLS-001 (`VersionFijada`) |
| MOD-03 API estable versionada | ADR-008 | DG-CLS-004 (clases) | `IValidacion {v = 1}` realizada por el motor de reglas | DG-CMP-003, DG-CST-005 |
| MOD-12 Despliegue reversible | ADR-009 | DG-ACT-001 (actividad) | Probar, activar y revertir como el mismo paso sobre la configuración | DG-SEQ-007, DG-SEQ-010, DG-CST-004 |
| SEG-03 Autorizar por remitente | ADR-010 | DG-CST-007 (caja blanca) | `pep`, `pdp` y el recurso `«guarded»` filtrado por remitente en el acceso a datos | DG-SEQ-107, DG-SEQ-008 |
| SEG-02 Autenticar (TLS mutuo) | ADR-010 | DG-DEP-001 (despliegue) | La ruta `«HTTPS» 443 · TLS mutuo` de entrada | DG-CST-007 (`deviceBinding`) |
| SEG-18 Auditoría encadenada | ADR-011 | DG-STM-005 (estados) | La cadena Abierta → Anclada → Verificada o Rota | DG-CLS-001 (`hashAnterior`), DG-CST-009, DG-SEQ-109, DG-SEQ-009 |
| INT-04 Modelo canónico | ADR-012 | DG-CLS-003 (clases) | `DeclaracionCanonica` con su contrato, su semántica y sus distancias | DG-CST-005 (`messageTranslator`), DG-CMP-001 |
| MOD-04 Publicar y suscribir | ADR-012 | DG-CMP-004 (componentes) | El evento `«event» declaracion.aceptada v1` y un proyector nuevo que solo se suscribe | DG-CST-011, DG-CLS-003, DG-STM-004 |

## Lo que estos diagramas todavía no muestran

- La caja blanca del Carril liviano, del Gestor de pendientes y de Resultado y notificación. Sus tácticas se ven en la concurrencia y en las secuencias.
- Los umbrales que BCR no ha fijado (`TBD` en los diagramas): umbral y espera del circuit breaker, `maxAttempts`, la tasa de drenaje y el tamaño de cada pool.
- La alta disponibilidad de la base de datos: el nodo de datos único es un riesgo de infraestructura sin ADR.
