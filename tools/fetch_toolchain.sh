#!/usr/bin/env bash
# Récupère la chaîne d'outils hors-ligne utilisée par build/build_apk.py et
# build/compile_desktop.sh (aucun SDK Android requis) :
#   - JRE (jdk4py)          -> python -c "import jdk4py; print(jdk4py.JAVA_HOME)"
#   - kotlinc (npm)         -> ~/.cache/toolchain/kotlinc
#   - r8.jar (D8/R8)        -> ~/.cache/toolchain/r8.jar
#   - android-34.jar        -> ~/.cache/toolchain/android-34.jar
#   - aapt2 (npm aaptjs3)   -> ~/.cache/toolchain/aapt2
# Les paquets Python nécessaires aux scripts (art, audio, signature) sont
# installés en même temps.
set -euo pipefail
TC="${TOOLCHAIN_DIR:-$HOME/.cache/toolchain}"
mkdir -p "$TC"
cd "$TC"

echo "== Python packages"
python3 -m pip install --quiet --break-system-packages \
  jdk4py pillow numpy scipy soundfile fonttools cryptography apksigtool brotli 2>&1 | tail -2 || true

if [ ! -x "$TC/kotlinc/bin/kotlinc" ]; then
  echo "== kotlinc"
  rm -rf ktmp && mkdir ktmp && cd ktmp
  npm pack kotlin-compiler@2.4.20 --silent >/dev/null
  tar xzf kotlin-compiler-*.tgz
  # le paquet npm EST la distribution kotlinc (bin/, lib/)
  rm -rf "$TC/kotlinc" && mv package "$TC/kotlinc"
  cd .. && rm -rf ktmp
  chmod +x "$TC/kotlinc/bin/"*
fi

# Téléchargement d'un fichier d'un dépôt GitHub : via `gh api` (blobs) si gh est authentifié,
# sinon via raw.githubusercontent.com (CI, poste de travail sans gh).
fetch_github_file() { # repo path dest
  local repo="$1" path="$2" dest="$3" sha=""
  if gh auth status >/dev/null 2>&1; then
    sha=$(gh api "repos/$repo/contents/$(dirname "$path")" --jq ".[] | select(.name==\"$(basename "$path")\") | .sha" 2>/dev/null || true)
  fi
  if [ -n "$sha" ]; then
    gh api -H "Accept: application/vnd.github.raw+json" "repos/$repo/git/blobs/$sha" > "$dest"
  else
    curl -fsSL "https://raw.githubusercontent.com/$repo/HEAD/$path" -o "$dest"
  fi
  [ -s "$dest" ] || { echo "échec du téléchargement de $repo/$path"; exit 1; }
}

if [ ! -f "$TC/r8.jar" ]; then
  echo "== r8.jar"
  fetch_github_file ProjectGhostOS/prebuilts_r8 r8.jar r8.jar
fi

if [ ! -f "$TC/android-34.jar" ]; then
  echo "== android-34.jar"
  fetch_github_file Sable/android-platforms android-34/android.jar android-34.jar
fi

if [ ! -x "$TC/aapt2" ]; then
  echo "== aapt2"
  rm -rf atmp && mkdir atmp && cd atmp
  npm pack aaptjs3 --silent >/dev/null
  tar xzf aaptjs3-*.tgz
  BIN=$(find package -type f -path '*linux*' -name 'aapt2*' | head -1)
  if [ -z "$BIN" ]; then echo "aapt2 introuvable"; find package -type f | head -30; exit 1; fi
  cp "$BIN" ../aapt2 && chmod +x ../aapt2
  cd .. && rm -rf atmp
fi

echo "== OK"
ls -la "$TC"
JAVA_HOME=$(python3 -c "import jdk4py; print(jdk4py.JAVA_HOME)")
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
