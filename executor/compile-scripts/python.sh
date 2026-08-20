#!/usr/bin/env bash
# Run a Python submission against the input file.
# Usage: python.sh <source.py> <input.txt> <workdir>
set -e
SRC="$1"; IN="$2"; DIR="$3"
python3 "$SRC" < "$IN"
