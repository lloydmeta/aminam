# Aminam developer tasks. Run `make help` for a list.

KEYS_DIR := keys
JWT_PRIVATE := $(KEYS_DIR)/jwt-private.pem
JWT_PUBLIC := $(KEYS_DIR)/jwt-public.pem

.DEFAULT_GOAL := help

.PHONY: help
help: ## List available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

$(JWT_PRIVATE):
	@mkdir -p $(KEYS_DIR)
	openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $(JWT_PRIVATE)

$(JWT_PUBLIC): $(JWT_PRIVATE)
	openssl rsa -in $(JWT_PRIVATE) -pubout -out $(JWT_PUBLIC)

.PHONY: keys
keys: $(JWT_PUBLIC) ## Generate the RSA keypair for signing tokens (into ./keys)
	@echo "Keys written to $(KEYS_DIR)/. Mounted into the container by docker-compose."

.PHONY: build
build: ## Build the JVM image via the Quarkus container build
	./gradlew build -Dquarkus.container-image.build=true

.PHONY: up
up: keys ## Start the full stack (postgres, redis, app) with mounted keys
	docker compose up --build

.PHONY: down
down: ## Stop the stack and remove volumes
	docker compose down -v

.PHONY: dev
dev: ## Run the app in Quarkus dev mode (Dev Services provide postgres + redis)
	./gradlew quarkusDev

.PHONY: demo
demo: ## Walk through the authorisation engine against a running app on :8080
	./demo.sh

.PHONY: test
test: ## Run the test suite
	./gradlew test
