# arqsoft-reto-4

Proyecto final de Arquitectura de Software (ARTI4109, MATI, Uniandes): la
arquitectura de **Orquídea**, el sistema de operaciones de divisas de BCR.

## Documentación

La documentación vive en [`docs/`](docs/) y se publica como wiki en
**<https://mati-mbit.github.io/arqsoft-reto-4/>**. Todo `.md` que llegue a
`main` dentro de `docs/` se publica con el workflow
[`.github/workflows/pages.yml`](.github/workflows/pages.yml).

`docs/` es la fuente de verdad del reto; Helix, la herramienta del curso, es
una réplica que se carga desde ahí. Las reglas están en [`CLAUDE.md`](CLAUDE.md)
y el procedimiento de réplica, en
[`notas/contrato-de-replica.md`](notas/contrato-de-replica.md).

### Vista previa local

```bash
cd docs
bundle install
bundle exec jekyll serve --livereload
```

Requiere Ruby 3.0 o superior.
