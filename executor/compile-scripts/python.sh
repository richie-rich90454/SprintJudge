#!/usr/bin/env bash
# Run a Python submission against the input file.
# Usage: python.sh <source.py> <input.txt> <workdir>
set -euo pipefail
SRC="${1:?usage: python.sh <source.py> <input.txt> <workdir>}"
IN="${2:?usage: python.sh <source.py> <input.txt> <workdir>}"
DIR="${3:?usage: python.sh <source.py> <input.txt> <workdir>}"
python3 "$SRC" < "$IN"
