#!/usr/bin/env bash
set -euo pipefail

mvn -Dmaven.repo.local=.m2/repository verify
