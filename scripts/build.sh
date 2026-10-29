#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [ -z "${ANDROID_HOME:-}" ] && [ -d /private/tmp/cleanyourtv-sdk ]; then
  export ANDROID_HOME=/private/tmp/cleanyourtv-sdk
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/private/tmp/cleanyourtv-gradle}"
./gradlew --no-daemon :core:test :tv:lintDebug :phone:lintDebug :tv:assembleDebug :phone:assembleDebug --console=plain
mkdir -p dist
cp tv/build/outputs/apk/debug/tv-debug.apk dist/.CouchLink-TV.apk.tmp
mv dist/.CouchLink-TV.apk.tmp dist/CouchLink-TV.apk
cp phone/build/outputs/apk/debug/phone-debug.apk dist/.CouchLink-Phone.apk.tmp
mv dist/.CouchLink-Phone.apk.tmp dist/CouchLink-Phone.apk
