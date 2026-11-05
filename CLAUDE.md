# arqsoft-reto-4 — contexto del proyecto

Proyecto final (reto 4) de **ARTI4109 Arquitectura de Software** (MATI,
Universidad de los Andes). Sistema: **Orquídea**, el Sistema de Información de
Operaciones de Divisas de la entidad BCR. Atributos: latencia, escalabilidad,
disponibilidad, seguridad, modificabilidad e interoperabilidad.

Equipo (Consultores Grupo 1): Nicolás Rozo, Carlos Chaparro, Rafael Reyes y
Néstor Rodríguez. Cliente del reto: David Guarnizo (BCR).

Este archivo guarda las reglas estables. Las decisiones con fecha van en
`notas/decisiones.md`, y la bitácora de cada sincronización con Helix, en
`notas/contrato-de-replica.md`.

---

## La meta

**`docs/` es la fuente de verdad. Helix es una réplica que se pide a demanda.**

La orden es *«sincroniza helix reto 4»*: tomar lo que está en `docs/` y llevarlo
a Helix. Nunca al revés. El procedimiento está en `notas/contrato-de-replica.md`
y la interfaz de Helix, en `notas/helix-recorrido-funcional.md` (heredado del
reto 2).

Arquitectura del reto 4 en Helix:
<https://helix.virtual.uniandes.edu.co/architecture/6d798a38-3a23-4b8d-ac44-42f325b34b19>

## Insumos

En `insumos/`, solo en local (los PDF no se versionan):

| Archivo | Qué es |
|---|---|
| `ARTI4109-proyecto-final-v5.pdf` | El enunciado del curso. Manda sobre todo lo demás |
| `Informe-Consultoria-Orquidea-Grupo1.pdf` | Informe de consultoría del equipo (3-nov-2026): AS-IS, 16 ASR candidatos, TO-BE, 18 ADR |
| `Respuesta a preguntas-Grupo1.pdf` | Respuesta del cliente (3-nov-2026): aprueba los 18 ADR, prioriza seis ASR, pide cuatro vistas y deja seis preguntas para la Fase 0 |

Lo que la respuesta del cliente fijó es vinculante: N = 5 masivos simultáneos en
pico, t0 del acuse desde el último byte, archivos de hasta 50 MB, 4 h-persona
con desarrollo y pruebas, exención del SLA de latencia con externos caídos.

## La estructura de `docs/`

Sitio Jekyll con Just the Docs, publicado por `.github/workflows/pages.yml`.
Cualquier `.md` que llegue a `main` dentro de `docs/` se publica solo. En
GitHub Pages, el Source tiene que estar en *GitHub Actions*.

La raíz de `docs/` espeja las secciones de Helix. Cada página declara en su
front matter la clave `helix_section`:

| Archivo | `helix_section` | Papel | Estado |
|---|---|---|---|
| `index.md` | (portada) | — | Hecho |
| `architecture.md` | `Objective` | Insumo | Hecho |
| `stakeholders.md` | `Stakeholders` | Insumo | Hecho |
| `constraints.md` | `Constraints` | Insumo | Hecho |
| `requirements.md` | `Requirements & Quality` | Insumo | Hecho |
| `glossary.md` | `Objective → Glossary` | Insumo | Hecho |
| `quality-attributes.md` | `Requirements & Quality → Quality Scenarios` | Sale de los insumos | Hecho |
| `experiments.md` | `Experiments` | Valida decisiones | Pendiente |
| `results.md` | `Experiments → Results & analysis` | Resultados | Pendiente |
| `modelos/` | No se carga en Helix | ADR (`adrs-orquidea.md`), vistas, draw.io y PNG | Hecho (ADR en Propuesta) |

## La regla de flujo

**Las páginas de insumo no nombran los ASR.** El problema, los stakeholders,
las restricciones, el glosario y las historias son la materia con la que se
construyen los ASR. Los criterios de aceptación de una historia dicen qué tiene
que pasar, nunca en cuánto tiempo: el umbral aparece por primera vez en el
escenario de calidad.

Para verificar: `grep -c ASR docs/*.md` debe dar cero en todo salvo
`index.md` (la portada enlaza la página de ASR), `quality-attributes.md`,
`experiments.md` y `results.md`.

## Cómo se escriben los ASR

Con la skill `arquitectura:definir-asrs-software`. Lo que se fijó para este
reto:

- **Una sola medida por ASR, sin hermanos** (Nicolás, 2026-11-04). La trampa
  de cada medida queda como nota en «Por qué es ASR».
- Los ID siguen el esquema del informe (`ASR-L`, `E`, `D`, `S`, `M`, `I`, con
  número), porque el cliente prioriza con esos ID y Helix numera por atributo.
- El ASR va en el espacio del problema: no nombra componentes, colas ni
  tecnologías, aunque el cliente ya haya aprobado los ADR.

## Lo que no va en `docs/`

`docs/` se publica en GitHub Pages. No lleva instrucciones de Helix, estado del
trabajo, historial de versiones ni resultados de chequeos de las skills: eso va
en `notas/`. Tampoco marcas de versión («v1», «versión 2») en ninguna página
(Nicolás, 2026-11-04).

## Convenciones

- **`[PREGUNTA → <a quién>]`** marca un dato que falta. **`[SUPUESTO → <a quién>]`**,
  un número que propusimos nosotros, con su pregunta. Nunca se inventa una cifra.
- Español de Bogotá. Los nombres de sección de Helix van en inglés porque así
  los muestra la herramienta.
- Redacción con `editorial:escribir-y-pulir-textos`: frases de 20 a 30
  palabras, títulos descriptivos, sin aforismos. El wiki es de entrada
  múltiple: cada página se sostiene sola.
- Vocabulario: **declaración** (cada registro reportado), **archivo** (el XML
  que llega), **remitente** (el IMC o titular que envía), **acuse** (la
  confirmación de recibo), **resultado definitivo** (aceptación total, parcial
  o rechazo). Pequeño: 1 a 50 registros · mediano: 51 a 5 000 · masivo: 5 001 a
  100 000.

## Entorno

- No hay `gh` CLI: GitHub por `git` con SSH o por el MCP de GitHub.
- Ruby local es 2.6, insuficiente para Jekyll 4.4. La verificación real es el
  workflow de Actions.
- La sincronización con Helix usa el MCP `chrome-devtools` sobre una sesión
  abierta. Borrar elementos en Helix y crear nodos con `evaluate_script` hay
  que autorizarlo aparte.
