# Tallyard developer entry points. Every target is also documented in README.md.
SHELL := /bin/bash
COMPOSE := docker compose --env-file .env -f deploy/docker-compose.yml
MVN := mvn -B -q

.PHONY: help env up down reset logs build test it verify fmt run-api run-worker run-all sim load-ingest load-entitlement openapi

help: ## list targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

env: ## create .env from .env.example with a fresh encryption key
	@test -f .env || { cp .env.example .env; sed -i.bak "s#REPLACE_WITH_BASE64_32_BYTES#$$(openssl rand -base64 32)#" .env && rm .env.bak; echo "created .env"; }

up: env ## build images and start the full stack (API, worker, Postgres, Kafka, Prometheus, Grafana, Jaeger)
	$(COMPOSE) up -d --build
	@echo "Dashboard http://localhost:8080  Grafana http://localhost:3000  Jaeger http://localhost:16686  OpenAPI http://localhost:8080/swagger-ui.html"

down: ## stop the stack, keep data
	$(COMPOSE) down

reset: ## stop the stack and delete all data volumes
	$(COMPOSE) down -v

logs: ## follow API and worker logs
	$(COMPOSE) logs -f api worker

infra: env ## start only Postgres and Kafka (for running the app from the IDE / Maven)
	$(COMPOSE) up -d postgres kafka

build: ## compile and package without tests
	$(MVN) -DskipTests -Dspotless.check.skip=true package

test: ## unit, property, Modulith and ArchUnit tests
	$(MVN) test

it: ## integration tests (Testcontainers; needs Docker)
	$(MVN) verify

verify: fmt-check test it ## everything CI runs

fmt: ## apply formatting
	$(MVN) spotless:apply

fmt-check: ## check formatting
	$(MVN) spotless:check

run-all: ## run the app locally (role=all) against `make infra`
	set -a && source .env && set +a && TALLYARD_KAFKA_BOOTSTRAP=localhost:9094 TALLYARD_DEMO_ENABLED=true TALLYARD_OTLP_ENDPOINT=http://localhost:4318/v1/traces mvn -pl tallyard-app spring-boot:run

sim: ## stream realistic traffic with duplicates/late/invalid events and verify totals (needs the stack up)
	java -jar tallyard-simulator/target/tallyard-simulator.jar --key $${TALLYARD_API_KEY:-$$(grep TALLYARD_DEMO_ADMIN_KEY .env | cut -d= -f2)} --rate $${RATE:-500} --duration $${DURATION:-60}

load-ingest: ## k6 ingest load test (docker)
	docker run --rm -i --network host -e API_KEY=$$(grep TALLYARD_DEMO_ADMIN_KEY .env | cut -d= -f2) -e BASE_URL=http://localhost:8080 -v $$(pwd)/loadtest:/scripts grafana/k6 run /scripts/ingest.js

load-entitlement: ## k6 entitlement load test (docker)
	docker run --rm -i --network host -e API_KEY=$$(grep TALLYARD_DEMO_ADMIN_KEY .env | cut -d= -f2) -e BASE_URL=http://localhost:8080 -v $$(pwd)/loadtest:/scripts grafana/k6 run /scripts/entitlement.js

openapi: ## refresh docs/api/openapi.json from the running tests
	$(MVN) verify -Dit.test=ContractIT -Dcontracts.update=true
