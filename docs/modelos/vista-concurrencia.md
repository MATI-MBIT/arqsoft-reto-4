---
title: Vista de concurrencia
parent: Vistas de arquitectura
nav_order: 3
---

# Vista de concurrencia de Orquídea

Qué corre en paralelo, qué se sincroniza y cómo recorre cada componente clave su operación crítica, con su camino de fallo.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-concurrencia.drawio`](drawio/vista-concurrencia.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo. Debajo de cada diagrama hay un enlace a su imagen fija, exportada del mismo archivo, para los visores que no dibujan el `.drawio`.

{% capture vista %}{% include_relative drawio/vista-concurrencia.drawio %}{% endcapture %}

## DG-CON-001 · Concurrencia de carriles y reproceso

La recepción, los tópicos de cada carril y de pendientes, un pool de trabajadores por carril y el reprocesador. Todos escriben el resultado, que es único por archivo y declaración (L04, D01).

{% include drawio.html xml=vista page=0 %}

Imagen fija de DG-CON-001: [concurrencia-dg-con-001.png](png/concurrencia-dg-con-001.png)

## DG-SEQ-001 · Recepción (secuencia interna)

Del último byte al acuse: verificar, registrar la llave, anexar la evidencia y enrutar, con los caminos de firma inválida y archivo duplicado (D01, L04).

{% include drawio.html xml=vista page=1 %}

Imagen fija de DG-SEQ-001: [concurrencia-dg-seq-001.png](png/concurrencia-dg-seq-001.png)

## DG-SEQ-002 · Integración con terceros (secuencia interna)

Enriquecer una declaración con el tercero disponible o caído, y con respuesta o timeout. En los dos caminos de fallo la declaración queda pendiente, nunca rechazada (D01).

{% include drawio.html xml=vista page=2 %}

Imagen fija de DG-SEQ-002: [concurrencia-dg-seq-002.png](png/concurrencia-dg-seq-002.png)

## DG-SEQ-003 · Verificador criptográfico (secuencia interna)

La firma inválida se rechaza con su causa. Con la revocación vencida, si la ECD responde se refresca, y si está caída el archivo queda en verificación pendiente, sin aceptarse (S06).

{% include drawio.html xml=vista page=3 %}

Imagen fija de DG-SEQ-003: [concurrencia-dg-seq-003.png](png/concurrencia-dg-seq-003.png)

{% include drawio-script.html %}
