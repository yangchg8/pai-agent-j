#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/pai-agent.jar"

# ── Detect whether sources have changed ────────────────────────────────────
needs_rebuild() {
    [[ ! -f "$JAR" ]] && return 0

    local jar_mtime
    jar_mtime=$(stat -f "%m" "$JAR" 2>/dev/null || stat -c "%Y" "$JAR")

    # Check pom.xml and all files under src/
    while IFS= read -r -d '' f; do
        local f_mtime
        f_mtime=$(stat -f "%m" "$f" 2>/dev/null || stat -c "%Y" "$f")
        (( f_mtime > jar_mtime )) && return 0
    done < <(find "$SCRIPT_DIR/src" "$SCRIPT_DIR/pom.xml" -type f -print0)

    return 1
}

# ── Rebuild if needed ───────────────────────────────────────────────────────
if needs_rebuild; then
    echo "🔄  Sources changed — rebuilding…"
    mvn -f "$SCRIPT_DIR/pom.xml" package -DskipTests -q
    echo "✅  Build complete"
else
    echo "✔  No changes detected, skipping build"
fi

# ── Run ─────────────────────────────────────────────────────────────────────
# Default to --less unless --full is explicitly passed
mode_flag="--less"
jvm_args=()
passthrough=()

for arg in "$@"; do
    case "$arg" in
        --full) mode_flag="--full" ;;
        --less) mode_flag="--less" ;;
        -D*|-X*|-XX:*|-javaagent:*|-agentlib:*|-agentpath:*|--add-opens=*|--add-exports=*|--enable-native-access=*)
            jvm_args+=("$arg") ;;
        *)
            passthrough+=("$arg") ;;
    esac
done

exec java "${jvm_args[@]+"${jvm_args[@]}"}" -jar "$JAR" "$mode_flag" "${passthrough[@]+"${passthrough[@]}"}"
