#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
RESULT_DIR="${RESULT_DIR:-robot-results}"
VENV_DIR="${VENV_DIR:-.venv-robot}"

python3 -m virtualenv "${VENV_DIR}" >/dev/null 2>&1 || python3 -m venv "${VENV_DIR}"
"${VENV_DIR}/bin/python" -m pip install -r requirements-robot.txt
"${VENV_DIR}/bin/python" -m robot --variable "BASE_URL:${BASE_URL}" --outputdir "${RESULT_DIR}" tests/robot
