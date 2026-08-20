#!/usr/bin/env bash
# Compile + run a Java submission against the input file.
# Convention: the public class is named "Solution" (file Solution.java).
# Usage: java.sh <source.java> <input.txt> <workdir>
set -e
SRC="$1"; IN="$2"; DIR="$3"
BASE=$(basename "$SRC" .java)
javac -d "$DIR" "$SRC" 2>/dev/null
java -cp "$DIR" "$BASE" < "$IN"
