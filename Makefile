# Project Chimera — Developer Makefile
# Traces to: specs/technical.md §5.1 (Maven 3.9, Docker 25), Constitution Principle VI (TDD)
#
# Usage: make <target>
# All targets assume Maven 3.9+ and Java 21+ are on PATH.

.DEFAULT_GOAL := help
.PHONY: help setup test lint spec-check docker-test

# ─────────────────────────────────────────────────────────────────────────────
# help — list all available targets
# ─────────────────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "Project Chimera — available make targets"
	@echo "─────────────────────────────────────────"
	@echo "  make setup        Install dependencies and compile (skips tests)"
	@echo "  make test         Run the full JUnit 5 test suite"
	@echo "  make lint         Run Checkstyle — enforces Constitution Principle I style rules"
	@echo "  make spec-check   Verify all spec documents are present"
	@echo "  make docker-test  Build Docker image and run tests inside the container"
	@echo ""
	@echo "Prerequisites: Java 21+, Maven 3.9+, Docker 25+"
	@echo ""

# ─────────────────────────────────────────────────────────────────────────────
# setup — compile the project and install artifacts to local Maven cache
# ─────────────────────────────────────────────────────────────────────────────
setup:
	@echo "→ Installing dependencies and compiling (tests skipped)…"
	mvn clean install -DskipTests
	@echo "✓ Setup complete."

# ─────────────────────────────────────────────────────────────────────────────
# test — run the full JUnit 5 suite via maven-surefire-plugin
# Constitution Principle VI: Red-Green-Refactor; all tests must pass before merge.
# ─────────────────────────────────────────────────────────────────────────────
test:
	@echo "→ Running JUnit 5 test suite…"
	mvn test
	@echo "✓ Tests complete. Check target/surefire-reports/ for details."

# ─────────────────────────────────────────────────────────────────────────────
# lint — run Checkstyle to enforce code style and architecture rules
# Catches: direct SDK imports in Worker business logic, non-Record DTOs.
# Constitution Principle I: Java 21+ standards enforced by tooling.
# ─────────────────────────────────────────────────────────────────────────────
lint:
	@echo "→ Running Checkstyle…"
	mvn checkstyle:check
	@echo "✓ Checkstyle passed."

# ─────────────────────────────────────────────────────────────────────────────
# spec-check — verify all spec documents exist and list their contents
# Constitution Principle II: no implementation without an approved spec.
# ─────────────────────────────────────────────────────────────────────────────
spec-check:
	@echo ""
	@echo "Checking specs alignment…"
	@echo "─────────────────────────────────────────"
	@REQUIRED="_meta.md functional.md technical.md openclaw_integration.md"; \
	MISSING=0; \
	for spec in $$REQUIRED; do \
		if [ -f "specs/$$spec" ]; then \
			echo "  ✓ specs/$$spec"; \
		else \
			echo "  ✗ specs/$$spec  ← MISSING"; \
			MISSING=$$((MISSING + 1)); \
		fi; \
	done; \
	echo ""; \
	echo "All files in specs/:"; \
	ls -1 specs/ 2>/dev/null | sed 's/^/    /'; \
	echo ""; \
	if [ $$MISSING -gt 0 ]; then \
		echo "ERROR: $$MISSING required spec file(s) missing. No implementation without an approved spec (Constitution Principle II)."; \
		exit 1; \
	else \
		echo "✓ All required specs present."; \
	fi
	@echo ""

# ─────────────────────────────────────────────────────────────────────────────
# docker-test — build the project image and run the test suite inside Docker
# Ensures tests pass in a clean, reproducible environment identical to CI.
# Constitution Principle I: Docker 25 required for all deployable services.
# ─────────────────────────────────────────────────────────────────────────────
docker-test:
	@echo "→ Building Docker image chimera-test:latest…"
	docker build --target test -t chimera-test:latest .
	@echo "→ Running tests inside container…"
	docker run --rm \
		-e CHIMERA_ENV=test \
		chimera-test:latest \
		mvn test
	@echo "✓ Docker test run complete."
