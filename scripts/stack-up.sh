#!/usr/bin/env bash
set -euo pipefail

mvn -Dmaven.repo.local=.m2/repository -DskipTests package
docker compose up --build -d api worker
docker compose ps
