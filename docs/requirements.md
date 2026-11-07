---
title: Historias de usuario
nav_order: 6
helix_section: "Requirements & Quality"
---

# Historias de usuario de Orquídea

Las historias siguen los cuatro procesos del enunciado: recibir y verificar; validar y procesar; entregar resultados y datos; y absorber los cambios dejando auditoría. Cada una nombra a un stakeholder de la [lista](stakeholders.md) y dice qué tiene que pasar.

## Las tres historias centrales

En la reunión técnica del 6 de noviembre de 2026, BCR y el equipo centraron el diseño en tres necesidades. Sus historias llevan criterios de aceptación completos; las demás quedan documentadas con criterios breves.

| Historia | Necesidad |
|---|---|
| [HU-06](#hu-06) | Seguir recibiendo sin rechazar archivos cuando un sistema externo no responde. Es el foco |
| [HU-03](#hu-03) | Que un archivo pequeño no espere detrás de los masivos |
| [HU-02](#hu-02) | Que ningún archivo quede aceptado sin verificar su firma por completo |

## El árbol completo

| Épica | Funcionalidad | Historias |
|---|---|---|
| E-1 Recepción y verificación de archivos | F-1.1 Envío y acuse | HU-01 |
| | F-1.2 Verificación criptográfica | **HU-02** |
| E-2 Validación y procesamiento de declaraciones | F-2.1 Procesamiento según el tamaño del archivo | **HU-03** · HU-04 |
| | F-2.2 Validación y enriquecimiento | HU-05 · **HU-06** |
| | F-2.3 Continuidad del procesamiento | HU-07 |
| E-3 Resultados y distribución | F-3.1 Resultado al remitente | HU-08 · HU-09 |
| | F-3.2 Entrega a las áreas de BCR | HU-10 |
| E-4 Cambio regulatorio, integración y auditoría | F-4.1 Cambios de la Circular | HU-11 · HU-12 |
| | F-4.2 Cambios de los sistemas externos | HU-13 |
| | F-4.3 Auditoría | HU-14 |

## E-1 — Recepción y verificación de archivos

Recibir los archivos de los remitentes, comprobar que son auténticos y confirmar que BCR los tiene.

### F-1.1 — Envío y acuse

**HU-01 — Enviar un archivo y recibir el acuse**

> Como **Intermediario del mercado cambiario (IMC)**, quiero enviar un archivo XML firmado de cualquier tamaño y recibir un acuse de recibo apenas termine el envío, para saber que BCR lo tiene y empezó a procesarlo.

### F-1.2 — Verificación criptográfica

**HU-02 — Aceptar solo archivos auténticos**
{: #hu-02}

> Como **Auditoría de BCR**, quiero que ningún archivo quede aceptado sin verificar su firma, su firmante, su estampa cronológica y la cadena y revocación de su certificado, para que ningún remitente pueda negar después haberlo enviado ni nadie pueda enviarlo a su nombre.

## E-2 — Validación y procesamiento de declaraciones

Validar, enriquecer y transformar cada declaración, sin importar el tamaño del archivo ni el estado de los sistemas externos.

### F-2.1 — Procesamiento según el tamaño del archivo

Los archivos pequeños (1 a 50 registros), medianos (51 a 5 000) y masivos (5 001 a 100 000) conviven sin estorbarse.

**HU-03 — Resultado de un archivo pequeño sin esperar a los masivos**
{: #hu-03}

> Como **Intermediario del mercado cambiario (IMC)**, quiero que mis archivos pequeños se resuelvan aunque otros remitentes estén enviando archivos masivos, para no esperar detrás de cargas que no son mías.

**HU-04 — Procesar un archivo masivo**

> Como **Intermediario del mercado cambiario (IMC)**, quiero enviar un archivo de hasta 100 000 registros y recibir el resultado de cada uno, para corregir y reenviar solo los que se rechazaron.

### F-2.2 — Validación y enriquecimiento

**HU-05 — Validar con las reglas vigentes al recibir**

> Como **Área de regulación cambiaria de BCR**, quiero que cada declaración se valide con el esquema y las reglas vigentes cuando llegó su archivo, para que el resultado no dependa de cuándo se procesó.

**HU-06 — Seguir recibiendo con un sistema externo caído**
{: #hu-06}

> Como **Intermediario del mercado cambiario (IMC)**, quiero que mis archivos se sigan recibiendo y terminen aceptados aunque STM, ANTA, MAMC, RNIC o la entidad de certificación no respondan, para no tener que reenviarlos cuando vuelvan.

### F-2.3 — Continuidad del procesamiento

**HU-07 — Retomar lo que estaba en curso tras una falla**

> Como **Equipo de TI de BCR**, quiero que, si un nodo interno falla, las declaraciones que tenía en curso se retomen solas, sin perderse ni repetirse, para no reconstruir a mano el estado de cada archivo.

## E-3 — Resultados y distribución

Devolver el resultado al remitente y entregar los datos a las áreas de BCR.

### F-3.1 — Resultado al remitente

**HU-08 — Recibir el resultado definitivo**

> Como **Intermediario del mercado cambiario (IMC)**, quiero recibir el resultado definitivo de cada archivo, con el detalle de cada declaración, para conciliar lo que reporté con lo que BCR aceptó.

**HU-09 — Ver y corregir solo lo propio**

> Como **Intermediario del mercado cambiario (IMC)**, quiero consultar, corregir o anular solo las operaciones que yo reporté, para que ningún otro remitente vea ni altere mi información ni la de mis clientes.

### F-3.2 — Entrega a las áreas de BCR

**HU-10 — Recibir los datos en el formato del área**

> Como **Áreas consumidoras de BCR**, queremos recibir las declaraciones aceptadas en nuestro formato, para usarlas sin pedir cambios al procesamiento de Orquídea.

## E-4 — Cambio regulatorio, integración y auditoría

Absorber los cambios de la Circular y de los sistemas externos, y dejar evidencia de todo.

### F-4.1 — Cambios de la Circular

**HU-11 — Poner en producción una regla cambiaria nueva**

> Como **Área de regulación cambiaria de BCR**, quiero que una regla cambiaria nueva entre en producción sin detener la recepción, para que rija desde la fecha que fije la Circular.

**HU-12 — Publicar una versión nueva del XSD**

> Como **Área de regulación cambiaria de BCR**, quiero publicar una versión nueva del XSD y que convivan las dos mientras los remitentes migran, para no rechazar a quien aún no se ha actualizado.

### F-4.2 — Cambios de los sistemas externos

**HU-13 — Adaptarse a un contrato de API nuevo**

> Como **Equipo de TI de BCR**, quiero adaptar Orquídea cuando STM, ANTA, MAMC o RNIC cambia su contrato de API, sin tocar el resto del procesamiento, para seguir enriqueciendo las declaraciones con su información.

### F-4.3 — Auditoría

**HU-14 — Auditoría inmutable**

> Como **Auditoría de BCR**, quiero que el registro de recepciones, acuses y resultados no se pueda modificar sin que se note, para reconstruir con garantía quién envió qué y cuándo.

## Criterios de aceptación

Dicen qué tiene que pasar para dar la historia por cumplida, nunca en cuánto tiempo.

### Criterios de las tres historias centrales

**HU-06 — Seguir recibiendo con un sistema externo caído**

- El archivo se recibe y se acusa aunque un sistema externo no responda; el acuse no espera a ninguno.
- Un sistema externo lento se trata igual que uno caído.
- Ningún archivo válido se rechaza por la caída. Válido es el que se habría aceptado con el sistema externo disponible.
- Lo que no se pudo procesar queda pendiente, sin perderse, y se reprocesa en una ventana nocturna a hora fija.
- Si el sistema sigue caído en esa ventana, se reintenta en la del día siguiente.
- Cuando el sistema vuelve, cada archivo válido pendiente queda aceptado en la primera ventana siguiente.
- Si el sistema no vuelve en los días de reintento, se notifica al remitente.
- Un archivo que una regla rechaza se rechaza por la regla, no por la caída.
- Si el remitente reenvía el mismo archivo, el reenvío se rechaza como duplicado y el original sigue su curso; un archivo distinto se recibe.

**HU-03 — Resultado de un archivo pequeño sin esperar a los masivos**

- Un archivo pequeño se resuelve sin esperar a que avancen los masivos que estén en proceso.
- Se cumple con varios masivos en proceso a la vez.
- Que los masivos se acumulen no afecta a los pequeños.
- Un archivo que llega al procesamiento equivocado no se pierde.

**HU-02 — Aceptar solo archivos auténticos**

- Se verifican la firma, el firmante, la estampa cronológica, la cadena del certificado y su revocación en todos los archivos.
- El firmante tiene que ser el mismo remitente autenticado.
- El certificado tiene que encadenar hasta una raíz aceptada por BCR.
- Una estampa con la hora desfasada o demasiado antigua invalida el archivo.
- Lo verificado tiene que ser exactamente lo que se procesa.
- El archivo puede acusarse antes de terminar la verificación, pero no se acepta hasta que termine bien; si falla, se rechaza con la causa.
- Si la revocación no se puede comprobar porque la entidad de certificación está caída, el archivo queda en verificación pendiente y se reprocesa con lo demás pendiente.
- Cada verificación queda como evidencia que el remitente no puede negar y que nadie puede alterar.

### Las demás historias

| HU | Criterios |
|---|---|
| HU-01 | El acuse llega para archivos de cualquier tamaño · Dice si la firma ya se verificó, nunca que el contenido sea válido |
| HU-04 | Cada declaración tiene su propio resultado, con la causa si se rechazó · Las válidas se aceptan aunque otras del mismo archivo se rechacen |
| HU-05 | Un archivo nunca se valida con dos versiones distintas |
| HU-07 | Lo que estaba en curso se retoma sin intervención manual, sin perderse ni aplicarse dos veces |
| HU-08 | El resultado es aceptación total, parcial o rechazo, con la causa de cada rechazo, por notificación firmada y por consulta |
| HU-09 | La identidad sale de la autenticación y del certificado, nunca de un campo del archivo |
| HU-10 | Agregar un área nueva no cambia la recepción, la validación ni la transformación |
| HU-11 | La regla entra y se revierte sin detener la recepción, y rige desde su fecha de vigencia |
| HU-12 | Cada archivo se valida con la versión del XSD que declara |
| HU-13 | Los datos del sistema externo se interpretan con su significado, o se rechazan con diagnóstico |
| HU-14 | Una modificación, un borrado o un cambio de orden del registro se detecta |

## De dónde sale cada historia

| HU | Fuente |
|---|---|
| HU-01 | Enunciado, proceso de recepción · BCR fijó desde cuándo se cuenta y el acuse de custodia |
| HU-02 | Enunciado: no repudio y prevenir la suplantación · Reunión del 6-nov: firmante, cadena y estampa |
| HU-03 | Enunciado: los masivos que bloquean a los pequeños en horas pico · Reunión del 6-nov |
| HU-04 | Enunciado, proceso de validación · BCR fijó la aceptación parcial por declaración |
| HU-05 | Enunciado: cambios frecuentes de la Circular · BCR fijó la vigencia a la hora de recepción |
| HU-06 | Enunciado: tolerar la caída de sistemas externos sin rechazar archivos · Reunión del 6-nov: ventana nocturna, reintento al día siguiente, notificación y duplicados |
| HU-07 | Enunciado: recuperarse de fallas de nodos internos sin pérdida de mensajes |
| HU-08 | Enunciado, notificación del resultado · BCR fijó la notificación firmada y la consulta |
| HU-09 | Enunciado: impedir que un IMC consulte o altere operaciones de terceros |
| HU-10 | Enunciado: entregar los datos a las áreas en múltiples formatos |
| HU-11 | Enunciado: desplegar reglas nuevas sin indisponibilidad |
| HU-12 | Enunciado: cambios en los esquemas XML · Supuesto del equipo: las versiones conviven |
| HU-13 | Enunciado: adaptar y reemplazar los conectores con los sistemas externos |
| HU-14 | Enunciado: auditoría inmutable · BCR fijó el tiempo de conservación |

## Preguntas abiertas

- `[PREGUNTA → BCR]` ¿A qué hora corre la ventana nocturna, y cuántos días se reintenta antes de notificar?
- `[PREGUNTA → BCR]` ¿Qué pasa con un archivo ya notificado si el sistema externo vuelve después: se reprocesa o el remitente lo reenvía?
- `[PREGUNTA → BCR]` ¿Un titular de cuenta de compensación tiene las mismas historias que un IMC? Aquí se asume que sí.
- `[PREGUNTA → BCR]` ¿Las correcciones y anulaciones pueden llegar antes que la declaración que modifican?
