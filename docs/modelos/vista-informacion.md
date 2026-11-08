---
title: Vista de información
parent: Vistas de arquitectura
nav_order: 4
---

# Vista de información de Orquídea

Qué guarda Orquídea, dónde lo guarda y quién es su dueño, y por qué estados pasan el archivo, la declaración y cada tercero en el monitor. La vista se alinea con los componentes de la [vista de componentes](vista-componentes.md) (DG-CMP-003).

Cada diagrama se dibuja en el navegador a partir del archivo [`vista-informacion.drawio`](drawio/vista-informacion.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El enlace «Abrir en draw.io», debajo de cada diagrama, abre esa misma página en diagrams.net desde el archivo publicado en el repositorio, para verla completa o editar una copia. Debajo de cada diagrama hay un enlace a su imagen fija, exportada del mismo archivo, para los visores que no dibujan el `.drawio`.

Todos los diagramas usan la convención de la vista de componentes. Cada elemento lleva la insignia de los ASR en cuyo alcance está: llena si es artefacto del ASR y punteada si participa sin modificarse. Al pie de cada diagrama van sus convenciones y la tabla de trazabilidad de los ASR críticos hacia sus elementos.

{% capture vista %}{% include_relative drawio/vista-informacion.drawio %}{% endcapture %}

## DG-CLS-001 · Modelo de información y modelo canónico

El diagrama de clases de lo que Orquídea guarda por cada archivo: el remitente y su certificado, el archivo con su llave única, el acuse, la verificación criptográfica y su evidencia, las declaraciones, lo pendiente por tercero, el estado de cada tercero, los resultados y las notificaciones (D01, S06).

Incluye el modelo canónico al que traducen los adaptadores de la frontera anticorrupción. `DatoTercero` es la clase abstracta, y cada tercero tiene su subclase: `TasaMercado` (STM), `InfoTributariaAduanera` (ANTA), `InfoMigratoria` (MAMC) e `IdentidadCiudadano` (RNIC). Así el núcleo nunca ve el formato propio de un tercero.

Los atributos de negocio de la declaración (moneda, monto, tasa y documento del titular) y los valores de `Decision` y `Resultado` son una propuesta del equipo `[SUPUESTO → equipo]`. Los atributos de los datos de ANTA y MAMC siguen abiertos `[PREGUNTA → BCR]`.

{% include drawio.html xml=vista page=0 %}

Imagen fija de DG-CLS-001: [informacion-dg-cls-001.png](png/informacion-dg-cls-001.png)

## DG-PER-001 · Persistencia

Dónde vive cada dato y qué componente lo escribe o lo lee. Se dibuja con las formas del curso: dominio, servicio, repositorio, base de datos y tabla.

- **Cola durable.** El log de Kafka (KRaft) guarda los tópicos `carril-a`, `carril-b`, `pendientes` y `estado-terceros`. El acuse sale solo después de custodiar el archivo y publicarlo, de modo que nada acusado se pierde si un tercero cae (ADR-001, ADR-003).
- **Esquema `repositorio`.** Doce tablas en PostgreSQL, agrupadas por dueño. El repositorio de archivos es la única puerta de escritura, y la llave única del archivo y la llave (archivo, declaración) del resultado evitan duplicados (ADR-004).
- **Esquema `evidencia`.** Una entrada por archivo, de solo anexar y firmada por BCR (ADR-008).
- **Caché local de revocación.** Vive en memoria del nodo de recepción y permite verificar con la ECD caída mientras la lista siga vigente (ADR-007).

Los flujos numerados del diagrama dicen quién escribe o lee en cada almacén. Los nombres de tablas y columnas, y la estructura de los mensajes, se proponen a partir de DG-CLS-001 `[SUPUESTO → equipo]`. Falta decidir si los bytes del XML custodiado van en la tabla `archivo` o en un almacenamiento de objetos `[PREGUNTA → equipo]`.

{% include drawio.html xml=vista page=1 %}

Imagen fija de DG-PER-001: [informacion-dg-per-001.png](png/informacion-dg-per-001.png)

## DG-STM-001 · Ciclo de vida del archivo

De recibido a verificado, a verificación pendiente o a rechazado, y de ahí a resuelto. Ningún camino llega a resuelto sin pasar por verificado (S06).

Con la ECD caída y la caché vencida, el archivo queda acusado pero sin resultado. El reprocesador lo reverifica en cada ventana nocturna y, al agotar los días de reintento, se notifica al remitente sin aceptarlo ni descartarlo (D01, ADR-007).

{% include drawio.html xml=vista page=2 %}

Imagen fija de DG-STM-001: [informacion-dg-stm-001.png](png/informacion-dg-stm-001.png)

## DG-STM-002 · Ciclo de vida de la declaración

Los estados de `Declaracion.estado`, desde que el carril la desencola hasta que su resultado queda escrito. Si el tercero no responde o está caído, la declaración pasa a pendiente; cada ventana nocturna la reprograma y suma un intento (D01, ADR-003).

Al agotar los días de reintento se notifica al remitente, y la declaración sigue pendiente hasta que el tercero vuelva. El resultado se escribe una sola vez por (archivo, declaración), aunque el carril y el reprocesador compitan (ADR-004). Si la declaración espera a varios terceros, hay una `DeclaracionPendiente` por tercero.

{% include drawio.html xml=vista page=3 %}

Imagen fija de DG-STM-002: [informacion-dg-stm-002.png](png/informacion-dg-stm-002.png)

## DG-STM-003 · Estado de un tercero en el monitor

Un tercero pasa de disponible a caído después de N fallos seguidos, sea un eco que no llega a tiempo o una llamada que vence su timeout. La lentitud cuenta como caída (ADR-002).

El monitor solo publica los cambios de estado en `estado-terceros`. Cuando el tercero vuelve, el orquestador y el reprocesador retoman lo pendiente en la siguiente ventana. Los valores de T_sondeo, T_echo y N siguen abiertos `[PREGUNTA → BCR]`.

{% include drawio.html xml=vista page=4 %}

Imagen fija de DG-STM-003: [informacion-dg-stm-003.png](png/informacion-dg-stm-003.png)

{% include drawio-script.html %}
