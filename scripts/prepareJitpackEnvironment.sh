#!/usr/bin/env bash
#
# Prepares the JitPack build environment before Gradle runs.
#
# NOTE: This is a stub. Other CountryDelight libraries ship their own
# prepareJitpackEnvironment.sh — reconcile this with that canonical copy
# (it may inject secrets, google-services.json, or keystore config).
#
set -euo pipefail

# JitPack provides the Android SDK; point Gradle at it explicitly.
if [ -n "${ANDROID_HOME:-}" ]; then
  echo "sdk.dir=${ANDROID_HOME}" > local.properties
fi

# Ensure the Gradle wrapper is executable on the build host.
chmod +x ./gradlew || true

echo "JitPack environment prepared."
