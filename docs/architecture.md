---
title: Problema y alcance
nav_order: 2
helix_section: "Objective"
---

# Problema que resuelve la arquitectura de Orquídea

## Enunciado del problema

BCR, la Entidad Central de Regulación Financiera y Monetaria, opera Orquídea, el Sistema de Información de Operaciones de Divisas. Por Orquídea pasan todas las operaciones de divisas del país: importaciones, exportaciones, endeudamiento externo e inversiones internacionales. Esa información alimenta las estadísticas macroeconómicas, el seguimiento de la balanza cambiaria y el control normativo del Estado.

Los intermediarios del mercado cambiario (IMC) y los titulares de cuentas de compensación reportan sus operaciones en archivos XML firmados digitalmente y con estampa cronológica de una entidad de certificación digital acreditada. Un archivo puede traer desde una sola declaración hasta 100 000. Cada uno pasa por la verificación criptográfica, la validación contra los esquemas y las reglas cambiarias, el enriquecimiento con cuatro sistemas externos (STM, ANTA, MAMC y RNIC) y la transformación al modelo canónico de BCR.

Hoy el sistema falla en tres frentes, que tienen la misma raíz: todas las cargas y todas las dependencias comparten un mismo camino.

- **Los archivos masivos bloquean a los pequeños.** En horas pico, un archivo de miles de registros se queda con el procesamiento, y los de pocos registros esperan detrás aunque no tengan nada que ver con él.
- **Orquídea depende en línea de sistemas que BCR no controla.** Son el Sistema de Tasas de Mercado (STM), la Agencia Nacional Tributaria y Aduanera (ANTA), el Ministerio de Asuntos Migratorios y Consulares (MAMC), el Registro Nacional de Identificación Ciudadana (RNIC) y la entidad de certificación. Cualquiera puede caerse o ponerse lento, y su falla se vuelve falla de Orquídea.
- **Cada cambio regulatorio cuesta caro.** La Junta Directiva de BCR modifica con frecuencia la Circular Reglamentaria Externa. Cada cambio de reglas, esquemas o conectores termina en un desarrollo largo y en un despliegue que interrumpe la operación.

A eso se suman dos exigencias que el sistema actual no garantiza. La primera es operar sin interrupción todos los días del año. La segunda es que ningún remitente, el IMC o titular que envía un archivo, pueda consultar, alterar o suplantar operaciones de otro, con una auditoría en la que cualquier alteración se detecte.

## Dentro del alcance

- La recepción de archivos XML firmados de 1 a 100 000 registros, la verificación de su firma, su estampa cronológica y la vigencia y revocación del certificado, y el acuse de recibo.
- La validación de cada declaración contra los esquemas XSD vigentes y las reglas cambiarias, y su enriquecimiento con STM, ANTA, MAMC y RNIC.
- La transformación al modelo canónico de BCR, la persistencia y la entrega de los datos a las áreas de BCR en sus formatos: bases analíticas, motores de búsqueda y archivos planos.
- La notificación del resultado definitivo al remitente: aceptación total, parcial o rechazo, con el detalle por declaración.
- El aislamiento de los datos entre remitentes y la auditoría inmutable de recepciones, acuses y resultados.
- La incorporación de reglas, esquemas y conectores nuevos con despliegues sin indisponibilidad.
- Las pruebas de concepto que validan las decisiones de arquitectura, en un entorno local.

## Fuera del alcance

- **La gestión de usuarios y el formulario de transmisión en línea.** Orquídea se integra con un proveedor de identidad existente y no lo diseña.
- **El contenido de cada regla cambiaria.** Al diseño le interesa poder cambiar las reglas, no su lógica, que es de BCR.
- **Los modelos analíticos y los tableros de las áreas de BCR.** Orquídea les entrega los datos; lo que hacen con ellos es de cada área.
- **La recuperación ante desastres entre regiones.** El enunciado pide tolerar la falla de nodos, no la de una región entera.
- **La validación de identidad de cada ciudadano en cada declaración.** Se asume que la identidad se valida al registrar al actor, como en el Sistema de Información Cambiaria del Banco de la República, el referente real de Orquídea en Colombia `[SUPUESTO → BCR]`.
- **La infraestructura de producción.** Una prueba local no demuestra la alta disponibilidad entre zonas ni el dimensionamiento de servidores; esos aspectos los evalúa un panel de expertos.

## Propósito del diseño

Diseñar la arquitectura que reemplaza al Orquídea actual. Tiene que separar lo que hoy está junto: la recepción del procesamiento, los archivos masivos de los pequeños, Orquídea de los sistemas externos y el código de lo que cambia con la Circular. Las decisiones que lo logren se implementan en pruebas de concepto, para comprobar con datos que cumplen los requisitos de calidad del enunciado.

El dominio es maduro y tiene un referente real: el Sistema de Información Cambiaria del Banco de la República. El referente sirve para llenar los vacíos del enunciado, nunca para contradecirlo. Allí los archivos no se firman y el horario es de días hábiles; el enunciado, en cambio, exige firma con estampa cronológica y operación continua.
