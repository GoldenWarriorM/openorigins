#!/usr/bin/env bash
# Ставит локальный JDK 25 в .jdk/java-25 (без sudo) и чинит symlink conf Arch-пакета.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -x .jdk/java-25/bin/java ]; then
  echo "JDK already present: .jdk/java-25"
  .jdk/java-25/bin/java -version 2>&1 | head -n 1
  exit 0
fi

mkdir -p .jdk/pkg
URL="$(pacman -Sp jdk25-openjdk 2>/dev/null | head -n 1 || true)"
if [ -z "${URL:-}" ]; then
  echo "ERROR: pacman cannot resolve jdk25-openjdk URL. Install JDK 25 manually and set JAVA_HOME." >&2
  exit 1
fi
echo "Downloading $URL ..."
curl -Ls --retry 3 -o .jdk/pkg/jdk25.pkg.tar.zst "$URL"
tar --use-compress-program=unzstd -xf .jdk/pkg/jdk25.pkg.tar.zst -C .jdk/pkg
mkdir -p .jdk
rm -rf .jdk/java-25
mv .jdk/pkg/usr/lib/jvm/java-25-openjdk .jdk/java-25
# Arch-пакет хранит конфиги в /etc через symlink conf -> /etc/java25-openjdk,
# которого нет без установки: подменяем реальной копией.
rm -f .jdk/java-25/conf
cp -r .jdk/pkg/etc/java25-openjdk .jdk/java-25/conf
rm -rf .jdk/pkg
.jdk/java-25/bin/java -version 2>&1 | head -n 1
echo "OK: export JAVA_HOME=\$PWD/.jdk/java-25"
