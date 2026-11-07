# arqsoft-reto-4

Proyecto final de Arquitectura de Software (ARTI4109, MATI, Uniandes): la
arquitectura de **Orquídea**, el sistema de operaciones de divisas de BCR.

## Prototipo del experimento

El código del experimento de los tres ASR críticos (ASR-D01, ASR-L04 y ASR-S06)
vive en este repositorio: los servicios en `services/`, la topología en
`deploy/`, la carga y el plan de ejecuciones en `load/`, y el veredicto en
`analisis/`. Requiere Docker, Java 21, k6, Python 3 y `make`.

```bash
make up           # genera la PKI de prueba, compila, levanta todo y espera a los servicios
make tablero      # abre los tableros de Grafana de D01, L04 y S06
make humo         # humo de ~12 min, los tres ASR de punta a punta, con veredicto
make experimento  # el plan de decisión completo (d01, l04 y s06)
make help         # todos los comandos
```

Qué se corre está en [`load/plan.tsv`](load/plan.tsv), una fila por ejecución
con su escenario; cómo se corre, en [`load/experimento.sh`](load/experimento.sh).
Cada ejecución deja en `analisis/resultados/<ejecución>/` el veredicto, los
eventos crudos, la salida de k6 y el enlace al tablero fijado en su ventana.
El diseño, los supuestos y lo que se construyó están en
[`notas/plan-implementacion-experimento.md`](notas/plan-implementacion-experimento.md).

Corre conectado a la corriente: con batería el orquestador no arranca, porque
una suspensión del equipo contamina la medición.

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
