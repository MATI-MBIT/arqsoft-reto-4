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

### 2026-11-05 · Regeneración completa: tácticas visibles por tipo de diagrama

- Pedido de Nicolás: que cada táctica se vea en el diagrama donde mejor se demuestra (secuencia, clases, estados, concurrencia), no solo como marca en los componentes. El mapa táctica → diagrama primario quedó en `docs/modelos/index.md`.
- 51 páginas (antes 26), desde un solo `modelo.json` con ids compartidos (el modelo vive en el scratchpad de la sesión; no se versiona). Nuevas: 6 eclosiones más (Verificador de revocación, Núcleo de validación en dos N1, Orquestador, Autorizador, Auditoría, Proyectores), DG-CLS-002 (patrón Adapter, INT-01), DG-CLS-003 (modelo canónico, INT-04), DG-CLS-004 (API del motor de reglas, MOD-03 y MOD-09), DG-STM-003/004/005 (pendientes, outbox, cadena de auditoría), DG-CON-002 (pendientes y drenaje, LAT-02), DG-SEQ-010 (contrato nuevo de un externo, ASR-I01), DG-ACT-001 (activar y revertir, MOD-12) y las generales derivadas DG-CON-000 y DG-CLS-000.
- Vista general: cada componente con la marca de su táctica principal. Para que se lea, omite quién más publica en el bus (núcleo, orquestador), la suscripción del gestor de pendientes y quién más escribe en la auditoría; esas relaciones están en las cajas blancas y en DG-CON-002. Quedan 20 cruces y 7 retornos (preferencia, no bloquea).
- DG-STM-001 se simplificó (sin Recibido ni PendienteEnriquecimiento, que pasó a DG-STM-003) porque como cadena salía franja y el emisor pedía partirla.
- Lint: `Contrato: lint_diagrama con fichas tactica/v1 · registro adr-log/v2 · plugin arquitectura 1.19.1` · 51 diagramas, 0 con ERROR, 42 avisos. Sin AP-20 a AP-23 ni TAC de roles faltantes ni de máquina de estados faltante. Justificación de los avisos:
  - TAC «rol sin «port»» (12, en CST-003/005/006/008/009/010/011): el rol está en un puerto y el emisor no escribe el estereotipo en el rótulo del puerto. TAC «Auditor» en CST-009: falso positivo, el nombre del componente «Auditoría» contiene «Auditor»; la parte `auditor : VerificadorDeIntegridad` existe.
  - AP-09 (13 a 18 elementos) en DEP-001, CST-001/002/005/006/007/008, CON-002 y ACT-001: los puertos y sus interfaces cuentan; ninguno pasa de 20.
  - AP-19 en SEQ-008, SEQ-009, SEQ-107, SEQ-109 y SEQ-111: su medida es un conteo (0 accesos, 100 % detectadas, 0 funciones), no un tiempo; está anotada en la nota.
  - AP-18 en SEQ-002: ASR de latencia, sin camino de fallo que demostrar. AP-16 en las generales derivadas: el emisor no copia notas. AP-20 en CMP-003/004: no son el N0. MARGEN en CST-010: defecto del emisor, sin efecto visible.
- Puerta 3 (mirada): revisadas en PNG la vista general, SEQ-106, CLS-002, CLS-003, CON-001 y CST-007. Se leen bien.
- Pendiente: la columna «Diagramas» del índice y el §6 de `adrs-orquidea.md` siguen con el plan anterior; actualizarlos es decisión del equipo.

### 2026-11-05 · Vistas rehechas desde cero: un archivo por vista, guiadas por los cuatro ASR críticos

- Pedido de Nicolás: no refinar los 51 diagramas anteriores, sino crearlos desde cero con `diagramar-uml-arquitectura` ≥ 1.20.0. Un `.drawio` por vista, máximo 3 páginas por vista (cada una profundiza la anterior), orden de importancia L04 → D01 → S06 → M01 y énfasis en sus tácticas.
- Versión: `diagramar-uml-arquitectura · arquitectura 1.20.0` (la sesión cargó 1.19.2 de `arquitectura~g3`; se usó la 1.20.0 de `arquitectura~g4`).
- Seis vistas en `docs/modelos/drawio/`, 14 páginas: contexto (1), funcional (3), dinámica y concurrencia (3), información (3), despliegue (1) y desarrollo (3). La vista de desarrollo es propuesta nuestra: M01 necesita N1 + N2 + clases y no cabía en las cinco vistas con el tope de 3 páginas.
- Componentes eclosionados: Recepción y verificación (L04, S06), Adaptadores de sistemas externos (D01) y Configuración versionada (M01). Carriles, verificador de revocación, núcleo, orquestador y gestor quedan como caja negra.
- Vista general: para que el ruteo terminara (23 aristas entre paquetes agotaban el tope del emisor) se fundieron el autorizador y los proyectores con Resultado, y la auditoría con el repositorio, y los paquetes quedaron en cuatro. DG-CON-001 salía franja (6:1) y se resolvió con `direccion: DOWN`, sin partirla.
- Lint (puerta 2): `Contrato: lint_diagrama con fichas tactica/v1 · registro adr-log/v2 · plugin arquitectura 1.20.0`. Sobre una copia combinada de las 14 páginas (para verificar la eclosión entre archivos): 14 diagramas, 0 con ERROR, 33 avisos. Por archivo, la vista funcional da 3 ERROR de eclosión (AP-20 de Configuración versionada y AP-23 de los dos N1) porque sus N1 y N2 viven en otros archivos; es consecuencia de partir por vista, y la copia combinada los resuelve. Avisos justificados:
  - TAC «rol sin «port»» en DG-CST-001 y DG-CST-002: el rol está en un puerto y el emisor no escribe el estereotipo en el rótulo del puerto (mismo defecto conocido de la 1.19.1).
  - TAC «roles no verificados» de DIS-12, DIS-14, MOD-03 y SEG-18, y SEG-18 sin máquina de estados ni «provable»: sus componentes no se eclosionaron (los tres primeros se ven en DG-STM-001, DG-CON-001 y DG-CLS-002; SEG-18 es de S04, no crítico).
  - AP-09 (14 a 18 elementos, ninguno pasa de 20) en DG-CST-001/002/003, DG-CON-001, DG-STM-001 y DG-DEP-001: los puertos y sus interfaces cuentan.
  - LAYOUT: 12 cruces y 10 retornos en la vista general, 3 cruces en el despliegue, 1 en la concurrencia; RODEO de las aristas de los carriles al bus. AP-20 en el contexto: no es un N0.
- Puerta 3 (mirada, PNG con draw.io desktop): contexto, vista general, DG-CST-001, DG-CON-001, DG-SEQ-001, DG-STM-001 y DG-STM-002. Se leen; la vista general es la más débil por los rodeos de las aristas hacia el bus.
- Puerta 4: creados 7 / declarados 7. El script reporta además que el plan declara 6 `.drawio` (la regla de un solo `.drawio` por entrega, reemplazada por el pedido de un archivo por vista).
- El `modelo.json` vivió en el scratchpad y no se versiona: tras la entrega manda cada `.drawio`.
- Pendiente: el §6 «Plan de diagramas» de `adrs-orquidea.md` sigue con los IDs anteriores; actualizarlo es decisión del equipo.

### 2026-11-06 · Reunión técnica con David (BCR): tres ASR críticos y simplificación

- Criterio (Carlos): dejar priorizados solo tres ASR para simplificar arquitectura, tácticas y experimento. Foco en disponibilidad.
- Críticos: D01 (foco), L04 y S06. M01 baja a alta. El resto queda documentado sin táctica ni experimento propios.
- Decisiones con Nicolás sobre las contradicciones entre el prompt de la reunión, el correo de David (3-nov) y el repo:
  - Terceros: se mantienen los nombres del enunciado (STM, ANTA, MAMC, RNIC y ECD). Registraduría≈RNIC, Relaciones Exteriores≈MAMC, tasas≈STM.
  - L04 conserva la medida del correo (+10 % de p99). Reemplazado el mismo día por p99 ≤ 3 s (ver la entrada siguiente). Los 1 s, 3 s, 30 s y 5 min siguen en sus ASR y el experimento los reporta como referencia.
  - D01 cambia de medida: «0 archivos sin desenlace al cierre de la primera ventana tras el regreso del externo». Revierte la reformulación del 4-nov porque la respuesta ahora exige reproceso nocturno y notificación, lo que cierra la trampa de los pendientes eternos.
  - Vistas: cuatro (componentes, concurrencia, información, despliegue) más la de contexto, que BCR exigió. La vista de desarrollo sale porque era de M01.
- Masivos: el prompt decía «más de 10 000 registros, ≤ 5 min». El correo no trae esa cifra; se usa la del enunciado (hasta 100 000, ≤ 5 min) y el vocabulario del repo (masivo = 5 001 a 100 000).
- mTLS: fuera del alcance de S06 y del experimento; el ADR-13 de BCR sigue aprobado.
- ADR: el registro se rehízo con 7 ADR (001 a 007) solo para D01, L04 y S06. Los demás salieron de la iteración y quedan en el historial de git. Lint adr-log/v2: 0 errores, 1 aviso (W08 en la vista general).
- Experimento: diseño en `docs/experiments.md`, con plazo de envío el sábado 7-nov-2026.
- ADR sin catálogos (Nicolás): el registro ya no lleva catálogo de elementos, de conectores, plan de diagramas ni «A7. Evidencia visual», porque sesgan los diagramas. Los ADR quedan agrupados por ASR. El lint de `decidir-adrs-arquitectura` da 13 errores de contrato `adr-log/v2` por esa razón, y se acepta.
- Vistas recreadas desde cero por un agente sin el contexto del diseño anterior, que solo leyó los ADR, las fichas de los tres ASR críticos y el experimento. Hay 5 archivos y 15 páginas: contexto (1), componentes (4), concurrencia (4), información (4) y despliegue (2). Se eclosionaron Recepción, el Verificador criptográfico y el Reprocesador nocturno. Salieron vista-funcional, vista-dinamica-concurrencia y vista-desarrollo.
- Lint de los diagramas (`lint_diagrama`, plugin arquitectura 1.20.0) sobre la copia combinada: 1 ERROR que no se resolvió al agotar el tope (B6 en DG-CMP-002: una arista pasa sobre la insignia ASR-S06 de la caché de revocación; la página tiene 23 cruces). Por archivo, la vista de componentes suma 3 AP-23, porque sus secuencias internas viven en la vista de concurrencia. La puerta 3 dio entre 16 y 21 puntos por página. La puerta 4 no corre en modo draw.io con varios archivos: se verificó a mano (5 creados / 5 declarados).
- Una sola caja externa para STM, ANTA, MAMC y RNIC en el contexto y en la vista general. Revisar si BCR exige verlos por separado.

### 2026-11-06 · ASR alineados con las seis partes y redacción corta

- Revisión contra la definición de escenario (fuente, estímulo, artefacto, ambiente, respuesta y medida). Los 17 ASR quedan con una línea por parte; «Por qué es ASR» y «Cómo se verifica» en dos o tres frases.
- D01 (Nicolás): la medida pasa a «0 archivos válidos rechazados o sin resultado definitivo al cierre de la primera ventana nocturna tras el regreso del externo». Contar sobre los válidos cierra el hueco de un sistema que deja de acusar; el plazo evita los pendientes eternos. Válido: se habría aceptado con el externo disponible. El duplicado sale del ASR y queda en HU-06. Título: «Ningún archivo se rechaza por la caída de un sistema externo». La fuente lleva los nombres completos de los sistemas externos.
- L04 (Nicolás): la medida pasa del margen de +10 % frente a la corrida sin masivos a «p99 ≤ 3 s por archivo pequeño», con 5 masivos en el ambiente. Razón: el margen exigía comparar dos corridas y no se leía directo. El +10 % de BCR queda como complemento en la verificación y en el experimento (L-1), con una `[PREGUNTA → BCR]` para confirmar el cambio.
- S06: una sola fuente, artefacto «emisión del resultado definitivo», un ambiente con la ECD como variable. El no repudio sale de la respuesta (sin medida propia): lo sostienen S06 y S04.
- Sincronizados: tabla de ASR y contexto de ADR-002, ADR-004 y ADR-007 en el registro, hipótesis y criterios del experimento, leyendas de los cinco diagramas y el índice de vistas.
- Artefactos (Nicolás): la definición pide «la parte del sistema estimulada», y estaban escritos como procesos. Los 17 pasan a un servicio funcional de Orquídea o a «todo el sistema»: D01 es todo el sistema, porque la caída de un externo recorre de la recepción al resultado; L04, E01, L03, L05, D02 y D03 son el servicio de procesamiento de archivos. Sigue sin nombrarse ningún componente del diseño.
- ASR atómicos (Nicolás): en disponibilidad y seguridad cada ASR es de un solo tipo (detección, corrección o prevención) con una medida de una sola condición. D01 pasa a corrección con «0 archivos válidos sin aceptar al cierre de la primera ventana nocturna tras el regreso del externo»; D03 pasa a corrección con «0 declaraciones acusadas sin exactamente un resultado definitivo» y título «Exactamente un resultado por declaración acusada»; D02 pasa de «reparación» a corrección; en STRIDE, «Reacción» pasa a «Corrección». D04, S01, S03 y S06 siguen como prevención y S04 como detección. Quedan sin ASR, por falta de umbral, la detección de un externo caído y la corrección de un certificado revocado ya aceptado (sección 6).

### 2026-11-06 · Registro de ADR recreado, agrupado por ASR

- Pedido de Nicolás: recrear los ADR agrupados por ASR, sin detalles de construcción de los diagramas.
- Numeración seguida por grupo. D01: ADR-001 pipeline, ADR-002 monitor y ventana nocturna (antes ADR-003), ADR-003 llave de idempotencia (antes ADR-004). L04: ADR-004 carriles (antes ADR-002). S06: ADR-005 a ADR-007 sin cambio. Se renumeraron las referencias en el experimento y en los cinco `.drawio`; esta bitácora conserva los números viejos en las entradas anteriores.
- Insumos al día: medidas nuevas de D01 (0 archivos válidos sin aceptar) y L04 (p99 ≤ 3 s), estímulo de D01 sin duración, artefactos como servicios.
- Tácticas contrastadas con el ArquiBook (caps. 8, 13 y 15, leídos en el navegador): todas las elegidas están en el árbol del libro. Se agregó LAT-12 «Programar los recursos» a ADR-004, y R-002b nombra la verificación de sanidad (DIS-07). El libro llama «recuperación» a la familia que la página de ASR rotula «corrección».
- Lint adr-log/v2: 13 errores, todos de estructura (secciones de catálogos y plan de diagramas que el equipo quitó, y los ADR en §2 en vez de §4). Se aceptan.
- Familias de tácticas (Nicolás): «corrección» pasa a «recuperación», como en el ArquiBook (cap. 13). En seguridad, la fila de STRIDE vuelve a «Reacción» (cap. 15). La convención queda en CLAUDE.md.
- `requirements.md` (Nicolás): HU-06, HU-03 y HU-02 son las tres historias centrales, con criterios completos alineados con los escenarios y los ADR (archivo válido, ventana nocturna, notificación al agotar los días, duplicado; pequeños sin esperar a los masivos; verificación completa, verificación pendiente y evidencia). Las demás quedan con uno o dos criterios. El glosario suma «Archivo válido». La página sigue sin nombrar ASR ni umbrales.
