---
title: Vistas de arquitectura
nav_order: 8
has_children: true
---

# Vistas de arquitectura de Orquídea, centradas en los tres ASR críticos

Esta sección reúne los diagramas que hacen visibles las decisiones de arquitectura de Orquídea. Las decisiones están en los [ocho ADR](adrs-orquidea.md), agrupados por ASR, y los requisitos, en la [página de ASR](../quality-attributes.md).

Los diagramas se centran en los tres ASR críticos: ningún archivo se rechaza por la caída de un sistema externo (ASR-D01, el foco), el aislamiento de carga entre archivos masivos y pequeños (ASR-L04) y ningún archivo aceptado sin validación criptográfica completa (ASR-S06).

Cada vista tiene su página, donde los diagramas se dibujan en el navegador, y su archivo de draw.io. El primer diagrama de cada vista es el global, y los demás, hasta tres, detallan lo que el global no alcanza a mostrar.

## Las cinco vistas

| Vista | Diagramas | Global | Detalle | Qué responde |
|---|---|---|---|---|
| [Contexto](vista-contexto.md) | [Ver](vista-contexto.md) · [`.drawio`](drawio/vista-contexto.drawio) | DG-CMP-001 | — | Quién envía, quién recibe y de qué terceros depende Orquídea |
| [Componentes](vista-componentes.md) | [Ver](vista-componentes.md) · [`.drawio`](drawio/vista-componentes.drawio) | DG-CMP-003 | DG-CST-001 · DG-CST-002 · DG-CST-003 | Qué componentes tiene Orquídea, y cómo están construidos por dentro la recepción, el verificador criptográfico y el reprocesador nocturno |
| [Concurrencia](vista-concurrencia.md) | [Ver](vista-concurrencia.md) · [`.drawio`](drawio/vista-concurrencia.drawio) | DG-CON-001 | DG-SEQ-001 · DG-SEQ-002 · DG-SEQ-003 | Qué corre en paralelo, qué se sincroniza y cómo recorre cada componente clave su operación crítica, con su camino de fallo |
| [Información](vista-informacion.md) | [Ver](vista-informacion.md) · [`.drawio`](drawio/vista-informacion.drawio) | DG-CLS-001 | DG-STM-001 · DG-STM-002 · DG-STM-003 | Qué guarda Orquídea por archivo, y por qué estados pasan el archivo, la declaración pendiente y cada tercero en el monitor |
| [Despliegue](vista-despliegue.md) | [Ver](vista-despliegue.md) · [`.drawio`](drawio/vista-despliegue.drawio) | DG-DEP-003 | DG-DEP-004 | Dónde corre cada parte en producción, y cómo se monta el experimento local en Docker |

## Componentes clave

Tres componentes se abren de caja negra a caja blanca, porque están en el camino crítico de los ASR. Su comportamiento se dibuja como secuencia interna en la vista de concurrencia.

| Componente | Caja blanca | Secuencia interna | Por qué es clave |
|---|---|---|---|
| Recepción | DG-CST-001 | DG-SEQ-001 | Acusa sin esperar a terceros, registra la llave única del archivo, anexa la evidencia y enruta por tamaño: D01, L04 y S06 |
| Integración con terceros | DG-CST-002 | DG-SEQ-002 | Es donde la caída o la lentitud de un tercero se vuelve pendiente y no rechazo, con el timeout y la degradación con gracia: D01 |
| Verificador criptográfico | DG-CST-003 | DG-SEQ-003 | Verifica firma, firmante, cadena, estampa y revocación, y deja el archivo en verificación pendiente si la ECD cae: S06 |

El monitor de terceros, el reprocesador nocturno, los carriles y el registro de evidencia quedan como caja negra. Su comportamiento se ve en la vista general, en la vista de concurrencia y en las máquinas de estado.

## Dónde se ve cada táctica

| ASR | ADR | Táctica | Diagrama principal | También en |
|---|---|---|---|---|
| D01 · L04 | ADR-001 | EST-07 Pipe and Filter · ESC-05 Asincronía y colas | DG-CMP-003 | DG-CST-001 |
| D01 | ADR-002 | DIS-01 Ping / echo · DIS-03 Monitor | DG-STM-003 | DG-CMP-003, DG-DEP-004 |
| D01 | ADR-002 | DIS-04 Excepción / timeout | DG-CST-002 | DG-SEQ-002, DG-STM-003 |
| D01 | ADR-003 | DIS-12 Reintento (diferido a la ventana nocturna) | DG-STM-002 | DG-CMP-003, DG-CON-001 |
| D01 | ADR-003 | DIS-14 Degradación con gracia (acusar sin el tercero) | DG-CST-002 | DG-SEQ-002 |
| D01 | ADR-004 | DIS-17 Transacciones (llave del archivo) | DG-CST-001 | DG-SEQ-001, DG-STM-001 |
| L04 | ADR-005 | DIS-22 Bulkhead | DG-CON-001 | DG-CMP-003, DG-DEP-003 |
| L04 | ADR-005 | LAT-03 Priorizar eventos (enrutar por tamaño) | DG-CST-001 | — |
| L04 | ADR-005 | LAT-12 Programar los recursos | DG-CON-001 | — |
| S06 | ADR-006 | SEG-02 Autenticar actores · SEG-11 Verificar integridad · SEG-08 Validar la entrada | DG-CST-003 | DG-STM-001 |
| S06 | ADR-007 | LAT-10 Mantener múltiples copias de datos · DIS-14 Degradación con gracia | DG-CST-003 | DG-SEQ-003 |
| S06 | ADR-008 | SEG-18 Registro de auditoría | DG-CMP-003 | — |

Cada táctica lleva su precio anotado en una nota o una restricción sobre su elemento.

## Lo que estos diagramas todavía no muestran

- Los parámetros que BCR no ha fijado van como `TBD` visible: hora de la ventana nocturna, días de reintento, umbral entre carriles, timeout de los adaptadores, periodo de sondeo, refresco de la caché de revocación, tamaño de los pools y réplicas.
- STM, ANTA, MAMC y RNIC aparecen como una sola caja externa, porque los cuatro se comportan igual ante una caída.
- En la vista general, los carriles A y B se dibujan como un solo procesador con dos instancias aisladas; el aislamiento se ve en la concurrencia y en el despliegue.
- El monitor de terceros, el reprocesador nocturno y el registro de evidencia no tienen caja blanca propia: el tope es de tres por vista.
- Los ASR no críticos no tienen diagrama propio.
