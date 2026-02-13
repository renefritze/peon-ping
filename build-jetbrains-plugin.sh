#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGIN_DIR="$SCRIPT_DIR/adapters/jetbrains"

if [ ! -d "$PLUGIN_DIR" ]; then
  echo "Error: JetBrains plugin directory not found at $PLUGIN_DIR" >&2
  exit 1
fi

GRADLE_CMD=()
if [ -x "$PLUGIN_DIR/gradlew" ]; then
  GRADLE_CMD=("$PLUGIN_DIR/gradlew")
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD=("gradle")
else
  echo "Error: neither gradle nor adapters/jetbrains/gradlew is available." >&2
  echo "Install Gradle, or add a Gradle wrapper in adapters/jetbrains." >&2
  exit 1
fi

TASKS=("buildPlugin")
if [ "$#" -gt 0 ]; then
  TASKS=("$@")
fi

echo "Building JetBrains plugin in: $PLUGIN_DIR"
(
  cd "$PLUGIN_DIR"
  "${GRADLE_CMD[@]}" "${TASKS[@]}"
)

ZIP_PATH="$(ls -1t "$PLUGIN_DIR"/build/distributions/*.zip 2>/dev/null | head -n 1 || true)"
if [ -n "$ZIP_PATH" ]; then
  echo ""
  echo "Plugin archive:"
  echo "  $ZIP_PATH"
  echo ""
  echo "Install in JetBrains: Settings -> Plugins -> gear icon -> Install Plugin from Disk..."
fi

