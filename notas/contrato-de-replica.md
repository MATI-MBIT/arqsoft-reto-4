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

## Lo que falta saber del reto 4

- `[PREGUNTA → recorrido de Helix]` Qué deslizadores trae la medida de
  `Performance · Latency`, `Performance · Scalability`, `Modifiability` e
  `Interoperability`. El reto 2 solo exploró disponibilidad y seguridad.
- ASR-L03 mide 5 min (300 000 ms) y no cabe en el techo de 60 000 ms.

## Los ASR en Helix

Cada escenario cuelga de una historia de usuario. Helix toma la fuente del
escenario del stakeholder de la historia, así que en S01 y S06 muestra a la
Auditoría de BCR y no al remitente. El TPS del ambiente A es 7 (400 pequeños por
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

---

## Bitácora de sincronizaciones

Sin sincronizaciones todavía.
