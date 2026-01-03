#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Missing gradle wrapper jar at $WRAPPER_JAR" >&2
  exit 1
fi

exec java -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
