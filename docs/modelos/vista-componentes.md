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

El sistema completo en una página: la recepción, la cola durable, los carriles A y B, el verificador criptográfico, el monitor de terceros, el reprocesador nocturno y el registro de evidencia. Muestra el pipeline asíncrono (D01, L04) y la caché de revocación (S06).

{% include drawio.html xml=vista page=0 %}

## DG-CST-001 · Recepción (caja blanca)

Cómo acusa la recepción sin llamar a terceros, cómo rechaza un archivo duplicado con la llave en una transacción local y cómo enruta por tamaño al carril A o al B (D01, L04).

{% include drawio.html xml=vista page=1 %}

## DG-CST-002 · Verificador criptográfico (caja blanca)

Las partes que verifican firma, firmante, cadena, estampa y revocación, y la degradación a verificación pendiente cuando la ECD no responde (S06).

{% include drawio.html xml=vista page=2 %}

## DG-CST-003 · Reprocesador nocturno (caja blanca)

Cómo toma lo pendiente en la ventana nocturna, lo reprograma para el día siguiente si el tercero sigue caído y notifica al remitente al agotar los días de reintento (D01).

{% include drawio.html xml=vista page=3 %}

{% include drawio-script.html %}
