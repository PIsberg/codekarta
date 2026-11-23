#!/usr/bin/env bash
# Renders every diagram in code-karta-cli/diagrams.txt, plus three whole-tree runs, with
# --format mermaid and --format dot into <out-dir>, for validate.mjs to parse (#293).
# The renderer tests feed hand-built graphs; this feeds what the parsers make of real code.
#
#   scripts/format-check/render-cli-samples.sh <code-karta-cli-*-all.jar> <out-dir>
#
# Run from the repository root: diagrams.txt's input paths are relative to it.
set -euo pipefail

jar="$1"
out="$2"
mkdir -p "$out"
batch="$out/batch.txt"
: > "$batch"

extra=(
  # The whole example tree: package groups become DOT clusters and Mermaid class boxes.
  "--input example-shipping-system/src/main/java"
  # A stitched multi-file sequence over the example.
  "--input example-shipping-system/src/main/java --sequence-only"
  # A build-reactor flowchart, read from this repository's own pom.xml modules.
  "--input . --modules-only"
)

n=0
emit() {
  n=$((n + 1))
  for format in mermaid dot; do
    echo "$1 --output $out/$n --format $format" >> "$batch"
  done
}

while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  case "$line" in
    ''|'#'*|*--viewer*) continue ;;
  esac
  # Drop the committed --output; each line gets its own directory so no two collide.
  emit "$(sed -E 's/--output [^ ]+//' <<< "$line")"
done < code-karta-cli/diagrams.txt

for args in "${extra[@]}"; do
  emit "$args"
done

java -jar "$jar" --batch "$batch"

count="$(find "$out" -name '*.mmd' -o -name '*.dot' | wc -l)"
if [ "$count" -ne $((n * 2)) ]; then
  echo "::error::expected $((n * 2)) .mmd and .dot files from $n diagrams, found $count" >&2
  exit 1
fi
echo "rendered $n diagrams as Mermaid and DOT into $out"
