---
title: Vista de componentes
parent: Vistas de arquitectura
nav_order: 2
---

# Vista de componentes de Orquídea

Qué componentes tiene Orquídea y cómo están construidos por dentro los tres que sostienen los ASR críticos.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-componentes.drawio`](drawio/vista-componentes.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo.

{% capture vista %}{% include_relative drawio/vista-componentes.drawio %}{% endcapture %}

## DG-CMP-002 · Vista general de Orquídea

El sistema en una página, con cuatro paquetes: recepción y verificación, mensajería (el bus con sus tópicos), procesamiento por carriles y disponibilidad ante terceros. Cada componente lleva la marca de su táctica, y cada sistema externo su protocolo.

{% include drawio.html xml=vista page=0 %}

## DG-CST-001 · Recepción (caja blanca)

Cómo la recepción registra la llave única del archivo, anexa la evidencia, clasifica por tamaño y publica en el carril, y acusa sin esperar a terceros (D01, L04, S06).

{% include drawio.html xml=vista page=1 %}

## DG-CST-002 · Integración con terceros (caja blanca)

Dónde la caída de un tercero se vuelve pendiente y no rechazo: el orquestador, el timeout de cada adaptador y la degradación con gracia (D01).

{% include drawio.html xml=vista page=2 %}

## DG-CST-003 · Verificador criptográfico (caja blanca)

Las partes que verifican la firma, el firmante, la cadena, la estampa y la revocación, con la caché local y la verificación pendiente cuando la ECD no responde (S06).

{% include drawio.html xml=vista page=3 %}

{% include drawio-script.html %}
