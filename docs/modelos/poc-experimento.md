---
title: PoC del experimento
parent: Vistas de arquitectura
nav_order: 6
---

# PoC del experimento: lo que se construyó y cómo corre una prueba

Esta página muestra el prototipo con que se pone a prueba la arquitectura de los tres ASR críticos. El primer diagrama muestra las piezas construidas y cómo se conectan; el segundo abre el bus de eventos con sus tres tópicos; el tercero muestra qué pasa en cada ejecución del experimento, desde que se prepara hasta que sale su veredicto. El diseño del experimento está en la página de [experimentos](../experiments.md).

Cada diagrama se dibuja en el navegador a partir del archivo [`poc-experimento.drawio`](drawio/poc-experimento.drawio). Con la lupa se amplía, y con el botón de pantalla completa se lee mejor un diagrama grande. El lápiz lo abre en diagrams.net para editarlo. Debajo de cada diagrama hay un enlace a su imagen fija, exportada del mismo archivo, para los visores que no dibujan el `.drawio`.

{% capture vista %}{% include_relative drawio/poc-experimento.drawio %}{% endcapture %}

## DG-CMP-101 · Componentes del PoC

| Campo | Valor |
|---|---|
| Tipo | Componentes, vista general (`alcance: sistema`) |
| ASR | ASR-D01, ASR-L04, ASR-S06 |
| ADR | ADR-001 a ADR-008 |
| Pregunta | ¿Qué se construyó para probar las decisiones y cómo se conecta? |

{% include drawio.html xml=vista page=0 %}

Imagen fija de DG-CMP-101: [poc-dg-cmp-101.png](png/poc-dg-cmp-101.png)

- **Qué muestra.** Se lee de izquierda a derecha, en el orden en que viaja un archivo: el generador de carga le pide al remitente simulado que arme y firme un archivo, la recepción lo recibe, el bus de eventos lo entrega al carril que le toca y el carril escribe el resultado en el repositorio. Las flechas que tocan el bus van en el sentido en que viaja el mensaje: quien publica apunta al bus y el bus apunta a quien recibe la entrega. A la derecha están los terceros simulados y el proxy que los corta. Abajo, la observabilidad. El orquestador y el generador de carga corren fuera de la frontera, en el equipo anfitrión. Cada componente de Orquídea lleva la táctica que aplica y la insignia de los ASR que toca.
- **Cómo se elige el carril.** La recepción cuenta los bytes mientras el archivo llega. Con el último byte publica en `carril-a` si el archivo pesa 30 000 bytes o menos, y en `carril-b` si pesa más, sin abrirlo (SUP-07). El bus solo le entrega `carril-a` al carril A y `carril-b` al carril B. El umbral es un valor de prueba que BCR no ha fijado.
- **Decisión que refleja.** La recepción custodia, verifica, registra la llave y enruta por tamaño antes de acusar (ADR-001, 004, 005, 006). Los dos carriles son contenedores separados, con pool y CPU propios (ADR-005); si el RNIC no responde, publican el archivo en `pendientes`. El monitor detecta la caída y el regreso del tercero (ADR-002), y el reprocesador corre la ventana nocturna (ADR-003, 007). La evidencia queda en un registro de solo anexar, firmado (ADR-008).
- **Qué no muestra.** Que cada servicio también escribe sus hechos en el registro de eventos, que el colector de métricas raspa a los seis servicios cada 2 s, y que la recepción consulta la CRL de la ECD a través del proxy. Los dos carriles y el reprocesador sí aparecen con sus dos relaciones hacia los terceros: le preguntan al monitor si el RNIC está caído y le reportan cada llamada vencida, y llaman al RNIC a través del proxy. Si el RNIC cae a mitad de un masivo, el carril B lo deja pendiente desde el lote donde quedó. Se omitieron para que el diagrama se lea; están en el código y en la tabla de abajo.

### De cada componente a lo que corre

El diagrama es lógico y no nombra productos. Cada componente corresponde a un contenedor de `deploy/docker-compose.yml`:

| Componente | Contenedor · tecnología | Qué hace en el PoC |
|---|---|---|
| Recepción y acuse | `recepcion` · Java 21, Spring Boot | Lee el XML una vez en streaming: custodia, hash, resumen, validación de estructura y verificación criptográfica. Registra la llave, anexa la evidencia, publica en su carril y acusa |
| Bus de eventos | `reto4-kafka` · Kafka 3.9 en modo KRaft | Tópicos `carril-a` y `carril-b` (12 particiones cada uno) y `pendientes` (3). Ver DG-CMP-102 |
| Carril A · pequeños / Carril B · masivos | `carril-a` (8 hilos, 2 CPU) · `carril-b` (5 hilos, 3 CPU) | Procesan las declaraciones por lotes y escriben un solo resultado por llave. Si el RNIC no responde, dejan el archivo pendiente |
| Monitor de terceros | `monitor-terceros` | Ping/echo cada 1 s al RNIC y a la ECD; caído con 3 fallos seguidos |
| Reprocesador nocturno | `reprocesador` | Lee los pendientes hasta el corte de la ventana; reprocesa, reprograma o notifica |
| Repositorio y registro de eventos | `postgres` · PostgreSQL 17 | Esquema `operacion` (archivos, resultados, pendientes, evidencia de solo anexar) y `registro.evento`, la fuente del veredicto |
| Remitente simulado | `remitente-sim` | Arma, firma y envía los archivos, incluido el catálogo criptográfico. Anota lo que se espera de cada uno |
| RNIC simulado / ECD simulada | `tercero-rnic-sim` / `ecd-sim` | Cruce de cédula contra nombre; publicación de la CRL |
| Proxy de fallas | `reto4-toxiproxy` · Toxiproxy 2.12 | Corta al RNIC o a la ECD, o les agrega latencia |
| Inyector de fallas | `reto4-inyector` | Anota el instante de cada falla y se la pide al proxy; en D-5 repite mensajes en la cola |
| Colector de métricas / Tableros de evidencia | `prometheus` · Prometheus 3 / `grafana` · Grafana 13 | Métricas cada 2 s; tres tableros, uno por ASR, que cruzan el registro de eventos por la llave |
| Generador de carga | k6, en el equipo anfitrión | 400 pequeños por minuto con arribo estocástico, y los masivos |
| Orquestador de ejecuciones | `load/experimento.sh` | Lee `load/plan.tsv` y corre cada ejecución |

## DG-CMP-102 · El bus y sus tópicos

| Campo | Valor |
|---|---|
| Tipo | Componentes: el bus de la vista general, abierto con sus tópicos |
| ASR | ASR-D01, ASR-L04 |
| ADR | ADR-001, ADR-003, ADR-005, ADR-007 |
| Pregunta | ¿Qué viaja por cada tópico, quién lo publica y quién lo consume? |

{% include drawio.html xml=vista page=1 %}

Imagen fija de DG-CMP-102: [poc-dg-cmp-102.png](png/poc-dg-cmp-102.png)

- **Qué muestra.** El mismo bus de la vista general, con sus tres tópicos como elementos. `carril-a` lleva los archivos de hasta 30 000 bytes y solo lo consume el carril A; `carril-b` lleva los demás y solo lo consume el carril B. `pendientes` recibe lo que no se pudo terminar: el archivo cuyo RNIC no respondió, que publica el carril, y el archivo con la revocación sin verificar, que publica la recepción. El reprocesador lo lee en cada ventana nocturna y, si verifica un archivo que estaba pendiente, lo publica en el tópico de su carril.
- **Decisión que refleja.** La separación de tópicos es el bulkhead de ADR-005: ningún masivo entra a la cola del carril A. La recepción acusa solo después de que el bus confirma el mensaje, así que lo acusado ya está en la cola (ADR-001). `pendientes` es la espera durable de ADR-003 y ADR-007.
- **Qué no muestra.** Las particiones de cada tópico ni el reparto entre los hilos de cada carril. Con la llave del archivo como clave, dos masivos pueden caer en el mismo hilo del carril B y procesarse uno tras otro; así ocurrió en L-1.

## DG-ACT-101 · Ciclo de una ejecución

| Campo | Valor |
|---|---|
| Tipo | Actividad con carriles |
| ASR | ASR-D01, ASR-L04, ASR-S06 |
| ADR | ADR-002, ADR-003, ADR-005, ADR-006, ADR-008 |
| Pregunta | ¿Qué pasa en cada ejecución del experimento, de la preparación al veredicto? |

{% include drawio.html xml=vista page=2 %}

Imagen fija de DG-ACT-101: [poc-dg-act-101.png](png/poc-dg-act-101.png)

- **Qué muestra.** Cada fila de `load/plan.tsv` es una ejecución, y todas siguen el mismo ciclo. Se prepara la topología con las variables de la fila y se calienta con carga que no entra a la medición. Luego se mide: la carga de fondo corre mientras, en el segundo que fija la fila, se corta o se hace lento al tercero, se lanzan masivos, se envía el catálogo o se reenvían archivos. Se corren las ventanas nocturnas, se cierra con la auditoría de la evidencia y se calcula el veredicto en SQL.
- **Decisión que refleja.** La ventana «abajo» corre con el tercero todavía caído y debe reprogramar sin rechazar (ADR-003). La «arriba» corre después de restablecerlo y debe procesar todo lo pendiente.
- **Qué no muestra.** El detalle de cada escenario (D-1 a S-2). Está en `load/plan.tsv` y en la página de experimentos.

## Validez de una ejecución

El veredicto solo cuenta si la ejecución es válida: un hueco de más de 30 s en la carga, una suspensión del equipo o más de 1 % de solicitudes fallidas la contaminan, y el orquestador la repite una vez.

{% include drawio-script.html %}
