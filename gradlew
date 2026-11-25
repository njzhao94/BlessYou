#!/bin/sh
# Bootstrap the unmodified official Gradle wrapper JAR, omitted from this source-only archive.
set -eu
cd -- "$(dirname -- "$0")"
wrapper="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$wrapper" ]; then
    source_url="https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --retry 2 "$source_url" --output "$wrapper.part"
    elif command -v wget >/dev/null 2>&1; then
        wget --output-document="$wrapper.part" "$source_url"
    else
        printf '%s\n' 'Install curl or wget to download the official Gradle wrapper.' >&2
        exit 1
    fi
    mv "$wrapper.part" "$wrapper"
fi
if [ -n "${JAVA_HOME:-}" ]; then
    java_command="$JAVA_HOME/bin/java"
else
    java_command="java"
fi
exec "$java_command" -classpath "$wrapper" org.gradle.wrapper.GradleWrapperMain "$@"
