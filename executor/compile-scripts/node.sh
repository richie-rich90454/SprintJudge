#!/usr/bin/env bash
# Run a Node.js (JavaScript) submission against the input file.
# Usage: node.sh <source.js> <input.txt> <workdir>
set -euo pipefail
SRC="${1:?usage: node.sh <source.js> <input.txt> <workdir>}"
IN="${2:?usage: node.sh <source.js> <input.txt> <workdir>}"
DIR="${3:?usage: node.sh <source.js> <input.txt> <workdir>}"
node "$SRC" < "$IN"
