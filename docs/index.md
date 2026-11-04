---
title: Inicio
layout: home
nav_order: 1
---

# Reto 4 — Arquitectura de Orquídea

Orquídea es la plataforma de BCR por la que los intermediarios del mercado
cambiario reportan todas las operaciones de divisas del país. Llegan archivos
XML firmados de 1 a 100.000 registros, y hoy los masivos bloquean a los
pequeños, cada declaración depende de cuatro sistemas externos y cada cambio de
la Circular Reglamentaria Externa termina en un redespliegue largo.

## Cómo se recorre

| Página | Qué trae |
|---|---|
| [Escenarios de calidad](quality-attributes.md) | Los requisitos que deciden la arquitectura, ordenados por importancia |

Las demás páginas (problema, stakeholders, restricciones, historias, glosario,
experimentos y resultados) se agregan a medida que avanza el reto.

## Cómo agregar o cambiar una página

Todo archivo `.md` que llegue a `main` dentro de `docs/` se publica en este
sitio. Sin front matter, el título sale del primer encabezado `#`. Con front
matter se controla el título y la posición en el menú:

```markdown
---
title: Nombre de la página
nav_order: 10
---
```

Este wiki es la fuente de verdad del reto, y Helix es una réplica que se carga
desde aquí. Un cambio se hace primero en `docs/`.
