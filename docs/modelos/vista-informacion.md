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

El archivo con su llave única, sus declaraciones, lo pendiente por tercero, el resultado, la entrada de evidencia de solo anexar, la información de revocación y el estado de cada tercero (D01, S06).

{% include drawio.html xml=vista page=0 %}

## DG-STM-001 · Ciclo de vida del archivo (estados)

De custodiado a registrado, verificado o en verificación pendiente, y de ahí a resultado, rechazo, rechazo por duplicado o notificación. Ningún camino llega al resultado sin pasar por la verificación (D01, S06).

{% include drawio.html xml=vista page=1 %}

## DG-STM-002 · Declaración pendiente (estados)

Una declaración que espera a un tercero caído: degradación con gracia, timeout, reintento en la ventana nocturna y notificación al agotar los días (D01).

{% include drawio.html xml=vista page=2 %}

## DG-STM-003 · Estado de un tercero en el monitor (estados)

Cómo el monitor lleva a cada tercero entre disponible, sospechoso, degradado, caído, recuperando y sin datos, con un solo aviso por caída (D01).

{% include drawio.html xml=vista page=3 %}

{% include drawio-script.html %}
