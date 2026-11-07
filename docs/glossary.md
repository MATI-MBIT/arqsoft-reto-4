---
title: Glosario
nav_order: 5
helix_section: "Objective → Glossary"
---

# Glosario de Orquídea

## Actores

**BCR.** Entidad Central de Regulación Financiera y Monetaria. Opera Orquídea.

**IMC.** Intermediario del mercado cambiario: banco comercial, corporación financiera o comisionista de bolsa. Reporta sus operaciones de divisas y las de sus clientes.

**Titular de cuenta de compensación.** Persona o empresa que reporta directamente sus propias operaciones de divisas, sin pasar por un IMC.

**Remitente.** Quien envía un archivo a Orquídea: un IMC o un titular de cuenta de compensación. Se identifica por su autenticación y por el certificado con que firma.

**Declarante.** La persona o empresa que hizo la operación de divisas. En un archivo de un IMC, el declarante de cada registro suele ser un cliente distinto del remitente.

**Áreas consumidoras.** Las áreas de BCR que usan los datos de Orquídea: estadística macroeconómica, balanza cambiaria y control normativo.

## Archivos y declaraciones

**Archivo.** El XML firmado que envía un remitente. Trae entre 1 y 100 000 registros y pesa como máximo 50 MB.

**Declaración.** Cada registro de un archivo: una operación de divisas reportada. Se acepta o se rechaza por sí sola.

**Archivo pequeño, mediano y masivo.** Pequeño, de 1 a 50 registros; mediano, de 51 a 5 000; masivo, de 5 001 a 100 000.

**Acuse de recibo.** La respuesta con que Orquídea confirma que recibió un archivo y empezó a procesarlo. Dice si la firma ya se verificó, pero nunca que el contenido sea válido: eso lo dice el resultado definitivo.

**Acuse de custodia.** El acuse de un archivo cuya verificación criptográfica todavía no termina: confirma que BCR lo tiene guardado, no que la firma sea válida.

**Resultado definitivo.** Lo que Orquídea concluye de un archivo cuando termina de procesarlo: aceptación total, aceptación parcial o rechazo, con el detalle de cada declaración.

**Corrección y anulación.** El envío con que un remitente modifica o deja sin efecto una declaración que ya había reportado.

## Firma y certificados

**Firma digital.** La firma del remitente sobre el archivo. Prueba quién lo envió y que nadie lo alteró después.

**Estampa cronológica.** La marca de tiempo que una entidad de certificación pone sobre el archivo firmado. Prueba cuándo existía.

**ECD.** Entidad de certificación digital acreditada. Emite los certificados de firma y las estampas cronológicas.

**CRL y OCSP.** Las dos formas en que una ECD publica qué certificados revocó: una lista que se descarga (CRL) o una consulta por certificado (OCSP).

**Verificación pendiente.** El estado de un archivo cuya revocación no se pudo comprobar, por ejemplo porque la ECD está caída. Puede tener acuse, pero no resultado definitivo.

**No repudio.** La garantía de que un remitente no puede negar haber enviado un archivo que firmó.

**Archivo válido.** Un archivo que se habría aceptado si todos los sistemas externos hubieran respondido. Sirve para distinguir el archivo que una regla rechaza del que solo espera a un sistema caído.

**Archivo duplicado.** Un archivo que repite uno ya recibido del mismo remitente. Se rechaza como duplicado y el original sigue su curso. Un archivo con otras declaraciones no es duplicado.

**Ventana de reproceso.** La corrida nocturna, a hora fija, que vuelve a procesar lo que quedó pendiente porque un sistema externo no respondía. Si el sistema sigue caído, lo pendiente pasa a la ventana del día siguiente.

## Reglas y esquemas

**Circular Reglamentaria Externa.** La norma de BCR que fija qué se reporta, cómo y con qué reglas. La modifica la Junta Directiva.

**Regla cambiaria.** Una condición que una declaración tiene que cumplir: un tope, la consistencia de la tasa de cambio, quién puede declarar o una vigencia.

**Vigencia.** La fecha desde la cual rige una regla o un esquema. Se aplica la vigente en la fecha y hora en que el archivo llegó.

**XSD.** El esquema que define la estructura válida de un archivo XML. Cambia con la Circular.

**Modelo canónico.** La estructura de datos interna de BCR, a la que se transforma cada declaración antes de guardarla y entregarla a las áreas.

## Sistemas externos

**STM.** Sistema de Tasas de Mercado: las tasas de cambio vigentes.

**ANTA.** Agencia Nacional Tributaria y Aduanera: la información tributaria y aduanera de las entidades.

**MAMC.** Ministerio de Asuntos Migratorios y Consulares: la información de los migrantes.

**RNIC.** Registro Nacional de Identificación Ciudadana: la identificación de los ciudadanos nacionales.

**Contrato de API.** Lo que un sistema externo promete en su interfaz: los campos, sus formatos y lo que significa cada valor. Lo cambia el sistema externo por su cuenta.

**Enriquecimiento.** Completar y validar una declaración con la información de los sistemas externos.

**Pendiente de enriquecimiento.** El estado de una declaración que espera a un sistema externo caído. No se rechaza: se completa cuando el sistema vuelve.

## Operación

**Horas pico.** Los cierres de mercado diarios y los vencimientos regulatorios, cuando llegan muchos más archivos pequeños que en la operación normal.

**Auditoría inmutable.** El registro de recepciones, acuses y resultados en el que cualquier alteración se detecta.

**Despliegue sin indisponibilidad** (*zero downtime*). Poner en producción un cambio sin detener la recepción ni el procesamiento de archivos.
