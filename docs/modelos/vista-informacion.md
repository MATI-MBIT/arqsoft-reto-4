---
title: Vista de información
parent: Vistas de arquitectura
nav_order: 4
---

# Vista de información de Orquídea

Qué guarda Orquídea por cada archivo y por qué estados pasan el archivo, la declaración pendiente y cada tercero en el monitor.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-informacion.drawio`](drawio/vista-informacion.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo.

{% capture vista %}{% include_relative drawio/vista-informacion.drawio %}{% endcapture %}

## DG-CLS-001 · Modelo de información

El remitente, el archivo con su llave única, la verificación criptográfica y su evidencia, las declaraciones, lo pendiente por tercero, el estado de cada tercero y los resultados (D01, S06).

{% include drawio.html xml=vista page=0 %}

## DG-STM-001 · Ciclo de vida del archivo

De recibido a verificado, en verificación pendiente o rechazado, y de ahí a resuelto. Ningún camino llega a resuelto sin pasar por verificado (S06, D01).

{% include drawio.html xml=vista page=1 %}

## DG-STM-002 · Ciclo de vida de la declaración pendiente

Una declaración que espera a un tercero caído: se reprograma en cada ventana, se notifica al agotar los días de reintento y se enriquece cuando el tercero vuelve (D01).

{% include drawio.html xml=vista page=2 %}

## DG-STM-003 · Estado de un tercero en el monitor

Cómo el ping/echo y el timeout llevan a un tercero de disponible a caído, y cómo su regreso habilita el reproceso (D01).

{% include drawio.html xml=vista page=3 %}

{% include drawio-script.html %}
