#!/usr/bin/env bash
# Compile le cœur du jeu + le harnais bureau, puis (optionnel) lance les tests ou le jeu.
#   tools/compile_desktop.sh            → compile build/out/game-desktop.jar
#   tools/compile_desktop.sh --tests    → compile et lance la suite de tests de contenu
#   tools/compile_desktop.sh --run      → compile et lance la fenêtre de jeu
#   tools/compile_desktop.sh --tour DIR → compile et produit les captures d'écran dans DIR
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)' 2>/dev/null || echo /usr/lib/jvm/default-java)}"
export PATH="$JAVA_HOME/bin:$PATH"
KOTLINC="${KOTLINC:-$HOME/.cache/toolchain/kotlinc/bin/kotlinc}"
mkdir -p build/out
if [ ! -f build/out/game-desktop.jar ] || [ -n "$(find core/src desktop/src -newer build/out/game-desktop.jar -name '*.kt' 2>/dev/null | head -1)" ]; then
  echo "▶ compilation Kotlin (core + desktop)…"
  "$KOTLINC" -nowarn -jvm-target 17 core/src desktop/src -d build/out/game-desktop.jar
fi
CP="build/out/game-desktop.jar:$HOME/.cache/toolchain/kotlinc/lib/kotlin-stdlib.jar"
MAIN=com.ateliermareebasse.cartographie.desktop.DesktopMainKt
case "${1:-}" in
  --tests) java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -Dsrc.dir=core/src -cp "$CP" $MAIN --tests "${2:-}" ;;
  --run) java -cp "$CP" $MAIN ;;
  --tour) java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp "$CP" $MAIN --tour "${2:-build/out/shots}" ;;
  *) echo "ok: build/out/game-desktop.jar" ;;
esac
