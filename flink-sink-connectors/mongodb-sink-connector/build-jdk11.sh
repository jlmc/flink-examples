#!/bin/bash

# Get the directory of the script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
PROJECT_ROOT="$( cd "$DIR/../.." >/dev/null 2>&1 && pwd )"

echo "Building mongodb-sink-connector with JDK 11 using Docker..."

docker run --rm \
  -v "$PROJECT_ROOT":/usr/src/mymaven \
  -v "$HOME/.m2":/root/.m2 \
  -w /usr/src/mymaven \
  maven:3.9.6-eclipse-temurin-11 \
  mvn clean package -pl flink-sink-connectors/mongodb-sink-connector -am -DskipTests

echo "Build completed successfully."
