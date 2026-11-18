#!/usr/bin/env bash
# Starts the backend with the settings from .env in the repo root.
set -euo pipefail
cd "$(dirname "$0")"
set -a
# shellcheck disable=SC1091
[ -f .env ] && . ./.env
set +a
cd backend
exec ./mvnw spring-boot:run
