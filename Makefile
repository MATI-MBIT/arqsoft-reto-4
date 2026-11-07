# ==============================================================================
# arqsoft-reto-4 — prototipo del experimento de Orquídea (ASR-D01, ASR-L04, ASR-S06)
#
# QUÉ se corre vive en load/plan.tsv, una fila por ejecución con el escenario al
# que sirve; CÓMO se corre, en load/experimento.sh. Aquí solo hay atajos.
# El veredicto sale del registro de eventos (analisis/*.sql); Grafana sirve para
# ver la ejecución mientras pasa y para mostrar la evidencia después.
# ==============================================================================

COMPOSE := docker compose -f deploy/docker-compose.yml
PSQL    := $(COMPOSE) exec -T postgres psql -U reto4 -d reto4
PUERTOS := 8081 8082 8083 8084 8085 8090 8091 8092

.DEFAULT_GOAL := help

##@ Experimento — lo que valida las hipótesis

.PHONY: plan
plan: ## Lista las ejecuciones del plan y a qué pregunta responde cada una
	@printf "\n  \033[1m%-8s %-9s %-4s %-5s %-8s %s\033[0m\n" grupo id asr esc criterio pregunta
	@awk -F'\t' '!/^#/ && NF>3 {printf "  %-8s %-9s %-4s %-5s %-8s %s\n", $$1,$$2,$$3,$$4,$$5,$$7}' load/plan.tsv
	@printf "\n  decisión: make experimento  ·  un grupo: make grupo G=d01  ·  humo: make humo\n\n"

.PHONY: humo
humo: up ## Humo de ~10 min: los tres ASR de punta a punta, con veredicto
	./load/experimento.sh humo

.PHONY: rapido
rapido: up ## Los 12 escenarios comprimidos, para probar cada cambio
	./load/experimento.sh rapido

.PHONY: d01
d01: up ## ASR-D01 de decisión: D-1 a D-5
	./load/experimento.sh d01

.PHONY: l04
l04: up ## ASR-L04 de decisión: L-1 a L-4
	./load/experimento.sh l04

.PHONY: s06
s06: up ## ASR-S06 de decisión: S-1 a S-3
	./load/experimento.sh s06

.PHONY: experimento
experimento: up ## El plan de decisión completo: D01, L04 y S06
	./load/experimento.sh d01 l04 s06

.PHONY: grupo
grupo: up ## Un grupo del plan: make grupo G=d01
	@test -n "$(G)" || { echo "falta G. Grupos:"; awk -F'\t' '!/^#/ && NF>3 {print "  "$$1}' load/plan.tsv | sort -u; exit 1; }
	./load/experimento.sh $(G)

.PHONY: veredicto
veredicto: ## Recalcula el veredicto de una ejecución: make veredicto CORRIDA=d1-20261107-120000
	@test -n "$(CORRIDA)" || { echo "falta CORRIDA. Ejecuciones:"; $(PSQL) -qtA -c "SELECT id FROM registro.corrida ORDER BY arranque DESC LIMIT 20"; exit 1; }
	@asr=$$($(PSQL) -qtA -c "SELECT lower(asr) FROM registro.corrida WHERE id = '$(CORRIDA)'"); \
	  test -n "$$asr" || { echo "no existe la ejecución $(CORRIDA)"; exit 1; }; \
	  $(PSQL) -v corrida=$(CORRIDA) -f /analisis/$$asr.sql

.PHONY: corridas
corridas: ## Lista las ejecuciones registradas, con su ventana de medición
	@$(PSQL) -c "SELECT id, asr, escenario, valida, inicio, fin, fin - inicio AS medicion FROM registro.corrida ORDER BY arranque DESC"

.PHONY: graficas
graficas: ## Regenera las gráficas de evidencia (SVG) desde el registro de eventos
	python3 analisis/graficas.py

##@ Observabilidad — la evidencia en vivo

.PHONY: tablero
tablero: ## Abre los tableros de Grafana de D01, L04 y S06
	@echo "D01 http://localhost:3000/d/d01 · L04 http://localhost:3000/d/l04 · S06 http://localhost:3000/d/s06"
	@open http://localhost:3000/d/d01 2>/dev/null || true

.PHONY: tableros
tableros: ## Regenera los JSON de los tableros desde deploy/observabilidad/generar-tableros.py
	python3 deploy/observabilidad/generar-tableros.py
	$(COMPOSE) restart grafana

.PHONY: estado
estado: ## Salud de cada servicio y estado de los terceros según el monitor
	@for p in $(PUERTOS); do printf "  :%s  " $$p; curl -s -m 2 localhost:$$p/actuator/health || printf "sin respuesta"; echo; done
	@printf "  terceros: "; curl -s -m 2 localhost:8084/estado; echo

##@ Fallas a mano — para probar el montaje, no para medir

.PHONY: cortar
cortar: ## Corta un tercero: make cortar T=rnic
	docker exec reto4-inyector inyectar cortar $(or $(T),rnic)

.PHONY: lentitud
lentitud: ## Agrega latencia a un tercero: make lentitud T=rnic MS=3000
	docker exec reto4-inyector inyectar lentitud $(or $(T),rnic) $(or $(MS),3000)

.PHONY: restablecer
restablecer: ## Restablece un tercero: make restablecer T=rnic
	docker exec reto4-inyector inyectar restablecer $(or $(T),rnic)

.PHONY: ventana
ventana: ## Dispara la ventana nocturna de reproceso
	@curl -s -X POST localhost:8085/ventanas; echo

.PHONY: enviar
enviar: ## Envía un archivo: make enviar TIPO=masivo CASO=valido
	@curl -s -X POST "localhost:8090/enviar?tipo=$(or $(TIPO),pequeno)&caso=$(or $(CASO),valido)&corrida=manual-$$(date +%H%M%S)"; echo

##@ Topología

.PHONY: pki
pki: ## Genera la PKI de prueba en deploy/pki (no se versiona)
	@test -f deploy/pki/raiz.crt || ./gradlew --console=plain -q :services:pki:run --args="$(CURDIR)/deploy/pki"

.PHONY: up
up: pki imagenes ## Levanta todo y espera a que los servicios respondan
	$(COMPOSE) up -d
	@printf "esperando a los servicios"; for p in $(PUERTOS); do \
	  for i in $$(seq 1 120); do curl -fs -m 1 localhost:$$p/actuator/health >/dev/null && break; printf "."; sleep 1; done; \
	done; echo " listos"
	@# Grafana arranca su plugin de PostgreSQL con la primera consulta; se dispara aquí, fuera de toda medición.
	@for i in $$(seq 1 30); do curl -fs -m 2 localhost:3000/api/health >/dev/null && break; sleep 1; done
	@curl -fs -m 20 -X POST -H 'Content-Type: application/json' localhost:3000/api/ds/query \
	  -d '{"from":"now-5m","to":"now","queries":[{"refId":"A","datasource":{"uid":"registro"},"rawSql":"SELECT 1","format":"table"}]}' >/dev/null \
	  && echo "Grafana precalentado" || echo "Grafana no respondió la consulta de precalentamiento"
	@echo "Grafana http://localhost:3000 · Prometheus http://localhost:9090 · Toxiproxy http://localhost:8474"

.PHONY: down
down: ## Detiene la topología; conserva la base y el histórico de Prometheus
	$(COMPOSE) down --remove-orphans

.PHONY: ps
ps: ## Estado de los contenedores
	$(COMPOSE) ps

.PHONY: logs
logs: ## Sigue los logs: make logs S=recepcion
	$(COMPOSE) logs -f $(S)

.PHONY: psql
psql: ## Abre psql sobre la base del prototipo
	$(COMPOSE) exec postgres psql -U reto4 -d reto4

.PHONY: vistas
vistas: ## Recarga las vistas SQL del cruce sin perder datos
	$(PSQL) -v ON_ERROR_STOP=1 < deploy/postgres/03-vistas.sql

##@ Compilación

.PHONY: build
build: pki ## Compila los servicios y corre sus pruebas unitarias
	./gradlew build --console=plain

.PHONY: imagenes
imagenes: ## Compila los jar y arma las imágenes (sin pruebas)
	./gradlew bootJar --console=plain -q
	$(COMPOSE) build --quiet

.PHONY: test
test: pki ## Pruebas unitarias (el camino criptográfico completo)
	./gradlew test --console=plain

.PHONY: help
help: ## Muestra esta ayuda
	@awk 'BEGIN {FS = ":.*##"} \
	  /^##@/ { printf "\n\033[1m%s\033[0m\n", substr($$0, 5); next } \
	  /^[a-zA-Z0-9_-]+:.*##/ { printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""
