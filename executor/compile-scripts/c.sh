#!/usr/bin/env bash
# Compile + run a C submission against the input file.
# Usage: c.sh <source.c> <input.txt> <workdir>
set -e
SRC="$1"; IN="$2"; DIR="$3"
OUT="$DIR/a.out"
gcc -O2 -static -lm "$SRC" -o "$OUT" 2>/dev/null
"$OUT" < "$IN"
