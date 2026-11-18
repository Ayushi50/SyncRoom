#!/usr/bin/env bash
# Builds one runnable jar that contains the API and the web app: backend/target/rooms-0.1.0.jar
set -euo pipefail
cd "$(dirname "$0")"
(cd frontend && npm ci && npm run build)
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/
(cd backend && ./mvnw -B -DskipTests package)
echo "Built backend/target/rooms-0.1.0.jar"
