#!/usr/bin/env bash
# Build the APK with the Gradle 8.7 already cached on this machine (the project has no wrapper).
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=$HOME/Android/Sdk JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
export PATH=$JAVA_HOME/bin:$PATH
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle)
"$G" assembleRelease -q --console=plain 2>&1 | { grep -v "Picked up JAVA_TOOL_OPTIONS" || true; }
ls -la app/build/outputs/apk/release/app-release.apk
