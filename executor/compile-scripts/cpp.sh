#!/usr/bin/env bash
# Compile + run a C++ submission against the input file.
# Usage: cpp.sh <source.cpp> <input.txt> <workdir>
set -euo pipefail
SRC="${1:?usage: cpp.sh <source.cpp> <input.txt> <workdir>}"
IN="${2:?usage: cpp.sh <source.cpp> <input.txt> <workdir>}"
DIR="${3:?usage: cpp.sh <source.cpp> <input.txt> <workdir>}"
OUT="$DIR/a.out"
# Shared (not static) linking: static libstdc++ is absent from minimal and
# sandboxed images. Compiler diagnostics stay on stderr for the caller.
g++ -O2 -std=c++17 "$SRC" -o "$OUT"
"$OUT" < "$IN"
