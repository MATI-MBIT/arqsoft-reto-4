---
title: Restricciones
nav_order: 4
helix_section: "Constraints"
---

# Restricciones de Orquídea

Una restricción es una decisión que el diseño no puede cambiar, porque ya la tomó el negocio, la norma o el encargo. No es un requisito de calidad, que deja al arquitecto libre de elegir cómo cumplirlo. Tampoco es una decisión de arquitectura, que sigue siendo del equipo aunque BCR ya la haya aprobado.

## Restricciones del negocio

| ID | Restricción | Origen |
|---|---|---|
| R-1 | Las declaraciones llegan en archivos XML firmados digitalmente y con estampa cronológica de una entidad de certificación digital acreditada | Enunciado |
| R-2 | Un archivo trae entre 1 y 100 000 registros y pesa como máximo 50 MB | Enunciado · BCR |
| R-3 | Cada archivo se valida contra los esquemas XSD vigentes de la Circular Reglamentaria Externa | Enunciado |
| R-4 | Las reglas cambiarias dependen de la información de STM, ANTA, MAMC y RNIC, que son sistemas externos que BCR no puede modificar | Enunciado |
| R-5 | Los datos se transforman al modelo canónico de BCR antes de persistirse, y se entregan a las áreas en varios formatos | Enunciado |
| R-6 | Orquídea opera todos los días del año, a toda hora | Enunciado |
| R-7 | El resultado de un archivo puede ser aceptación total, parcial o rechazo, y cada declaración se acepta o se rechaza por sí sola | Enunciado · BCR |
| R-8 | La regla que se aplica a una declaración es la vigente en la fecha y hora en que el archivo se recibió | BCR |
| R-9 | Los archivos y la auditoría se conservan 90 días en almacenamiento de consulta inmediata y 10 años en almacenamiento que no se puede modificar | BCR, por norma |

## Restricciones de tecnología

| ID | Restricción | Origen |
|---|---|---|
| R-10 | Los datos van cifrados en tránsito y en reposo | Enunciado |
| R-11 | La verificación de firma, estampa y certificado se hace contra la información de revocación que publica la entidad de certificación emisora (CRL u OCSP) | Enunciado |
| R-12 | Las pruebas de concepto corren en un entorno local, con software de código abierto, sin servicios en la nube ni licencias de pago | Decisión del equipo |

## Restricciones del proyecto

No van en Helix, que solo admite restricciones de negocio y de tecnología, pero condicionan la entrega.

| ID | Restricción | Origen |
|---|---|---|
| R-13 | Las decisiones de arquitectura se implementan para validar los requisitos de calidad | Enunciado |
| R-14 | Los requisitos se escriben como escenarios de calidad de seis partes, las decisiones como ADR y las vistas tienen que ser coherentes entre sí | Método del curso |
| R-15 | BCR exige cuatro vistas antes de cerrar la Fase 1 de su hoja de ruta: despliegue y topología de red; dinámica y de concurrencia; información; y contexto | BCR |

## Supuestos

Lo que no dicen el enunciado ni la respuesta de BCR al informe de consultoría (3 de noviembre de 2026), y se asume para poder seguir.

- `[SUPUESTO → BCR]` La identidad de los ciudadanos se valida al registrar al actor, como en el sistema real del Banco de la República, y no en cada declaración.
- `[SUPUESTO → BCR]` STM, ANTA, MAMC y RNIC no aceptan consultas por lote, y las tasas de STM cambian por publicación, no en cada consulta.
- `[SUPUESTO → BCR]` Los remitentes migran a una versión nueva del XSD a ritmos distintos, así que dos versiones pueden convivir un tiempo.
- `[SUPUESTO → BCR]` Una corrección o anulación solo la puede enviar el remitente que reportó la operación original.

## Lo que no es una restricción

**Los 18 ADR que aprobó BCR.** El estilo por etapas asíncronas, la mensajería, la base de datos o la autenticación mutua por certificados son decisiones del equipo de consultores. Que BCR las haya aprobado no las vuelve restricciones: si una prueba de concepto las refuta, se cambian.

**Reemplazar reglas, esquemas y conectores sin afectar el flujo principal.** El enunciado lo pide, pero es un requisito de calidad que se mide, no una decisión ya tomada.

**La reconstrucción del sistema actual** que hizo el informe de consultoría del equipo. Es una hipótesis armada desde los síntomas del enunciado; nadie de BCR la ha validado `[SUPUESTO → BCR]`.
