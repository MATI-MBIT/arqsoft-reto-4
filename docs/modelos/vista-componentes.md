---
title: Vista de componentes
parent: Vistas de arquitectura
nav_order: 2
---

# Vista de componentes de Orquídea

Qué componentes tiene Orquídea y cómo están construidos por dentro los tres que sostienen los ASR críticos.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-componentes.drawio`](drawio/vista-componentes.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo. Debajo de cada diagrama hay un enlace a su imagen fija, exportada del mismo archivo, para los visores que no dibujan el `.drawio`.

{% capture vista %}{% include_relative drawio/vista-componentes.drawio %}{% endcapture %}

## DG-CMP-003 · Vista funcional de Orquídea

El sistema en una página, en notación UML de componentes, de izquierda a derecha: recepción y verificación, mensajería, procesamiento por carriles e integración con terceros; debajo, disponibilidad ante terceros y datos y entrega. Cada componente lleva su táctica y la insignia de los ASR en cuyo alcance está, y cada nota amarilla resume un ADR con su precio.

Los tópicos del bus aparecen como componentes con un ícono de cola de mensajes, y los componentes que guardan datos llevan un ícono de cilindro. Las bases de datos no se dibujan aquí: su modelo va en la vista de información.

{% include drawio.html xml=vista page=0 %}

Imagen fija de DG-CMP-003: [componentes-dg-cmp-003.png](png/componentes-dg-cmp-003.png)

## DG-CST-001 · Recepción (caja blanca)

Cómo la recepción registra la llave única del archivo, anexa la evidencia, clasifica por tamaño y publica en el carril, y acusa sin esperar a terceros (D01, L04, S06).

{% include drawio.html xml=vista page=1 %}

Imagen fija de DG-CST-001: [componentes-dg-cst-001.png](png/componentes-dg-cst-001.png)

## DG-CST-002 · Integración con terceros (caja blanca)

Dónde la caída de un tercero se vuelve pendiente y no rechazo: el orquestador, el timeout de cada adaptador y la degradación con gracia (D01).

{% include drawio.html xml=vista page=2 %}

Imagen fija de DG-CST-002: [componentes-dg-cst-002.png](png/componentes-dg-cst-002.png)

## DG-CST-003 · Verificador criptográfico (caja blanca)

Las partes que verifican la firma, el firmante, la cadena, la estampa y la revocación, con la caché local y la verificación pendiente cuando la ECD no responde (S06).

{% include drawio.html xml=vista page=3 %}

Imagen fija de DG-CST-003: [componentes-dg-cst-003.png](png/componentes-dg-cst-003.png)

{% include drawio-script.html %}
