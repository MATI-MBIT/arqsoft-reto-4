---
title: Stakeholders
nav_order: 3
helix_section: "Stakeholders"
---

# Stakeholders de Orquídea

## Quiénes participan

| Nombre | Rol | Descripción |
|---|---|---|
| Intermediario del mercado cambiario (IMC) | Remitente | Banco comercial, corporación financiera o comisionista de bolsa. Envía archivos XML firmados con sus operaciones de divisas y las de sus clientes, recibe el acuse y el resultado, y corrige lo rechazado |
| Titular de cuenta de compensación | Remitente | Persona o empresa que reporta directamente sus propias operaciones, sin pasar por un IMC. Usa el mismo canal y las mismas reglas que un IMC |
| Junta Directiva de BCR | Regulador | Modifica la Circular Reglamentaria Externa, de donde salen las reglas cambiarias, los esquemas de las declaraciones y sus fechas de vigencia |
| Área de regulación cambiaria de BCR | Dueña de las reglas | Traduce cada cambio de la Circular en reglas, esquemas y vigencias concretas, y pide que entren en producción `[SUPUESTO → BCR]` |
| Áreas consumidoras de BCR | Consumidor de datos | Estadística macroeconómica, seguimiento de la balanza cambiaria y control normativo. Reciben los datos transformados en bases analíticas, motores de búsqueda o archivos planos |
| Equipo de TI de BCR | Operador y desarrollador | Desarrolla, despliega y opera Orquídea; implementa los cambios de reglas, esquemas y conectores |
| Auditoría de BCR | Auditor | Reconstruye quién envió qué y cuándo, y exige una auditoría en la que cualquier alteración se detecte, incluso la de quien administra el sistema |
| Sistema de Tasas de Mercado (STM) | Sistema externo | Entrega las tasas de cambio vigentes con las que se valida cada declaración |
| Agencia Nacional Tributaria y Aduanera (ANTA) | Sistema externo | Entrega la información tributaria y aduanera de las entidades que declaran |
| Ministerio de Asuntos Migratorios y Consulares (MAMC) | Sistema externo | Entrega la información de los migrantes que aparecen en las declaraciones |
| Registro Nacional de Identificación Ciudadana (RNIC) | Sistema externo | Verifica la identificación de los ciudadanos nacionales que reportan |
| Entidad de certificación digital (ECD) | Sistema externo | Emite los certificados y las estampas cronológicas, y publica la información de revocación de los certificados |

## Por qué cada uno está en la lista

**Los dos remitentes van por separado.** El informe de consultoría que el equipo entregó a BCR el 3 de noviembre de 2026 los trata como uno, pero no son lo mismo: el IMC reporta operaciones de terceros, sus clientes, y el titular reporta las suyas. Esa diferencia decide quién puede ver y corregir qué.

**La Junta Directiva no escribe las reglas.** Cambia la Circular, que es un texto normativo; alguien en BCR la convierte en reglas que un sistema puede aplicar. El enunciado no nombra a esa área, y se agrega como supuesto porque es quien pide los cambios al equipo de TI.

**Auditoría va aparte del equipo de TI.** Auditoría tiene que poder detectar lo que haga cualquiera con acceso al sistema, incluido el equipo de TI que lo opera. En ese punto, su interés no coincide con el del equipo.

**Los sistemas externos son stakeholders** aunque no sean personas: su contrato de API, su disponibilidad y sus cambios condicionan el diseño, y BCR no los controla.

## Quiénes quedan fuera de la lista

- **Los clientes de los IMC.** Son los declarantes de muchas operaciones, pero no interactúan con Orquídea: su IMC reporta por ellos.
- **Los proveedores de infraestructura.** La nube, la red y los centros de datos quedan fuera del alcance del diseño.
- **El proveedor de identidad.** Orquídea lo usa, pero su diseño y su operación no son parte de este trabajo.
