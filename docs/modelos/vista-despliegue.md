---
title: Vista de despliegue
parent: Vistas de arquitectura
nav_order: 5
---

# Vista de despliegue de Orquídea

Dónde corre cada parte en producción y cómo se monta el experimento local en Docker.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-despliegue.drawio`](drawio/vista-despliegue.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo.

{% capture vista %}{% include_relative drawio/vista-despliegue.drawio %}{% endcapture %}

## DG-DEP-001 · Despliegue objetivo

Un nodo para la recepción, uno por carril y uno de soporte, más el nodo de datos con Kafka y PostgreSQL, con las réplicas y el protocolo de cada ruta (L04, D01, S06).

{% include drawio.html xml=vista page=0 %}

## DG-DEP-002 · Despliegue del experimento local

Un equipo con Docker Compose y los once contenedores del experimento, incluidos los simuladores del RNIC y de la ECD y Toxiproxy para inyectar caídas. Ver la página de [experimentos](../experiments.md).

{% include drawio.html xml=vista page=1 %}

{% include drawio-script.html %}
