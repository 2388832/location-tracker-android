#!/bin/bash

##############################################################################
##
##  Gradle start up script for UNIX
##
##############################################################################

# 设置 JAVA_HOME（可选）
# export JAVA_HOME=/path/to/java

# Gradle Wrapper 入口脚本
DIRNAME=$(dirname "$0")
DIRNAME=$(cd "$DIRNAME" && pwd)

# 默认 JVM 参数
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Gradle wrapper jar 路径
WRAPPER_JAR="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"

# 执行 Gradle
exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
