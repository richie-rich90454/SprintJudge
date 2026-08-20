#!/usr/bin/env bash
# Run a Node.js (JavaScript) submission against the input file.
# Usage: node.sh <source.js> <input.txt> <workdir>
set -e
SRC="$1"; IN="$2"; DIR="$3"
node "$SRC" < "$IN"
