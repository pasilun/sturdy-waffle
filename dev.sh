#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Java 21 ─────────────────────────────────────────────────────────────────
# Find a Java 21 JDK.  Priority order:
#   1. JAVA_HOME if it already points to Java 21
#   2. `java` on PATH if it is Java 21
#   3. VS Code bundled JRE 21 (common on Linux dev machines)
_java_version() { "$1" -version 2>&1 | grep -oE '"[0-9]+\.' | head -1 | tr -d '"'; }
_find_java21() {
    # Check JAVA_HOME first
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] \
       && [ "$(_java_version "$JAVA_HOME/bin/java")" = "21." ]; then
        echo "$JAVA_HOME"; return
    fi
    # Check java on PATH
    if command -v java &>/dev/null && [ "$(_java_version java)" = "21." ]; then
        echo "$(dirname "$(dirname "$(command -v java)")")"; return
    fi
    # VS Code bundled JRE
    find "${HOME}/.vscode/extensions" -name "java" -path "*/21*/bin/java" \
         2>/dev/null | head -1 | sed 's|/bin/java||'
}
_jdk21=$(_find_java21)
if [ -z "$_jdk21" ]; then
    echo "✗  Java 21 not found. Install JDK 21 and set JAVA_HOME, or run sdk install java 21."
    exit 1
fi
export JAVA_HOME="$_jdk21"
export PATH="$JAVA_HOME/bin:$PATH"
echo "→ Java $(_java_version "$JAVA_HOME/bin/java") at $JAVA_HOME"

# ── API key ──────────────────────────────────────────────────────────────────
_env_file="$ROOT_DIR/api/.env"
if [ ! -f "$_env_file" ]; then
    _key_file="$HOME/src/interview 2/anthropic_api_key.txt"
    if [ -f "$_key_file" ]; then
        echo "ANTHROPIC_API_KEY=$(cat "$_key_file")" > "$_env_file"
        echo "→ Created api/.env from key file"
    else
        echo "✗  api/.env not found. Create it: echo 'ANTHROPIC_API_KEY=sk-ant-...' > api/.env"
        exit 1
    fi
fi
set -a; source "$_env_file"; set +a

# ── API ──────────────────────────────────────────────────────────────────────
echo "→ Starting API (Spring Boot) on :8080 …"
cd "$ROOT_DIR/api"
chmod +x gradlew
./gradlew bootRun --no-daemon &
API_PID=$!

# ── Web ──────────────────────────────────────────────────────────────────────
echo "→ Starting web (Vite) on :5173 …"
cd "$ROOT_DIR/web"
pnpm install --frozen-lockfile 2>/dev/null || pnpm install
pnpm dev &
WEB_PID=$!

echo ""
echo "  API  → http://localhost:8080"
echo "  Web  → http://localhost:5173"
echo "  Press Ctrl-C to stop both."

trap 'echo "Stopping…"; kill "$API_PID" "$WEB_PID" 2>/dev/null; wait; exit 0' INT TERM
wait
