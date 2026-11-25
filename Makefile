.DEFAULT_GOAL := help
.PHONY: help build test verify format format-check clean \
        redis-up postgres-up infra-down run-gateway run-admin \
        cluster-up cluster-down cluster-logs cluster-ps load-check benchmarks mutation

MVN     ?= mvn
COMPOSE ?= docker compose
VERSION ?= 0.1.0-SNAPSHOT

help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make \033[36m<target>\033[0m\n\nTargets:\n"} \
		/^[a-zA-Z0-9_-]+:.*##/ {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

##@ Build and test

build: ## Compile and package every module, without tests
	$(MVN) -B -DskipTests package

test: ## Run the unit and integration tests (Docker needed for Testcontainers)
	$(MVN) -B test

verify: ## Full gate: tests, Spotless check and coverage for the whole reactor
	$(MVN) -B verify

format: ## Format the code with Spotless
	$(MVN) -B spotless:apply

format-check: ## Check the formatting without changing files
	$(MVN) -B spotless:check

clean: ## Remove build output
	$(MVN) -B clean

##@ Local run

redis-up: ## Start Redis on localhost:6379
	$(COMPOSE) up -d redis

postgres-up: ## Start PostgreSQL on localhost:5432 (used by the admin API)
	$(COMPOSE) --profile cluster up -d postgres

infra-down: ## Stop Redis and PostgreSQL
	$(COMPOSE) --profile cluster stop redis postgres

run-gateway: build ## Run one gateway on :8080 (backend expected on :8081)
	java -jar gateway/target/gateway-$(VERSION).jar

run-admin: build postgres-up ## Run the admin API on :8090 against the local PostgreSQL
	java -jar admin-api/target/admin-api-$(VERSION).jar

##@ Cluster (three gateways, nginx, Redis, PostgreSQL, admin API, Jaeger, Prometheus, Grafana)

cluster-up: ## Build and start the whole cluster, nginx on :8000
	$(COMPOSE) --profile cluster up -d --build

cluster-down: ## Stop and remove the cluster
	$(COMPOSE) --profile cluster down

cluster-ps: ## Show the cluster containers
	$(COMPOSE) --profile cluster ps

cluster-logs: ## Follow the cluster logs
	$(COMPOSE) --profile cluster logs -f --tail=100

load-check: ## Check the shared limit under concurrency (cluster must be up)
	python3 scripts/load_check.py

##@ Performance

mutation: ## Run PIT mutation testing on ratelimiter-core (fails below 80% killed)
	$(MVN) -B -pl ratelimiter-core test-compile org.pitest:pitest-maven:1.30.2:mutationCoverage

benchmarks: ## Run the JMH benchmarks (full run, takes a while)
	$(MVN) -B -DskipTests package -pl benchmarks -am
	java -jar benchmarks/target/benchmarks.jar
