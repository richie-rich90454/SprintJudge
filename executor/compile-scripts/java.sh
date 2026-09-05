#!/usr/bin/env bash
# Compile + run a Java submission against the input file.
# Convention: the entry class is named "Main" (file Main.java).
# Usage: java.sh <source.java> <input.txt> <workdir>
set -euo pipefail
SRC="${1:?usage: java.sh <source.java> <input.txt> <workdir>}"
IN="${2:?usage: java.sh <source.java> <input.txt> <workdir>}"
DIR="${3:?usage: java.sh <source.java> <input.txt> <workdir>}"
BASE=$(basename "$SRC" .java)
javac -d "$DIR" "$SRC"
java -cp "$DIR" "$BASE" < "$IN"
