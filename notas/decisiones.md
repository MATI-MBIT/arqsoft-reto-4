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

## Registro de ADR (docs/modelos/adrs-orquidea.md)

- 2026-11-04 · 12 ADR en estado Propuesta, retados contra los 18 del informe. Quedan fuera: topología activo-activo (infraestructura), notificación por webhooks (funcional, sin ASR) y Kafka como decisión propia (es producto, va como concern C-01 dentro de ADR-001).
- Lint: `Contrato: el registro declara adr-log/v2; validado con lint_adrs (adr-log/v2, fichas tactica/v1, plugin arquitectura 1.19.1)` · 0 errores, 1 aviso. W08 en DG-CMP-001 (12 elementos): se acepta porque es la vista general del flujo.
- Pendiente: el equipo decide qué ADR pasan a Aceptada. Solo el humano acepta.

## Diagramas (docs/modelos/drawio/orquidea.drawio)

- 2026-11-04 · 26 páginas generadas (revisadas el mismo día: clasificación por bytes, nodo de procesamiento en el despliegue, parte «control» del circuit breaker) con `diagramar-uml-arquitectura` desde un solo modelo (ids compartidos entre vistas): los 17 diagramas del plan de ADR más 8 de eclosión (caja blanca y secuencia interna de Recepción y verificación, Carril pesado, Adaptadores y Configuración versionada) y la máquina de estados del circuit breaker.
- Lint: `Contrato: lint_diagrama con fichas tactica/v1 · registro adr-log/v2 · plugin arquitectura 1.19.1` · 26 diagramas, 0 con ERROR, 30 avisos. Justificación de los avisos:
  - TAC «roles no verificados» (SEG-03, SEG-18, MOD-03, MOD-04, INT-04, DIS-12, DIS-14, DIS-17): esos componentes no se eclosionaron; quedan como hueco.
  - TAC «máquina de estados» (SEG-02, SEG-18, MOD-04, DIS-17): falta su diagrama de estados; hueco.
  - TAC de roles en el N1 de Adaptadores: el rol «client» estaba ligado a una parte «adapter», y la ficha pide «control» o «boundary»; se corrigió con la parte `client : LlamadaProtegida` «control». Quedan 4 avisos de roles en puertos (target, supplier, adaptee, breakerEvents): el modelo los declara con estereotipo «port», pero el emisor no escribe el estereotipo en el rótulo del puerto (verificado en el XML), así que el lint no lo ve.
  - LAYOUT: 24 cruces en la vista general (el bus conecta casi todo) y aristas de retorno en varias páginas: preferencias, no bloquean.
  - AP-09 (15 elementos) en la caja blanca de Adaptadores; AP-18/19/20 en el contexto y en páginas de componentes que no demuestran una medida.
- Puerta 3 (mirada): revisadas en PNG la vista general, SEQ-005, CST-003, DEP-001 y CLS-001. Defecto visible: en CST-003 la línea del rol «adaptee» pasa sobre el rótulo del puerto supplier.
