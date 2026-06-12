#!/bin/sh
# Gradle wrapper stub - will be replaced by actual wrapper on first CI run
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(pwd)
exec java --enable-preview -cp "$APP_HOME/gradle/wrapper" org.gradle.wrapper.GradleWrapperMain "$@"
