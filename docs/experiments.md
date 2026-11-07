---
title: Experimento
nav_order: 9
helix_section: "Experiments"
---

# Experimento local para los tres ASR críticos: disponibilidad ante terceros caídos, aislamiento de carga y validación criptográfica

El experimento pone a prueba, en un solo equipo con Docker, las decisiones de arquitectura que atienden a los tres ASR críticos: [ASR-D01](quality-attributes.md#asr-d01), [ASR-L04](quality-attributes.md#asr-l04) y [ASR-S06](quality-attributes.md#asr-s06). La disponibilidad ante la caída de un sistema externo es el foco, y por eso tiene más escenarios.

El experimento no tiene que cumplir los ASR. Si una medida no se alcanza, se reporta el resultado con su análisis: un resultado honesto vale más que uno maquillado.

## 1. Qué se pone a prueba

| ASR | Hipótesis | Decisiones que se prueban |
|---|---|---|
| ASR-D01 | Con un sistema externo caído durante la carga, todos los archivos válidos quedan aceptados al cierre de la primera ventana nocturna tras su regreso | [ADR-001, ADR-002 y ADR-003](modelos/adrs-orquidea.md) |
| ASR-L04 | Con 5 archivos masivos en proceso, el p99 de los archivos pequeños es ≤ 3 s | [ADR-004](modelos/adrs-orquidea.md) |
| ASR-S06 | Ningún archivo con firma, firmante, estampa, cadena o revocación inválida, o sin poder verificarse, llega a aceptación | [ADR-005, ADR-006 y ADR-007](modelos/adrs-orquidea.md) |

El alcance es acotado: la recepción del archivo, la clasificación por tamaño y el enrutamiento al carril A o B, la validación de la firma, y la caída de un sistema externo con su reproceso. Quedan fuera la autenticación del canal (TLS mutuo), la integración con el Banco de la República, las reglas cambiarias completas y la entrega a las áreas de BCR.

## 2. Componentes

Todo corre en contenedores de Docker Compose sobre un solo equipo. El diagrama está en la vista de despliegue (DG-DEP-002).

| Contenedor | Papel en el experimento | Decisión que encarna |
|---|---|---|
| `recepcion` | Recibe el archivo por HTTP, lo custodia, verifica la firma, registra la llave del archivo, emite el acuse y lo publica en el tópico de su carril según el tamaño en bytes | ADR-001, ADR-004, ADR-003, ADR-005 |
| `kafka` | Cola durable en modo KRaft, nodo único, con los tópicos `carril-a`, `carril-b` y `pendientes` | ADR-001 |
| `carril-a` | Procesa archivos pequeños con su propio pool y su propio límite de CPU, sin componentes de arranque lento | ADR-004 |
| `carril-b` | Procesa archivos masivos en modo batch, con su propio pool y su propio límite de CPU | ADR-004 |
| `monitor-terceros` | Sondea al sistema externo simulado y publica su estado: disponible, degradado o caído | ADR-002 |
| `reprocesador` | Corre la ventana de reproceso a una hora configurable o al llamar a un endpoint de prueba; reprograma lo que sigue pendiente y manda a notificar lo que agotó los reintentos, sin sacarlo de los pendientes | ADR-002, ADR-006 |
| `tercero-rnic-sim` | RNIC simulado: una tabla de personas ficticias que cruza cédula contra nombre | — |
| `toxiproxy` | Proxy entre los carriles y el sistema externo simulado, para cortarlo o agregarle latencia en mitad de la carga | — |
| `ecd-sim` | Autoridad certificadora de prueba con su lista de revocación y su servicio de estampa cronológica | ADR-006 |
| `postgres` | Repositorio de archivos, llaves, declaraciones, pendientes y resultados, y el registro de evidencia de solo anexar | ADR-003, ADR-007 |
| `generador-carga` | k6: genera archivos pequeños a ritmo constante y dispara los masivos | — |

El RNIC simulado hace las veces de la Registraduría que se nombró en la reunión. Basta con un sistema externo simulado: los otros tres tienen el mismo comportamiento ante una caída.

Para probar la caída se usa una tabla de personas ficticias. Una cédula cargada que no corresponde al nombre tiene que terminar rechazada por la regla, no por la caída.

## 3. Datos de prueba

| Conjunto | Contenido |
|---|---|
| Archivos pequeños | 1 a 50 registros, firmados con un certificado vigente de `ecd-sim` |
| Archivos masivos | 100 000 registros y hasta 50 MB, firmados |
| Personas ficticias | Tabla del RNIC simulado; un 5 % de las declaraciones lleva una cédula que no corresponde al nombre `[SUPUESTO → equipo]` |
| Catálogo criptográfico | Un archivo por caso: firma alterada, firma envuelta (XSW), firmante distinto del remitente, certificado revocado, certificado de una autoridad no aceptada, estampa con la hora desfasada y estampa vencida |

## 4. Escenarios

La disponibilidad va primero porque es el foco. Los tiempos de caída de horas se comprimen: una caída de «una noche» dura lo que tarde la prueba en llegar a la ventana disparada a mano.

### Disponibilidad (ASR-D01)

| ID | Escenario | Qué se hace | Qué se observa |
|---|---|---|---|
| D-1 | Caída total y reproceso con el tercero de vuelta | Carga de 400 pequeños por minuto durante 20 min; a los 5 min `toxiproxy` corta el RNIC simulado. A los 15 min se restablece y se dispara la ventana | Archivos rechazados por la caída; pendientes antes de la ventana; resueltos al cierre de la ventana; cédulas que no corresponden, rechazadas por la regla |
| D-2 | Tercero todavía caído en la ventana | Como D-1, pero la ventana se dispara con el RNIC aún caído; luego una segunda ventana con el RNIC arriba | Pendientes reprogramados para la ventana siguiente; resueltos en la segunda |
| D-3 | Reintentos agotados | El RNIC sigue caído durante tantas ventanas como el máximo de días de reintento, fijado en 2 solo para la prueba `[SUPUESTO → BCR]` | Archivos notificados al remitente; siguen pendientes y, al restablecer el RNIC, quedan aceptados en la ventana siguiente |
| D-4 | Latencia extrema | `toxiproxy` agrega al RNIC una latencia mayor que el timeout del adaptador | El monitor lo marca degradado o caído; las declaraciones quedan pendientes, sin rechazos ni hilos bloqueados |
| D-5 | Reenvío del mismo archivo | Con archivos pendientes, el generador reenvía 50 de ellos con la misma llave, y 50 archivos nuevos | Los reenvíos se rechazan como duplicados; los nuevos se reciben; ningún original cambia |
| D-6 | ECD caída con la información de revocación vencida | Se apaga `ecd-sim` y se fuerza el vencimiento de la lista en caché; luego se restablece y se dispara la ventana | Archivos en verificación pendiente, sin resultado; resueltos en la ventana |

### Aislamiento de carga (ASR-L04)

| ID | Escenario | Qué se hace | Qué se observa |
|---|---|---|---|
| L-1 | Línea base (complemento) | 400 pequeños por minuto durante 10 min, sin masivos | p99 de los pequeños sin masivos, para el margen de 10 % |
| L-2 | Con 5 masivos | La misma carga, con 5 masivos de 100 000 registros lanzados al minuto 2 | p99 de los pequeños con masivos |
| L-3 | Control sin carriles | Como L-2, pero con un solo tópico y un solo pool para todos los archivos | p99 de los pequeños sin aislamiento, para contrastar con L-2 |
| L-4 | Barrido hasta el quiebre | Como L-2, con 6, 8 y 10 masivos | El N en el que el p99 de los pequeños supera 3 s |

### Validación criptográfica (ASR-S06)

El cliente aceptó que este ASR es difícil de probar y que basta con que la arquitectura lo muestre con claridad. La prueba es mínima.

| ID | Escenario | Qué se hace | Qué se observa |
|---|---|---|---|
| S-1 | Catálogo de archivos inválidos | Se envía cada archivo del catálogo criptográfico, mezclado con la carga de L-1 | Archivos inválidos que llegan a aceptación; causa de rechazo de cada uno |
| S-2 | Evidencia de lo aceptado | Al final de L-1 y de D-1 se cruza cada archivo aceptado contra el registro de evidencia | Archivos aceptados con su entrada firmada y el mismo hash de los bytes procesados |

## 5. Métricas que se capturan

Cada archivo deja sus marcas de tiempo en `postgres`, con el mismo reloj del equipo: t0 cuando la recepción lee el último byte, el acuse, la llegada al carril, el paso a pendiente, cada ventana y el resultado definitivo o la notificación.

| Métrica | Cómo se calcula | Escenarios |
|---|---|---|
| p50, p95 y p99 del resultado definitivo de los pequeños | Del t0 al resultado, por archivo | L-1 a L-4, S-1 |
| Razón de aislamiento | p99 de L-2 ÷ p99 de L-1 | L-2, L-3, L-4 |
| Archivos válidos sin aceptar | Válidos enviados − aceptados al cierre de la primera ventana tras el regreso del externo, por conciliación (también los notificados de D-3) | D-1 a D-6 |
| Archivos rechazados por la caída | Rechazos cuya causa es la falta de respuesta del sistema externo | D-1 a D-4, D-6 |
| Duplicados | Archivos con la misma llave aceptados más de una vez, y resultados escritos dos veces | D-5, D-1 |
| Profundidad de pendientes y duración de la ventana | Mensajes en el tópico `pendientes`; del inicio al fin de cada ventana | D-1 a D-3 |
| Tiempo hasta que el monitor marca la caída | De la caída en `toxiproxy` al cambio de estado | D-1, D-4 |
| Archivos inválidos aceptados | Archivos del catálogo con aceptación total o parcial | S-1, D-6 |
| Aceptados sin evidencia | Archivos aceptados sin entrada firmada o con otro hash | S-2 |
| Referencias del enunciado | p99 del acuse frente a 1 s; p99 de pequeños frente a 3 s; duración de cada masivo frente a 5 min | L-1, L-2 |
| Uso de recursos | CPU y memoria por contenedor, con `docker stats` | Todos |

## 6. Criterio de éxito o fracaso por ASR

Los criterios se fijan antes de correr, y cada resultado se reporta con su análisis aunque falle.

| ASR | Éxito | Fracaso | Qué se reporta además |
|---|---|---|---|
| ASR-D01 | 0 archivos sin desenlace y 0 rechazados por la caída en D-1 a D-6, y 0 duplicados en D-5 | Un archivo acusado que se pierde, se rechaza por la caída, queda sin desenlace o se duplica | Profundidad de pendientes, duración de la ventana y tiempo de detección del monitor |
| ASR-L04 | p99 de los pequeños en L-2 ≤ 3 s | p99 de los pequeños en L-2 > 3 s | El contraste con L-3, el N de quiebre de L-4 y las tres referencias del enunciado |
| ASR-S06 | 0 archivos inválidos aceptados en S-1 y D-6, y 100 % de los aceptados con evidencia en S-2 | Un solo archivo inválido aceptado, o uno aceptado sin evidencia | La causa de rechazo de cada caso del catálogo |

## 7. Amenazas a la validez

- **Tiempos optimistas.** En local no hay saltos de red, API Gateway ni balanceadores. Los p99 absolutos serán menores que en producción. Un p99 ≤ 3 s en local no prueba los 3 s en producción; por eso se reporta también la razón de aislamiento (L-2 ÷ L-1), más confiable porque compara dos corridas en el mismo equipo.
- **Recursos compartidos.** Los dos carriles comparten disco, memoria y bus del mismo equipo, aunque tengan límites de CPU propios. Si L-2 falla, hay que descartar que la contienda venga del equipo y no del diseño.
- **Horas comprimidas.** Las caídas de 2 a 20 horas y la ventana nocturna se simulan en minutos. El experimento no mide cuánto tarda una ventana real con 500 000 declaraciones acumuladas.
- **Sistemas externos simulados.** El RNIC simulado y la autoridad certificadora de prueba se comportan como se programaron. Una caída real puede ser parcial o devolver datos errados, y eso el monitor no lo detecta.
- **Nodo único de la cola.** Con `kafka` en un solo nodo no se prueba la caída de la cola misma.
- **Muestra pequeña de masivos.** Con 5 masivos por corrida, su duración se reporta como máximo y no como percentil.
- **Componentes de arranque lento.** Si en el carril A se usara un componente que tarda alrededor de un minuto en levantar, como un job tipo Glue, la línea base saldría inflada. El carril A del experimento no tiene ninguno, y en producción tampoco debe tenerlo.

## 8. Parámetros que dependen de BCR

Mientras BCR no los fije, el experimento usa valores de prueba, y el informe de resultados los declara como tales: la hora de la ventana (se dispara a mano), el máximo de días de reintento (2), el umbral en bytes entre carriles (el tamaño de un archivo de 50 registros), el timeout del adaptador y el periodo de sondeo del monitor, el desfase de reloj tolerado en la estampa y su antigüedad máxima. Las preguntas están en la [página de ASR](quality-attributes.md#6-abierto).
