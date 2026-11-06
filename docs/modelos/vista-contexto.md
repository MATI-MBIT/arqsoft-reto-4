---
title: Vista de contexto
parent: Vistas de arquitectura
nav_order: 1
---

# Vista de contexto de Orquídea

Quién envía archivos a Orquídea, quién recibe sus resultados y de qué sistemas externos depende.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-contexto.drawio`](drawio/vista-contexto.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo.

{% capture vista %}{% include_relative drawio/vista-contexto.drawio %}{% endcapture %}

## DG-CMP-001 · Contexto de Orquídea

El remitente (IMC o titular), el operador de BCR, la ECD que emite firmas y estampas, y los sistemas externos de enriquecimiento alrededor de Orquídea. STM, ANTA, MAMC y RNIC van en una sola caja porque se comportan igual ante una caída.

{% include drawio.html xml=vista page=0 %}

{% include drawio-script.html %}
