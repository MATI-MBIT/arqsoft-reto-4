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
- Archivo notificado (Nicolás): al agotar los días de reintento se notifica al remitente, pero el archivo sigue pendiente y se reprocesa cuando el externo vuelve; el remitente no tiene que reenviarlo. Ajustados la respuesta de D01, HU-06, la decisión de ADR-002 y el escenario D-3 del experimento.
- Experimento (Nicolás): reorganizado por ASR, con hipótesis igual a la medida vigente, tácticas, escenarios, métricas y criterio en cada bloque. D01: la medida es 0 archivos válidos sin aceptar (D-1 a D-4) y D-3 comprueba que lo notificado se acepta al regresar el externo. L04: L-1 es la corrida con 5 masivos (p99 ≤ 3 s) y L-2 la línea base del 10 %. S06: la ECD caída pasa de D-6 a S-2 y la evidencia a S-3.
- ADR de detección (Nicolás): D01 es de recuperación, y sin detección no hay recuperación ni forma de simularla. El antiguo ADR-002 se divide en ADR-002 «Detección de terceros caídos, lentos o de vuelta» (DIS-01 ping/echo, DIS-03 monitor, DIS-04 timeout; descarta solo-timeout y heartbeat, KO por R-4) y ADR-003 «Pendientes durables y ventana nocturna» (DIS-12, DIS-14). Los demás corren un número: idempotencia ADR-004, carriles ADR-005, verificación ADR-006, revocación ADR-007, evidencia ADR-008. El experimento separa la detección (D-4 y la métrica de detección de caída y regreso).
- Patrones (Nicolás): la columna «Patrón» de A2 se llena con el patrón de la ficha de la táctica o, sin ficha, con el «Debe verse» del catálogo. Se corrigieron DIS-03 (Monitor), DIS-04 (Exception detection) y SEG-18 (Security Logger and Auditor), y se llenaron los ocho que tenían «—».
- Nombres (Nicolás): en las tablas de tácticas, «Táctica (curso)» lleva el nombre exacto del catálogo, sin descripciones agregadas, y «Patrón» el nombre canónico en inglés (ficha o Bass), sin traducciones. El título de ADR-002 dice «ping/echo» en vez de «sondeo».
- D01, medida final (Nicolás): «100 % de los archivos válidos procesados dentro de los días de reintento». Se propagó a la tabla resumen, al registro de ADR (§1.2, A1, contextos de ADR-001, ADR-003 y ADR-007, A5 de ADR-003), al experimento (D-3 queda fuera de la medida) y a los diagramas.
- Fichas sin «Por qué es ASR» (Nicolás): se quitó la fila de las 17 fichas y la mención a la «nota de trampa» de la introducción.
- Vistas recreadas desde cero por un agente que solo leyó los ADR, las fichas críticas, el experimento y el glosario. Componentes eclosionados: Recepción, Integración con terceros y Verificador criptográfico. Lint: 0 ERROR en la copia combinada de 15 páginas; DG-CMP-002 queda con 16 cruces tras agotar el tope. Puerta 3 con PNG de draw.io: entre 16 y 20 puntos por página.
- Pulido editorial de la página de ASR (escribir-y-pulir-textos, con veredicto de revisar-texto por otra mano: DEFECTOS = 27 sobre la versión previa, de los que 12 quedaron sin objeto al quitar «Por qué es ASR»). Cerrados: andamiaje de M01, premisa de I01, párrafo de los 3 s junto a L04, frase larga de D01 con la `[PREGUNTA]` de los días, verificación de D01 alineada con la medida, «equipo de TI» en I01, nombre completo del servicio de consulta, «resultado definitivo» en L04, medida de D03 en positivo, antecedente de I02 y «carga de A». Quedan abiertos para el equipo: «validación» frente a «verificación» criptográfica en los títulos de S01 y S06, qué es la Fase 4 de BCR y cuál es la presión real según el informe (E01).

### 2026-11-07 · Prototipo del experimento construido

- Pedido de Nicolás: entender la estrategia de los experimentos de los retos 1 y 2 y construir el experimento y las pruebas de los tres ASR críticos, con observabilidad para capturar la evidencia.
- Se heredó la estructura: monorepo Gradle, `load/plan.tsv` + un solo orquestador, registro de eventos como fuente del veredicto, veredicto en SQL, tableros de Grafana generados por script y chequeo de validez por ejecución.
- Servicios: recepción (custodia, verificación en streaming, llave, evidencia, enrutador), carril A y carril B (misma imagen), monitor de terceros, reprocesador de la ventana nocturna, y simuladores del RNIC, de la ECD y de los remitentes. Kafka KRaft, PostgreSQL y Toxiproxy para cortar o hacer lento al tercero.
- La firma del prototipo es envuelta sobre bytes (ECDSA), no XMLDSig con canonicalización (P-2). El costo de las reglas cambiarias se simula con 1 ms de CPU por declaración (P-1).
- Prueba unitaria del camino criptográfico: los nueve casos del catálogo se rechazan con su causa.
- Humo del 2026-11-07 (16:00 a 16:11): los tres ASR pasan; ejecuciones válidas.
- La primera prueba manual de un masivo midió 10 min en vez de 105 s porque el equipo se suspendió: el orquestador invalida toda ejecución con suspensión.
- Plan de decisión del 2026-11-07 (16:11 a 18:46, ciclo `20261107-161130`): 13 ejecuciones, todas válidas (hueco máximo 1,5 s, sin suspensiones, 0 % de fallos en k6). Resultados en `docs/results.md`.
  - ASR-D01 pasa en D-1, D-2, D-4 y D-5 (100 % de los válidos); D-3 notifica 2 437 y los procesa al volver. Detección de 0,2 a 0,5 s con corte y 1,4 s con lentitud.
  - ASR-L04 pasa: p99 de 491 ms con 5 masivos, 482 ms sin masivos (+1,9 %), de 468 a 478 ms con 6, 8 y 10. Sin carriles (L-3), 118 s.
  - ASR-S06 pasa: 0 de 27 del catálogo aceptados y 0 de 2 048 aceptados antes de verificar con la ECD caída.
  - Hallazgo: en el carril B los masivos esperan turno por hilo (partición por llave); uno de cada tanda pasa la referencia de 5 min. Se propone repartirlos entre hilos libres.
  - El inyector anotaba el restablecimiento después de aplicarlo: el regreso de D-4 quedó en −8 ms. Corregido para las ejecuciones siguientes.

### 2026-11-07 · Evidencia visual de los resultados

- Pedido de Nicolás: resultados concretos, dinámicos y claros, con pantallazos y videos como en el reto 2, validando que cada video cuente lo que se prueba.
- Por ASR, en `docs/results.md`: foto testigo anotada de la ejecución de decisión, «Cómo leerla», «Qué ocurre» con números que casan con la foto, y un clip time-lapse plegado.
- Los clips se graban con `load/evidencias.sh grabar` y el renderizador de Grafana en el servidor; se validan sacando cuadros con `analisis/evidencia/cuadros.swift`.
- El primer clip sin carriles de L04 contaba lo contrario del veredicto (p99 de 597 ms, 0 sobre 3 s) porque la pantalla solo contaba los pequeños resueltos. Se corrigió `ev-l04` para contar los que esperan con su espera hasta ahora y se volvió a grabar.

### 2026-11-07 · Revisión de tensiones internas: entrada liviana con claim check

- Tensión detectada: ADR-005 promete aislamiento «por construcción», pero la recepción (verificación en streaming del masivo, ADR-006) y la salida (un solo Kafka y un solo PostgreSQL, compartidos con el reprocesador) son comunes a los dos carriles. Los carriles aíslan solo el procesamiento.
- Claim check (equipo): la recepción guarda los bytes en un almacén de custodia mientras llegan y calcula el hash al vuelo; a la cola va solo una ficha JSON, no el archivo. Encolar no depende del tamaño, y el acuse cabe en el segundo desde el último byte.
- Ficha JSON: `idArchivo`, `ubicacion`, `tamanoBytes`, `carril`, `hashSha256` (de los bytes custodiados, no del mensaje), `tUltimoByte` (t0 de L04), `llave` y `versionMensaje`. Se descartó `correlacion`, porque `idArchivo` ya cumple ese papel.
- Se enruta por el tamaño del archivo (`tamanoBytes`), no del mensaje. La recepción publica directo en `carril-a` o `carril-b`.
- Protocolo de carga (equipo): HTTPS en la arquitectura y HTTP en el experimento local. El t0 es el fin de la petición y el acuse va en la respuesta (`202 Accepted` con el `idArchivo`). Se descartó SFTP: el último byte es ambiguo y el acuse necesitaría otro canal. Falta `[PREGUNTA → BCR]`: ¿los remitentes usan hoy SFTP?
- Llave del archivo (propuesta, por discutir con el equipo): sirve para la idempotencia, pero su valor (entidad + fecha + lote + consecutivo) lo pone el remitente dentro del XML, así que no es confiable hasta verificar la firma. Principio: por rendimiento, nunca se procesan ni se verifican dos archivos con la misma llave. La llave se **aparta** en la entrada y se **confirma o libera** en el carril:
  - Entrada: se lee la llave del encabezado, en los primeros kilobytes, y se **aparta** con restricción de unicidad. Si ya está apartada o confirmada, el archivo se rechaza con causa («duplicado de la llave X», `409 Conflict`) y se corta la subida: no recibe acuse, no se guarda, no se encola ni se verifica. Solo el primero recibe acuse.
  - Carril: si la firma es válida, la llave se **confirma**. Si la firma falla, se **libera**, y el remitente puede corregir y reenviar con la misma llave.
  - Siempre gana el archivo que llegó primero; no hay carrera entre carriles.
  - Archivo vacío o con encabezado ilegible: no hay llave que apartar; se rechaza en la entrada con su causa.
  - Riesgo asumido en el backend: un archivo con encabezado legible pero contenido basura o firma inválida aparta la llave mientras se verifica (de segundos a minutos; hasta la ventana nocturna si la ECD está caída). El carril la libera al fallar la verificación. En producción, el TLS mutuo (ADR-13 de BCR) permite además exigir que la entidad de la llave coincida con la del certificado del canal; en el experimento no aplica (R-14).
  - Las repeticiones internas (Kafka al menos una vez, reproceso nocturno) se controlan con `idArchivo` + número de declaración, que es nuestro.
  - La ficha JSON suma el campo `llave`, para que el carril la confirme sin volver a leer el encabezado.
  - SUP-09 se amplía: el XSD trae los cuatro campos **en el encabezado**. Falta `[PREGUNTA → BCR]`.
  - Cierra la tensión de ADR-004 frente a S06: la llave ya no queda ocupada por un archivo que no pasó la verificación.
- Escritura en la base, camino feliz (equipo): los dos carriles son procesos separados (ADR-005), cada uno leyendo su cola y dimensionado a su modo (A: muchos trabajadores livianos; B: pocos con mucha memoria y CPU). La base sigue siendo **una sola, central**. Para que el masivo no frene al pequeño al escribir:
  - **Bulkhead en las conexiones** (DIS-22, extendido de los hilos a las sesiones): cada carril con su propio pool y un tope. El tope del carril B es el que protege al A.
  - **Batching solo en el carril B**: los resultados del masivo se escriben en lotes grandes (p. ej. 1 000). Tiene 5 min (L03), así que no le afecta. El carril A no usa batching: armar un lote obliga a esperar y se come los 3 s; un pequeño ya escribe sus 1 a 50 resultados en una sola transacción.
  - Táctica de Bass para el batching: reducir la sobrecarga (*reduce overhead*). Falta el ID exacto en el catálogo del curso.
  - Descartado por ahora **CQRS**: separa lecturas de escrituras, y el choque es entre escrituras. Queda como evolución, o una réplica de solo lectura, cuando crezcan las consultas de estado de los remitentes (todo acuse es de custodia).
  - Alternativa con disparador: **una base por carril con consolidación por CDC** (Database per Service + Debezium). Solo si L-1 muestra que la base frena a los pequeños. La tabla de llaves seguiría central.
- Procesamiento por registro y camino triste de un externo caído (equipo):
  - Las cuatro validaciones (STM, ANTA, MAMC, RNIC) son tareas independientes; el orden no importa (equipo). Se consultan **en paralelo** por cada registro: el tiempo es el del externo más lento, no la suma.
  - **Scatter-Gather** (equipo, patrón de integración empresarial): un **agregador** por carril lee la ficha de su cola, reparte una solicitud por registro a la cola de cada externo, y un **trabajador por externo** la consume, llama al externo y devuelve la respuesta a una cola de respuestas. El agregador acumula en memoria las cuatro respuestas del registro y lo escribe una vez en la base.
    - **Colas por externo y por carril** (`stm-a`, `stm-b`, `anta-a`, … `rnic-b`): si fueran compartidas, la solicitud de un pequeño quedaría detrás de las 100 000 de un masivo y se rompería el aislamiento de L04. Por lo mismo, las respuestas también van por carril (`respuestas-a`, `respuestas-b`). Los cuatro trabajadores son compartidos por los dos carriles, con consumidores separados para `-a` y `-b`.
    - Diagrama de la propuesta: `notas/propuesta-concurrencia.drawio` (no reemplaza a DG-CON-001 hasta que el equipo lo apruebe). Queda por decidir cómo llama el reprocesador al externo pendiente: por el trabajador de ese externo o con una cola propia.
    - **Cada trabajador controla el ritmo hacia su externo** (N llamadas por segundo o simultáneas): ataca el volumen de llamadas que los externos no aguantan. El monitor y el timeout de ese externo (ADR-002) viven en su trabajador. Si el externo está caído, el trabajador responde «pendiente» sin llamarlo.
    - **Timeout en el agregador**: si una respuesta no llega a tiempo, escribe lo que tiene y deja ese externo pendiente.
    - **Correlación por `idArchivo` + registro** en cada solicitud y respuesta. Si el agregador cae, pierde lo acumulado: al reiniciar, Kafka le entrega la ficha otra vez, se salta lo ya escrito y reparte de nuevo lo que estaba en curso. Las respuestas huérfanas se descartan. Repetir una consulta no hace daño.
    - Descartado **Valkey** como almacén de estado temporal: solo evitaba repetir consultas cuando un proceso cae, que es raro e inofensivo. Se aceptan las consultas repetidas.
    - Descartadas las llamadas en serie: suman los cuatro tiempos (~1,6 s contra ~0,4 s) y no evitan el problema del estado en curso.
    - Alternativa considerada: llamadas directas desde el carril con un límite por externo (Bulkhead por externo), sin colas. Más simple y rápida; se prefirió Scatter-Gather por el control de ritmo y el desacople por externo (también sirve a I01).
    - Riesgos por medir en L-1: latencia de dos pasos por cola por externo en el camino de los 3 s, y unos 800 000 mensajes por masivo (400 000 de ida y 400 000 de vuelta).
  - **PostgreSQL se escribe una vez por registro**: con el resultado completo, o con el avance parcial y lo que quedó pendiente si un externo falló. En el carril B, en lotes. Nunca queda un registro a medias por la caída de un carril.
  - **El estado es por registro**, no por lote. El estado del archivo se deduce: está resuelto cuando todos sus registros lo están.
  - **Una cola de pendientes por externo** (`pendientes-stm`, `pendientes-anta`, `pendientes-mamc`, `pendientes-rnic`), con un aviso por archivo y externo, no por registro. Cuando vuelve un externo solo se consume su cola, con su propio ritmo (Queue-Based Load Leveling por externo). Un registro pendiente de dos externos avanza con cada uno que vuelva.
  - **No se reinicia desde cero**: en la noche, el reprocesador (proceso aparte) lee de la base qué falta y llama solo al externo pendiente. R-8 lo hace correcto: lo ya guardado vale, porque la regla es la vigente al recibir. Falta `[PREGUNTA → BCR]`: ¿los externos permiten consultar por fecha (p. ej., la tasa del STM del día de recepción)?
  - **Recuperación de un carril caído**: el agregador confirma el offset en Kafka solo al terminar el archivo. Si cae, Kafka le entrega la ficha otra vez; se salta los registros ya escritos (llave `idArchivo` + registro) y vuelve a repartir los que estaban en curso. La verificación de la firma queda como estado del archivo en la base, para no repetirla: al retomar solo se comprueba el hash contra el almacén.
  - La base de datos es la única fuente de verdad duradera; la memoria del agregador es un borrador de segundos; las colas solo llevan trabajo y avisos.
- En discusión, sin aplicar: el resto del archivo mientras una declaración espera (¿resultado parcial ya o todo en la noche?), el tiempo de detección y el timeout frente a los 3 s de L04, la espera hasta la noche aunque el externo vuelva antes (SUP-10), verificar la firma en el carril (todo acuse de custodia), la evidencia de los archivos verificados tarde (ADR-008), el timeout de los adaptadores frente a los 3 s de L04 y un respaldo de finalización (mover el archivo al llegar a resultado definitivo, no al leerlo; cuadrar con R-9).
- Por ajustar cuando cierre la discusión: ADR-001 (claim check, HTTPS, almacén de custodia), ADR-004 (apartar en la entrada, confirmar o liberar en el carril, dos llaves), ADR-002 (monitor y timeout en el trabajador de cada externo), ADR-003 (colas de pendientes por externo, avance guardado por registro, ritmo por externo), ADR-005 (enrutar por `tamanoBytes`, riesgo del almacén compartido, Bulkhead en conexiones, batching en B), un ADR nuevo para Scatter-Gather (agregador, trabajadores por externo, colas por externo y carril), ADR-006 (verificación en el carril) y el montaje del experimento. Los diagramas los revisa el equipo aparte.

### 2026-11-07 · Verificación pendiente: un solo tópico y la publica quien llama

- Incoherencia de la vista de componentes: el Verificador publicaba «verificación pendiente», pero no había tópico para eso, y `pendientes` decía «tercero caído». El prototipo ya lo resolvía de otra forma.
- Decisión (alineada con el prototipo): **un solo tópico `pendientes`**, con un campo `motivo` (verificación pendiente o tercero caído). Los dos casos los atiende el mismo reprocesador en la misma ventana, así que un segundo tópico no aporta.
- **El Verificador devuelve un veredicto** (válido, inválido o pendiente) y no publica en el bus. La Recepción publica el pendiente, porque tiene la llave, la ruta en la custodia y el carril. El reprocesador, al reverificar, reprograma o publica el archivo en su carril.
- Aplicado en `vista-concurrencia.drawio`: DG-CON-001 (recepción → `pendientes`, reprocesador → `carril-a` y `carril-b`, `motivo` en el tópico, nota ADR-007), DG-SEQ-001 (`alt [verificado] / [pendiente]` tras el commit) y DG-SEQ-003 (el Verificador devuelve «veredicto: pendiente»).
- Queda para quien lleve `vista-componentes.drawio`: rótulo de `pendientes` en DG-CMP-003, la flecha e15 sale de la Recepción y no del Verificador, y en DG-CST-003 se quita el puerto `pPendientes` del Verificador.

### 2026-11-07 · Monitor de terceros en la vista de concurrencia

- Validación de procesos contra el prototipo (`deploy/docker-compose.yml`): los procesos propios son recepción, carril A, carril B, monitor de terceros y reprocesador. A DG-CON-001 le faltaba el monitor, que es objeto activo porque sondea por su cuenta. No falta ningún otro: el Verificador y el Registro de evidencia corren dentro de la recepción, Resultados dentro de los carriles, y la Entrega a áreas está fuera del foco.
- Agregados a DG-CON-001: `monitor : Monitor de terceros` (DIS-01, ping/echo), el tópico `estado-terceros` y las lecturas de los dos carriles y del reprocesador, más una nota de ADR-002. La trazabilidad de ASR-D01 suma el monitor (artefacto) y `estado-terceros` (participa).
- Los carriles pasan de «thread» a «process»: en el prototipo son contenedores separados (ADR-005: pool y despliegue propios).
- Diferencia por resolver con el prototipo: allí el monitor no publica en un tópico. Los clientes le piden el estado por HTTP cada 200 ms y le reportan las llamadas vencidas. La vista de componentes y la de concurrencia dicen tópico `estado-terceros`. Toca decidir si el tópico es la arquitectura y el HTTP es una simplificación del experimento, o al revés.
- DG-CON-001 rehecha con la notación de la propuesta (pedido de Nicolás): mensajes numerados por hilo (1.x recepción, A y B carriles, M monitor, R reprocesador), un círculo con número en cada pieza y el panel «¿Para qué sirve cada pieza?». Contenido del equipo: suma la ECD, el registro de evidencia y los terceros como objetos del entorno, y `resultado : ResultadoDeclaracion` pasa a `repositorio : RepositorioDeArchivos`, como en la vista de componentes. Las páginas de secuencia no cambian.
- El Orquestador de enriquecimiento no es un proceso: es un «part» que corre dentro de quien lo llama. En el prototipo, `Procesador` y `AdaptadorRnic` (librería `comun`) tienen una copia por proceso: carril A, carril B y reprocesador. En DG-CON-001 queda como restricción `{orquestador y adaptadores en este proceso}` en los tres. Con esto se cierra el punto 3: el Orquestador no rompe el aislamiento de L04 en ejecución. Lo que sí comparten los carriles es el tercero: un masivo que lo vuelve lento afecta al pequeño, que el timeout manda a pendiente sin que se quede esperando.

### 2026-11-07 · Consultas a los externos: en serie y, si falla una, se repiten todas

- Decisión (Nicolás): las cuatro consultas de una declaración (STM, ANTA, MAMC, RNIC) van **en serie**. Si una falla (tercero caído o timeout), la declaración queda pendiente **entera** y en el reproceso se vuelve a consultar a los cuatro desde cero. Razón: la información de los externos puede cambiar por decisión del gobierno, así que no se mezclan datos obtenidos en momentos distintos.
- Cierra los puntos abiertos 1 (no se conserva el avance parcial) y 2 (no van en paralelo). Simplifica: un solo tópico de pendientes por declaración, sin avance por externo, como ya lo tiene el equipo.
- Consecuencia para L04: en serie, los tiempos se suman. Con el timeout del prototipo (1 s por llamada), cuatro externos lentos dan hasta 4 s, más que los 3 s del pequeño. El timeout de cada adaptador tiene que caber en el presupuesto: por ejemplo, ≤ 600 ms cada uno. Liga con el punto abierto del timeout.
- Antes de la primera llamada se mira el estado de los cuatro en el monitor: si alguno está caído, la declaración va a pendientes sin llamar a ninguno.
- No choca con R-8: R-8 fija la **regla** vigente al recibir, no los datos de los externos.
- Tasa del STM en el reproceso (Nicolás): **la del momento de la consulta**, no la del día de recepción. Es coherente con repetir las cuatro consultas: todos los datos de una declaración salen del mismo momento. Consecuencia: una declaración reprocesada de noche puede dar un resultado distinto del que habría dado en el día. Se deja de preguntar a BCR si los externos permiten consultar por fecha.
- Orden de las consultas: indiferente por ahora (Nicolás); se dibuja STM → ANTA → MAMC → RNIC.
- Aplicado en `vista-concurrencia.drawio`: DG-CON-001 (A3.1→A3.4 y B3.1→B3.4 en serie con timeout por llamada, A5/B5 «declaración entera», R3 «los 4 de nuevo, desde cero», restricción en los workers y tarjetas) y DG-SEQ-002 (`loop [para cada tercero]` dentro de `[los 4 disponibles]`, `[alguno caído]` sin llamar a ninguno, el timeout sale del loop, nota de la suma de tiempos frente a L04 y nota de ADR-003 ajustada).
- Claim Check señalado en `vista-concurrencia.drawio` (pedido de Nicolás): nota en DG-CON-001 junto a los carriles y `{patrón = Claim Check}` en la recepción; los mensajes 1.4, 1.5, A1, B1 y R5 llevan «ficha» en vez de «archivo»; nota en DG-SEQ-001 junto al publicador. La ficha del prototipo: llave, ruta en la custodia, tamaño, registros y carril (`Cola.ArchivoEnCola`).
- DG-SEQ-002: se agrega `reportarVencida(tercero)` del controlador de degradación al estado de terceros, rumbo al monitor, cuando vence el timeout de una llamada (ADR-002: la llamada vencida cuenta como caída). El prototipo lo hace con `MonitorCliente.reportarVencida`.
