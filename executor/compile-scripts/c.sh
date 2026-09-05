#!/usr/bin/env bash
# Compile + run a C submission against the input file.
# Usage: c.sh <source.c> <input.txt> <workdir>
set -euo pipefail
SRC="${1:?usage: c.sh <source.c> <input.txt> <workdir>}"
IN="${2:?usage: c.sh <source.c> <input.txt> <workdir>}"
DIR="${3:?usage: c.sh <source.c> <input.txt> <workdir>}"
OUT="$DIR/a.out"
# Shared (not static) linking: static glibc is absent from minimal and
# sandboxed images. Compiler diagnostics stay on stderr for the caller.
gcc -O2 "$SRC" -o "$OUT" -lm
"$OUT" < "$IN"
