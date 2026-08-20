#!/usr/bin/env bash
# Compile + run a C++ submission against the input file.
# Usage: cpp.sh <source.cpp> <input.txt> <workdir>
set -e
SRC="$1"; IN="$2"; DIR="$3"
OUT="$DIR/a.out"
g++ -O2 -static -std=c++17 "$SRC" -o "$OUT" 2>/dev/null
"$OUT" < "$IN"
