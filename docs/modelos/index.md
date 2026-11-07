---
title: Vistas de arquitectura
nav_order: 8
has_children: true
---

# Vistas de arquitectura de Orquídea, centradas en los tres ASR críticos

Esta sección reúne los diagramas que hacen visibles las decisiones de arquitectura de Orquídea. Las decisiones están en los [ADR](adrs-orquidea.md), agrupadas por ASR, y los requisitos, en la [página de ASR](../quality-attributes.md).

Los diagramas se centran en los tres ASR críticos: ningún archivo se rechaza por la caída de un sistema externo (ASR-D01, el foco), el aislamiento de carga entre archivos masivos y pequeños (ASR-L04) y ningún archivo aceptado sin validación criptográfica completa (ASR-S06).

Cada vista tiene su página, donde los diagramas se dibujan en el navegador, y su archivo de draw.io. El primer diagrama de cada vista es el global, y los demás, hasta tres, detallan lo que el global no alcanza a mostrar.

## Las cinco vistas

| Vista | Diagramas | Global | Detalle | Qué responde |
|---|---|---|---|---|
| [Contexto](vista-contexto.md) | [Ver](vista-contexto.md) · [`.drawio`](drawio/vista-contexto.drawio) | DG-CMP-001 | — | Quién envía, quién recibe y de qué terceros depende Orquídea |
| [Componentes](vista-componentes.md) | [Ver](vista-componentes.md) · [`.drawio`](drawio/vista-componentes.drawio) | DG-CMP-002 | DG-CST-001 · DG-CST-002 · DG-CST-003 | Qué componentes tiene Orquídea, y cómo están construidos por dentro la recepción, el verificador criptográfico y el reprocesador nocturno |
| [Concurrencia](vista-concurrencia.md) | [Ver](vista-concurrencia.md) · [`.drawio`](drawio/vista-concurrencia.drawio) | DG-CON-001 | DG-SEQ-001 · DG-SEQ-002 · DG-SEQ-003 | Qué corre en paralelo, qué se sincroniza y cómo recorre cada componente clave su operación crítica, con su camino de fallo |
| [Información](vista-informacion.md) | [Ver](vista-informacion.md) · [`.drawio`](drawio/vista-informacion.drawio) | DG-CLS-001 | DG-STM-001 · DG-STM-002 · DG-STM-003 | Qué guarda Orquídea por archivo, y por qué estados pasan el archivo, la declaración pendiente y cada tercero en el monitor |
| [Despliegue](vista-despliegue.md) | [Ver](vista-despliegue.md) · [`.drawio`](drawio/vista-despliegue.drawio) | DG-DEP-001 | DG-DEP-002 | Dónde corre cada parte en producción, y cómo se monta el experimento local en Docker |

## Componentes clave

Tres componentes se abren de caja negra a caja blanca, porque están en el camino crítico de los ASR. Su comportamiento se dibuja como secuencia interna en la vista de concurrencia.

| Componente | Caja blanca | Secuencia interna | Por qué es clave |
|---|---|---|---|
| Recepción | DG-CST-001 | DG-SEQ-001 | Acusa sin llamar a terceros, rechaza el duplicado y enruta por tamaño: D01 y L04 |
| Verificador criptográfico | DG-CST-002 | DG-SEQ-002 | Verifica firma, firmante, cadena, estampa y revocación, y degrada a verificación pendiente si la ECD cae: S06 |
| Reprocesador nocturno | DG-CST-003 | DG-SEQ-003 | Reprocesa lo pendiente en la ventana nocturna, lo reprograma o lo notifica al agotar los días: D01 |

El monitor de terceros, los adaptadores, los carriles y el registro de evidencia quedan como caja negra. Sus tácticas se ven en la vista general, en las máquinas de estado y en la vista de concurrencia.

## Dónde se ve cada táctica

| ASR | Táctica | Diagrama principal | También en |
|---|---|---|---|
| D01 | DIS-03 Monitor | DG-STM-003 | DG-CMP-002, DG-CON-001 |
| D01 | DIS-04 Timeout en cada adaptador | DG-STM-002 | DG-CMP-002, DG-SEQ-003 |
| D01 | DIS-12 Reintento diferido a la ventana nocturna | DG-CST-003 | DG-SEQ-003, DG-STM-002, DG-CON-001, DG-CMP-002 |
| D01 | DIS-14 Degradación con gracia (acusar sin el tercero) | DG-STM-002 | DG-CMP-002 |
| D01 | DIS-17 Llave del archivo en una transacción local | DG-CST-001 | DG-SEQ-001, DG-STM-001, DG-CLS-001 |
| D01 · L04 | ESC-05 y EST-07 Pipeline asíncrono sobre una cola durable | DG-CMP-002 | DG-CON-001 |
| L04 | DIS-22 Bulkhead (un pool por carril) | DG-CON-001 | DG-DEP-001 |
| L04 | LAT-03 Enrutar por tamaño al recibir | DG-CST-001 | DG-SEQ-001 |
| S06 | SEG-02 Autenticar (firma y firmante) | DG-CST-002 | DG-SEQ-002, DG-CMP-002 |
| S06 | SEG-08 Validar la entrada (parser seguro) | DG-CST-002 | DG-SEQ-002 |
| S06 | SEG-11 Verificar integridad | DG-CST-001 | — |
| S06 | DIS-14 Degradación (verificación pendiente) | DG-CST-002 | DG-SEQ-002, DG-STM-001 |
| S06 | LAT-10 Revocación en caché local | DG-CMP-002 | DG-CLS-001 |
| S06 | SEG-18 Registro de evidencia firmado | DG-SEQ-002 | DG-CLS-001, DG-CMP-002, DG-DEP-001 |

Cada táctica lleva su precio anotado en una nota de la misma página.

## Lo que estos diagramas todavía no muestran

- Los parámetros que BCR no ha fijado van como `TBD` visible: hora de la ventana nocturna, días de reintento, umbral entre carriles, periodo de sondeo, timeout, desfase y antigüedad de la estampa, refresco de la caché, tamaño de los pools y réplicas.
- STM, ANTA, MAMC y RNIC aparecen como una sola caja externa en el contexto y en la vista general, para que se lean; los cuatro tienen el mismo comportamiento ante una caída.
- Los ASR no críticos no tienen diagrama propio.
