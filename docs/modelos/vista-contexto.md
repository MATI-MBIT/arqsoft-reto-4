---
title: Vista de contexto
parent: Vistas de arquitectura
nav_order: 1
---

# Vista de contexto de Orquídea

Quién envía archivos a Orquídea, quién usa sus resultados y de qué sistemas externos depende.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-contexto.drawio`](drawio/vista-contexto.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El enlace «Abrir en draw.io», debajo de cada diagrama, abre esa misma página en diagrams.net desde el archivo publicado en el repositorio, para verla completa o editar una copia. Debajo de cada diagrama hay un enlace a su imagen fija, exportada del mismo archivo, para los visores que no dibujan el `.drawio`.

{% capture vista %}{% include_relative drawio/vista-contexto.drawio %}{% endcapture %}

## DG-CMP-001 · Contexto de Orquídea

Orquídea como caja negra, con el remitente (por HTTPS), el operador de BCR y las áreas consumidoras. Afuera quedan STM, ANTA, MAMC y RNIC, agrupados porque se comportan igual ante una caída, y la ECD con su lista de revocación, OCSP y estampa.

{% include drawio.html xml=vista page=0 %}

Imagen fija de DG-CMP-001: [contexto-dg-cmp-001.png](png/contexto-dg-cmp-001.png)

{% include drawio-script.html %}
