#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/bin"

javac -d "$ROOT/bin" "$ROOT"/src/*.java
java -cp "$ROOT/bin" Simulation
