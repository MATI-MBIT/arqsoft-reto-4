---
title: Vistas de arquitectura
nav_order: 8
has_children: true
---

# Vistas de arquitectura de Orquídea

Los diagramas hacen visibles las decisiones de arquitectura: cada táctica de un [ADR](adrs-orquidea.md) aparece en un diagrama con su marca `«tactic»` y su precio en una nota. Todos están en un solo archivo de draw.io, [`orquidea.drawio`](drawio/orquidea.drawio), con una página por diagrama.

| Vista | Qué responde |
|---|---|
| [Vista de contexto](vista-contexto.md) | Con quién habla Orquídea y qué intercambia con cada uno. |
| [Vista funcional](vista-funcional.md) | Qué etapas y componentes tiene Orquídea y dónde vive cada táctica. |
| [Vista dinámica y de concurrencia](vista-concurrencia.md) | Cómo se comporta Orquídea en el tiempo: los objetos activos, los carriles, y las secuencias que demuestran cada ASR con su camino de fallo. |
| [Vista de información](vista-informacion.md) | Qué datos guarda Orquídea, cómo se atan a su versión, su resultado y su auditoría, y por qué estados pasa un archivo. |
| [Vista de despliegue](vista-despliegue.md) | Dónde corre cada etapa, cuántas réplicas tiene y por qué protocolo se comunica. |

## Lo que estos diagramas todavía no muestran

- Las cajas blancas del autorizador por remitente, del núcleo de validación, de la auditoría y de los proyectores. Sus decisiones se ven en las secuencias, pero no su estructura interna.
- La máquina de estados del outbox y la de la sesión del remitente, que piden las fichas de esas tácticas.
- La vista general tiene 24 cruces de líneas: el bus de eventos conecta casi todas las etapas. El detalle de cada flujo está en las secuencias.
