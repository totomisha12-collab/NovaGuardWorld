#!/usr/bin/env sh
# Pinned Gradle bootstrap. Downloads the project-pinned distribution when absent.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.10.2
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin/local"
GRADLE="$DIST/gradle-$GRADLE_VERSION/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  mkdir -p "$DIST"
  ZIP="$DIST/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else echo "curl or wget is required to bootstrap Gradle" >&2; exit 1; fi
  unzip -q -o "$ZIP" -d "$DIST"
fi
exec "$GRADLE" -p "$APP_HOME" "$@"
