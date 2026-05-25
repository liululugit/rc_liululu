#!/bin/sh
# Maven Wrapper

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk1.8.0_361.jdk/Contents/Home}"

MAVEN_PROJECTBASEDIR=$(cd "$(dirname "$0")"; pwd)
MAVEN_OPTS="$MAVEN_OPTS -Dfile.encoding=UTF-8"

exec "$JAVA_HOME/bin/java" -classpath "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar" \
    -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
    org.apache.maven.wrapper.MavenWrapperMain "$@"
