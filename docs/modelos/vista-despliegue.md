---
title: Vista de despliegue
parent: Vistas de arquitectura
nav_order: 5
---

# Vista de despliegue

Dónde corre cada etapa, cuántas réplicas tiene y por qué protocolo se comunica. Responde la vista de despliegue y topología de red que pidió BCR.

## DG-DEP-001 · Despliegue y topología de red

![DG-DEP-001 · Despliegue y topología de red](png/DG-DEP-001.png)

- **Qué muestra:** Los nodos de ingesta, de cada carril, del broker y de datos, con sus réplicas, los artefactos y el protocolo y puerto de cada ruta.
- **Decisión que refleja:** ADR-001 y ADR-002: cada carril con su despliegue. ADR-010: TLS mutuo en la entrada.
- **Qué no muestra:** La alta disponibilidad entre zonas, que queda para el juicio de expertos.
