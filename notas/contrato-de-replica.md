# Contrato de réplica hacia Helix

`docs/` es la fuente de verdad. Helix es una réplica que se pide a demanda, con
la orden *«sincroniza helix reto 4»*. La réplica va en un solo sentido: lo que
alguien edite directo en Helix se pierde en la siguiente sincronización.

Arquitectura destino:
<https://helix.virtual.uniandes.edu.co/architecture/6d798a38-3a23-4b8d-ac44-42f325b34b19>
La comparte el grupo G1 con rol de edición: lo que se cargue lo ven todos.

---

## Qué página alimenta qué sección

Cada página de `docs/` declara en su front matter la clave `helix_section`. Esa
clave es el contrato: si una sección de Helix no tiene página, no tiene fuente.

| Página | Sección en Helix | Campos |
|---|---|---|
| `architecture.md` | `Objective` | `Statement` · `In scope` · `Out of scope` · `Purpose` |
| `stakeholders.md` | `Stakeholders` | `Name` · `Role` · `Description`, uno por fila |
| `constraints.md` | `Constraints` | Un bloque por restricción, en `Business` o `Technology` |
| `requirements.md` | `Requirements & Quality` | Árbol Epic → Feature → Story |
| `quality-attributes.md` | Escenarios de calidad colgados de las historias | Atributo · prioridad · artefacto · ambiente · TPS · medida |
| `glossary.md` | `Objective → Glossary` | Un solo editor |
| `experiments.md` | `Experiments`, uno por hipótesis | `Planning` y `Results & analysis` |

## El orden de carga no es negociable

El campo `As a` de cada historia es un desplegable de stakeholders. De ahí sale
la secuencia:

1. `Stakeholders`
2. `Objective` y `Constraints`
3. `Requirements & Quality`, el árbol de historias
4. Los escenarios de calidad, colgados de su historia

## Lo que se aprendió en el reto 2 y sigue valiendo

- Cada editor de texto enriquecido tiene `Import Markdown`. El panel abre solo
  con un clic real; una vez abierto, el cuadro se llena por script y `Insert`
  responde a `click()`. Para reemplazar un campo: clic en el editor, `Cmd+A`,
  `Backspace`, `Import Markdown`, pegar e `Insert`.
- El editor no guarda imágenes ni tablas: las imágenes van como enlace al PNG
  publicado en el wiki y las tablas, como viñetas.
- No hay exportación: el *Agent channel* está deshabilitado para Uniandes. La
  verificación de la réplica es visual, sección por sección.
- Helix deriva `Source`, `Stimulus` y `Response` del escenario de la historia
  (stakeholder, `I want`, `so that`). A mano solo se cargan atributo,
  prioridad, artefacto, ambiente, TPS y medida.
- La medida de tiempo es un deslizador con techo de 60 000 ms. La prioridad va
  de `No priority` a `Critical`.
- Las casillas `Security Requirements` son tácticas: no se marcan al cargar un
  ASR.

## Lo que se aprendió en el reto 4

Recorrido del 2026-11-04, al cargar los escenarios:

- **Cada atributo trae otro formulario de medida.**
  - `Performance · Latency`: p95 y p99, con techo de 6 000 ms.
  - `Performance · Scalability`: carga inicial y final en TPS, duración de la subida y p95 y p99 con techo de 6 000 ms.
  - `Availability`: un porcentaje obligatorio, que arranca en 99 %, y `Detection Time`, con techo de 60 000 ms.
  - `Security`: nivel de autenticación, `Response Time` con techo de 60 000 ms y casillas de requisitos.
  - `Modifiability` e `Interoperability`: ambiente y medida como texto enriquecido, sin artefacto ni TPS.
- **Lo que no cabe en un control va en el texto.** Las medidas que no son de tiempo, las relativas y las que pasan el techo se escriben en el ambiente con el prefijo «Medida:», y el control queda en 0.
  - No caben: L04 (relativa); L03 y L05 (pasan los 6 s); S03, S04, S06 y D03 (no son de tiempo); D01 y D04 (no son detección).
- **El porcentaje de disponibilidad es obligatorio.** Ningún ASR lo fija, así que el texto de cada escenario de `Availability` lo aclara.
- **El nombre del escenario lo genera Helix** con el título de la historia y el atributo. El ID del ASR va al comienzo del artefacto, o del ambiente en modificabilidad e interoperabilidad.
- **«Undefined parts: Artifact»** aparece en el árbol de utilidad aunque el texto esté guardado: Helix espera que el artefacto sea un elemento de un modelo. Se resuelve cuando se dibujen las vistas en Helix.
- **El rol de un stakeholder es un combo con sugerencias.** La lista de sugerencias tapa el botón `Done`. Hay que cerrarla con el botón «Show used roles» antes de guardar, porque si no el clic cae sobre una sugerencia y cambia el rol.
- **En la lista de atributos, «Detection» aparece dos veces** (Availability y Security). Se elige con un clic sobre la opción, no por su texto.
- **Los campos de título se guardan con un retraso.** Si se cambia de nodo enseguida, el título se pierde. Hay que esperar a que el árbol muestre el título nuevo.

## Los ASR en Helix

Cada escenario cuelga de una historia de usuario. Helix toma la fuente del
escenario del stakeholder de la historia, así que en S01 y S06 muestra a la
Auditoría de BCR y no al remitente. El TPS del ambiente A - Operación normal es 7 (400 pequeños por
minuto) y el del B, 20 (1 200 por minuto).

| ID | Atributo en Helix | Prioridad en Helix | ¿Cabe la medida? | Historia |
|---|---|---|---|---|
| ASR-L04 | Performance · Latency | Critical | Es relativa: por confirmar | HU-03 |
| ASR-D01 | Availability · Recovery | Critical | Sí: 1 000 ms | HU-06 |
| ASR-S01 | Security · Resistance | High | Sí: 200 ms | HU-02 |
| ASR-M01 | Modifiability | Critical | Por confirmar | HU-11 |
| ASR-E01 | Performance · Scalability | High | Sí: 5 000 ms, TPS 20 | HU-03 |
| ASR-I01 | Interoperability | High | Por confirmar | HU-13 |
| ASR-D03 | Availability · Prevention | High | No es de tiempo | HU-07 |
| ASR-L01 | Performance · Latency | High | Sí: 1 000 ms | HU-01 |
| ASR-S03 | Security · Resistance | High | No es de tiempo | HU-09 |
| ASR-S06 | Security · Resistance | Critical | No es de tiempo | HU-02 |
| ASR-D04 | Availability · Prevention | High | Sí: 1 000 ms | HU-11 |

| ASR-L03 | Performance · Latency | Medium | No: 300 000 ms supera el techo de 60 000 ms | HU-04 |
| ASR-D02 | Availability · Recovery | Medium | Sí: 10 000 ms | HU-07 |
| ASR-L05 | Performance · Latency | Medium | Sí: 30 000 ms | HU-03 |
| ASR-M02 | Modifiability | Medium | Por confirmar | HU-12 |
| ASR-S04 | Security · Detection | Medium | No es de tiempo | HU-14 |
| ASR-I02 | Interoperability | Low | Por confirmar | HU-10 |

D01 y D04 miden la latencia del acuse en un ambiente degradado. Se cargaron como
`Availability` con el control `Detection Time` en 0 y la medida escrita en el
ambiente, para que Helix no la muestre como tiempo de detección (decisión del
2026-11-04).

---

## Bitácora de sincronizaciones

### 2026-11-04 — primera carga completa

| Sección | Qué se cargó | Verificación |
|---|---|---|
| `Stakeholders` | Los 12 de `stakeholders.md` | Lista leída tras recargar |
| `Objective` | `Statement`, `In scope`, `Out of scope`, `Purpose` y `Glossary`, por `Import Markdown` | Longitud y comienzo de cada campo tras recargar |
| `Constraints` | R-1 a R-9 en `Business` y R-10 a R-12 en `Technology`, con el origen al final. R-13 a R-15 no van: Helix no tiene restricciones de proyecto | Los 12 textos tras recargar |
| `Requirements & Quality` | 4 épicas (EP-001 a 004), 10 funcionalidades (FE-001 a 010) y 14 historias (US-001 a 014), con el ID de `docs/` al comienzo del título. Los criterios de aceptación no se cargan: Helix no tiene campo para ellos | Árbol completo |
| Escenarios de calidad | Los 17 ASR (QS-001 a QS-017), cada uno en su historia | Árbol de utilidad tras recargar: 5 de desempeño, 4 de disponibilidad, 4 de seguridad, 2 de interoperabilidad y 2 de modificabilidad |

El recorrido del reto 2 había dejado rastro sucio en `ARQ-005`; en esta arquitectura no quedó nada sucio.
