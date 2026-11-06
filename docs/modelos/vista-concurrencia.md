---
title: Vista de concurrencia
parent: Vistas de arquitectura
nav_order: 3
---

# Vista de concurrencia de Orquídea

Qué corre en paralelo, qué se sincroniza y cómo recorre cada componente clave su operación crítica, con su camino de fallo.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-concurrencia.drawio`](drawio/vista-concurrencia.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo.

{% capture vista %}{% include_relative drawio/vista-concurrencia.drawio %}{% endcapture %}

## DG-CON-001 · Procesos, colas y estado compartido

Los procesos que corren a la vez, una cola durable y un pool de trabajadores por carril (bulkhead, L04), la cola de pendientes del reprocesador y el estado compartido que se sincroniza: el resultado y el estado de los terceros (D01).

{% include drawio.html xml=vista page=0 %}

## DG-SEQ-001 · Recepción (secuencia interna)

Desde el último byte del archivo hasta el acuse: llave de idempotencia, rechazo del duplicado y enrutamiento por tamaño, sin esperar a ningún tercero (D01, L04).

{% include drawio.html xml=vista page=1 %}

## DG-SEQ-002 · Verificador criptográfico (secuencia interna)

La verificación completa y el camino de fallo cuando la ECD está caída: el archivo queda en verificación pendiente, nunca aceptado sin verificar, y la evidencia queda firmada (S06).

{% include drawio.html xml=vista page=2 %}

## DG-SEQ-003 · Reprocesador nocturno (secuencia interna)

La corrida de la ventana nocturna con el timeout de cada adaptador, la reprogramación al día siguiente y la notificación al agotar los reintentos (D01).

{% include drawio.html xml=vista page=3 %}

{% include drawio-script.html %}
