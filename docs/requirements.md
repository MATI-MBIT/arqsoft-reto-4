---
title: Historias de usuario
nav_order: 6
helix_section: "Requirements & Quality"
---

# Historias de usuario de Orquídea

Las historias siguen los cuatro procesos del enunciado: recibir y verificar; validar y procesar; entregar resultados y datos; y absorber los cambios dejando auditoría. Cada una nombra a un stakeholder de la [lista](stakeholders.md) y dice qué tiene que pasar.

## El árbol completo

| Épica | Funcionalidad | Historias |
|---|---|---|
| E-1 Recepción y verificación de archivos | F-1.1 Envío y acuse | HU-01 |
| | F-1.2 Verificación criptográfica | HU-02 |
| E-2 Validación y procesamiento de declaraciones | F-2.1 Procesamiento según el tamaño del archivo | HU-03 · HU-04 |
| | F-2.2 Validación y enriquecimiento | HU-05 · HU-06 |
| | F-2.3 Continuidad del procesamiento | HU-07 |
| E-3 Resultados y distribución | F-3.1 Resultado al remitente | HU-08 · HU-09 |
| | F-3.2 Entrega a las áreas de BCR | HU-10 |
| E-4 Cambio regulatorio, integración y auditoría | F-4.1 Cambios de la Circular | HU-11 · HU-12 |
| | F-4.2 Cambios de los sistemas externos | HU-13 |
| | F-4.3 Auditoría | HU-14 |

## E-1 — Recepción y verificación de archivos

Recibir los archivos de los remitentes, comprobar que son auténticos y confirmar que BCR los tiene.

### F-1.1 — Envío y acuse

Los remitentes envían sus archivos y saben que BCR los recibió antes de que termine el procesamiento.

**HU-01 — Enviar un archivo y recibir el acuse**

> Como **Intermediario del mercado cambiario (IMC)**, quiero enviar un archivo XML firmado de cualquier tamaño y recibir un acuse de recibo apenas termine el envío, para saber que BCR lo tiene y empezó a procesarlo.

### F-1.2 — Verificación criptográfica

Comprobar la firma, la estampa y el certificado de cada archivo antes de aceptar su contenido.

**HU-02 — Aceptar solo archivos auténticos**

> Como **Auditoría de BCR**, quiero que ningún archivo quede aceptado sin verificar su firma, su estampa cronológica y la vigencia y revocación de su certificado, para que ningún remitente pueda negar después haberlo enviado ni nadie pueda enviarlo a su nombre.

## E-2 — Validación y procesamiento de declaraciones

Validar, enriquecer y transformar cada declaración, sin importar el tamaño del archivo ni el estado de los sistemas externos.

### F-2.1 — Procesamiento según el tamaño del archivo

Los archivos pequeños (1 a 50 registros), medianos (51 a 5 000) y masivos (hasta 100 000) conviven sin estorbarse.

**HU-03 — Resultado de un archivo pequeño sin esperar a los masivos**

> Como **Intermediario del mercado cambiario (IMC)**, quiero que mis archivos pequeños y medianos se resuelvan aunque otros remitentes estén enviando archivos masivos, para no esperar detrás de cargas que no son mías.

**HU-04 — Procesar un archivo masivo**

> Como **Intermediario del mercado cambiario (IMC)**, quiero enviar un archivo de hasta 100 000 registros y recibir el resultado de cada uno, para corregir y reenviar solo los que se rechazaron.

### F-2.2 — Validación y enriquecimiento

Cada declaración se valida contra el esquema y las reglas que regían cuando llegó, y se completa con los sistemas externos.

**HU-05 — Validar con las reglas vigentes al recibir**

> Como **Área de regulación cambiaria de BCR**, quiero que cada declaración se valide con el esquema y las reglas vigentes en la fecha y hora en que llegó su archivo, para que el resultado no dependa de cuándo se procesó.

**HU-06 — Seguir recibiendo con un sistema externo caído**

> Como **Intermediario del mercado cambiario (IMC)**, quiero que mis archivos se sigan recibiendo y procesando aunque STM, ANTA, MAMC, RNIC o la entidad de certificación no respondan, para no tener que reenviarlos cuando vuelvan.

### F-2.3 — Continuidad del procesamiento

El procesamiento sobrevive a sus propias fallas.

**HU-07 — Retomar lo que estaba en curso tras una falla**

> Como **Equipo de TI de BCR**, quiero que, si un nodo interno falla, las declaraciones que tenía en curso se retomen solas, sin perderse ni repetirse, para no reconstruir a mano el estado de cada archivo.

## E-3 — Resultados y distribución

Devolver el resultado al remitente y entregar los datos a las áreas de BCR.

### F-3.1 — Resultado al remitente

El remitente conoce el resultado de lo que envió, y solo eso.

**HU-08 — Recibir el resultado definitivo**

> Como **Intermediario del mercado cambiario (IMC)**, quiero recibir el resultado definitivo de cada archivo, por notificación y por consulta, con el detalle de cada declaración, para conciliar lo que reporté con lo que BCR aceptó.

**HU-09 — Ver y corregir solo lo propio**

> Como **Intermediario del mercado cambiario (IMC)**, quiero consultar, corregir o anular solo las operaciones que yo reporté, para que ningún otro remitente vea ni altere mi información ni la de mis clientes.

### F-3.2 — Entrega a las áreas de BCR

Cada área recibe los datos en el formato que usa.

**HU-10 — Recibir los datos en el formato del área**

> Como **Áreas consumidoras de BCR**, queremos recibir las declaraciones aceptadas en nuestro formato (base analítica, motor de búsqueda o archivo plano), para usarlas sin pedir cambios al procesamiento de Orquídea.

## E-4 — Cambio regulatorio, integración y auditoría

Absorber los cambios de la Circular y de los sistemas externos, y dejar evidencia de todo.

### F-4.1 — Cambios de la Circular

Las reglas y los esquemas cambian sin detener la operación.

**HU-11 — Poner en producción una regla cambiaria nueva**

> Como **Área de regulación cambiaria de BCR**, quiero que una regla cambiaria nueva entre en producción sin detener la recepción, para que rija desde la fecha que fije la Circular.

**HU-12 — Publicar una versión nueva del XSD**

> Como **Área de regulación cambiaria de BCR**, quiero publicar una versión nueva del XSD de las declaraciones y que convivan las dos mientras los remitentes migran, para no rechazar a quien aún no se ha actualizado.

### F-4.2 — Cambios de los sistemas externos

Un cambio en un sistema externo se absorbe en un solo lugar.

**HU-13 — Adaptarse a un contrato de API nuevo**

> Como **Equipo de TI de BCR**, quiero adaptar Orquídea cuando STM, ANTA, MAMC o RNIC cambia su contrato de API, sin tocar el resto del procesamiento, para seguir enriqueciendo las declaraciones con su información.

### F-4.3 — Auditoría

Lo que pasó se puede reconstruir y probar.

**HU-14 — Auditoría inmutable**

> Como **Auditoría de BCR**, quiero que el registro de recepciones, acuses y resultados no se pueda modificar sin que se note, para reconstruir con garantía quién envió qué y cuándo.

## Criterios de aceptación

Dicen qué tiene que pasar para dar la historia por cumplida.

| HU | Criterios |
|---|---|
| HU-01 | El acuse llega para archivos de 1 a 100 000 registros · El acuse dice que el archivo se recibió, no que es válido · Si la verificación del archivo no ha terminado, el acuse lo dice (acuse de custodia) |
| HU-02 | Se verifican la firma, la estampa cronológica, la vigencia y la revocación del certificado de todos los archivos · Un archivo con cualquiera de ellas inválida se rechaza con la causa · Un archivo cuya revocación no se pudo comprobar queda en verificación pendiente y no recibe resultado definitivo · El firmante tiene que ser el mismo remitente autenticado |
| HU-03 | Un archivo pequeño o mediano no espera a que termine un masivo · Se cumple también en horas pico, con archivos masivos en paralelo · Ningún archivo se pierde en la espera |
| HU-04 | El archivo llega a resultado definitivo aunque haya otros masivos en proceso · Cada declaración tiene su propio resultado, con la causa si se rechazó · Las declaraciones válidas se aceptan aunque otras del mismo archivo se rechacen |
| HU-05 | La versión del esquema y de las reglas queda fijada al recibir el archivo · Un archivo nunca se valida con dos versiones distintas · El resultado dice con qué versión se validó |
| HU-06 | Ningún archivo se rechaza porque un sistema externo no responda · El acuse no espera a ningún sistema externo · Las declaraciones que dependen del sistema caído quedan pendientes de enriquecimiento y se completan cuando vuelve · Un sistema externo lento se trata igual que uno caído |
| HU-07 | Las declaraciones en curso se retoman sin intervención manual · Ninguna declaración con acuse emitido se pierde · Ninguna se aplica dos veces |
| HU-08 | El resultado es aceptación total, parcial o rechazo · Trae el resultado de cada declaración y la causa de cada rechazo · Llega en una notificación firmada por BCR y también se puede consultar · Se puede rastrear desde el acuse hasta el resultado |
| HU-09 | La identidad sale de la autenticación y del certificado, nunca de un campo del archivo o de la consulta · Un intento de acceder a operaciones de otro remitente se niega y queda en la auditoría · Las correcciones y anulaciones solo valen sobre operaciones que reportó el mismo remitente |
| HU-10 | Cada área recibe las declaraciones aceptadas en su formato · El dato significa lo mismo en todos los formatos · Agregar un área nueva no cambia la recepción, la validación ni la transformación |
| HU-11 | La regla entra en producción con un despliegue sin indisponibilidad: sin detener la recepción ni el procesamiento · Rige para los archivos recibidos desde su fecha de vigencia · Se puede revertir sin detener la operación · Ningún archivo queda evaluado con dos versiones de la misma regla |
| HU-12 | Los archivos de la versión anterior se siguen validando mientras convivan · Cada archivo se valida con la versión que declara · La versión nueva entra sin detener la operación |
| HU-13 | Los datos del sistema externo se interpretan con el significado que él les da, o se rechazan con diagnóstico · Ninguno se registra con otro significado · El cambio no toca la validación, las reglas ni la transformación · La versión nueva entra sin detener la operación |
| HU-14 | Cada recepción, acuse y resultado queda registrado · Una modificación, un borrado o un cambio de orden se detecta e indica el registro afectado · Ni el equipo de TI puede alterar el registro sin que se note · Se conserva el tiempo que exige la norma |

## De dónde sale cada historia

| HU | Fuente |
|---|---|
| HU-01 | Enunciado, proceso de recepción · BCR fijó desde cuándo se cuenta y el acuse de custodia |
| HU-02 | Enunciado, proceso de recepción y exigencia de no repudio y de prevenir la suplantación |
| HU-03 | Enunciado: el problema de los masivos que bloquean a los pequeños en horas pico |
| HU-04 | Enunciado, proceso de validación · BCR fijó la aceptación parcial por declaración |
| HU-05 | Enunciado: cambios frecuentes de la Circular · BCR fijó la vigencia a la hora de recepción |
| HU-06 | Enunciado: tolerar la caída de sistemas externos y de entidades de certificación sin rechazar archivos |
| HU-07 | Enunciado: recuperarse de fallas de nodos internos sin pérdida de mensajes |
| HU-08 | Enunciado, notificación del resultado · BCR fijó la notificación firmada y la consulta |
| HU-09 | Enunciado: impedir que un IMC consulte o altere operaciones de terceros |
| HU-10 | Enunciado: entregar los datos a las áreas en múltiples formatos |
| HU-11 | Enunciado: desplegar reglas nuevas sin indisponibilidad |
| HU-12 | Enunciado: cambios en los esquemas XML sin indisponibilidad · Supuesto del equipo: las versiones conviven |
| HU-13 | Enunciado: adaptar y reemplazar los conectores con los sistemas externos |
| HU-14 | Enunciado: auditoría inmutable · BCR fijó el tiempo de conservación |

## Preguntas abiertas

- `[PREGUNTA → BCR]` ¿Un titular de cuenta de compensación tiene las mismas historias que un IMC, o alguna cambia? Aquí se asume que sí.
- `[PREGUNTA → BCR]` ¿Las correcciones y anulaciones pueden llegar antes que la declaración que modifican? Si pueden, hace falta una regla de orden.
- `[PREGUNTA → BCR]` ¿Qué pasa con un archivo que se queda en verificación pendiente por mucho tiempo: se rechaza en algún momento o espera indefinidamente?
