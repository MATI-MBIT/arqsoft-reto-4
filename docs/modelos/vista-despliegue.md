---
title: Vista de despliegue
parent: Vistas de arquitectura
nav_order: 5
---

# Vista de despliegue de Orquídea

Dónde corre cada parte en producción y cómo se monta el experimento local en Docker.

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-despliegue.drawio`](drawio/vista-despliegue.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El enlace «Abrir en draw.io», debajo de cada diagrama, abre esa misma página en diagrams.net desde el archivo publicado en el repositorio, para verla completa o editar una copia. Debajo de cada diagrama hay un enlace a su imagen fija, exportada del mismo archivo, para los visores que no dibujan el `.drawio`.

{% capture vista %}{% include_relative drawio/vista-despliegue.drawio %}{% endcapture %}

## DG-DEP-003 · Despliegue objetivo

Dónde corre cada servicio en producción. Cada nodo muestra lo que se instala en él: el `.jar` de Spring Boot sobre JVM 21 y su configuración, los esquemas de PostgreSQL, los tópicos de Kafka y la custodia de archivos. Las rutas llevan su protocolo y su puerto, y cada nodo dice cuántas instancias corren (L04, D01, S06).

Los componentes lógicos no se repiten aquí: están en la [vista de componentes](vista-componentes.md) (DG-CMP-003). Los carriles A y B corren el mismo `carril.jar` en nodos separados, cada uno con su configuración, y esa separación es el bulkhead de ADR-005.

{% include drawio.html xml=vista page=0 %}

Imagen fija de DG-DEP-003: [despliegue-dg-dep-003.png](png/despliegue-dg-dep-003.png)

## DG-DEP-004 · Despliegue del experimento local

Un solo equipo con Docker Compose, tal como lo arma `deploy/docker-compose.yml`. Los contenedores de Orquídea corren los mismos `.jar` del despliegue objetivo. Alrededor están los simuladores del remitente, del RNIC y de la ECD, Toxiproxy y el inyector de fallas, Kafka y PostgreSQL, y Prometheus con Grafana. Por fuera de Docker, en el mismo equipo, k6 genera la carga con `pequenos.js` y `masivos.js`, y `experimento.sh` orquesta cada ejecución. Ver la página de [experimentos](../experiments.md).

{% include drawio.html xml=vista page=1 %}

Imagen fija de DG-DEP-004: [despliegue-dg-dep-004.png](png/despliegue-dg-dep-004.png)

{% include drawio-script.html %}
