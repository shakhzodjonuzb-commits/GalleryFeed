#!/usr/bin/env bash
# Lightweight Gradle bootstrapper. It downloads the pinned Gradle distribution
# when the official wrapper JAR is not present in this source archive.
set -euo pipefail

GRADLE_VERSION="8.14.6"
GRADLE_HOME_BASE="${GRADLE_USER_HOME:-$HOME/.gradle}"
DIST_DIR="$GRADLE_HOME_BASE/galleryfeed-distributions/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/gradle-$GRADLE_VERSION/bin/gradle"
ZIP_PATH="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$DIST_DIR"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP_PATH" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  else
    echo "curl or wget is required to download Gradle." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP_PATH" -d "$DIST_DIR"
  else
    echo "unzip is required to extract Gradle." >&2
    exit 1
  fi
fi

exec "$GRADLE_BIN" "$@"
