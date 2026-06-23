ROOT := $(shell git rev-parse --show-toplevel)

.PHONY: help install-hooks test style

help:
	@echo "make install-hooks  Install the pre-push hook (run once after clone)"
	@echo "make test           Run unit tests"
	@echo "make lint           Run Android lint"

# Symlinks the committed pre-push hook into .git/hooks/. Idempotent.
install-hooks:
	@ln -sf '$(ROOT)/scripts/git-hooks/pre-push' '$(ROOT)/.git/hooks/pre-push'
	@chmod +x '$(ROOT)/scripts/git-hooks/pre-push'
	@echo "Pre-push hook installed -> .git/hooks/pre-push"

test:
	@./gradlew testDebugUnitTest

lint:
	@./gradlew lint
