# Decisiones del equipo

Lo que se fija en el camino, con fecha y quién lo decidió. Lo estable sube a
`CLAUDE.md`; aquí queda el rastro.

| Fecha | Quién | Decisión |
|---|---|---|
| 2026-11-04 | Nicolás | El repo se estructura como el del reto 2: `docs/` es la fuente de verdad y Helix una réplica en un solo sentido |
| 2026-11-04 | Nicolás | Los ASR llevan una sola medida y no hay ASR hermanos. La trampa de cada medida va como nota en la ficha |
| 2026-11-04 | Nicolás | Los ASR se ordenan por importancia para revisarla en equipo; el orden de `docs/quality-attributes.md` es una propuesta |
| 2026-11-04 | Nicolás | Sin marcas de versión en las páginas. Helix, el estado del trabajo y los chequeos de las skills no se publican: van en `notas/` |
| 2026-11-04 | Nicolás | D01 se reformuló: la medida «0 archivos rechazados» del informe se cumplía dejando las declaraciones pendientes para siempre. Ahora mide que la falla de un externo no llegue al acuse |

## Pendientes del chequeo de los ASR

El chequeo de 26 puntos de `definir-asrs-software` falla en tres, y los tres
esperan una cifra de BCR:

- **Punto 12, disponibilidad.** Falta un ASR de detección.
- **Punto 12, seguridad.** Falta un ASR de reacción.
- **Punto 14.** Ningún ASR detecta un resultado que sale a tiempo pero mal,
  como un masivo notificado como aceptado total cuando le faltan declaraciones.

## Pendientes del equipo

- ¿Quiénes forman el panel de expertos que valida M01 e I01? Al menos dos humanos de fuera del curso.
- Los orígenes por lente de divergencia de cada ASR (ambiente, estímulo repetido, segundo orden, inversión, fuente) se quitaron de la página publicada; quedan en el historial de git.
