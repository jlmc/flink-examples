#!/bin/bash
set -e

echo "🧹 Stopping Apache Flink cluster..."

# List of JobManager and TaskManager container names
containers=("jobmanager-master" "jobmanager" "taskmanager")

for container in "${containers[@]}"; do
  if [ "$(docker ps -q -f name=$container)" ] || [ "$(docker ps -aq -f name=$container)" ]; then
    echo "Stopping and removing container: $container"
    docker stop $container >/dev/null 2>&1
    docker rm -f $container >/dev/null 2>&1
  else
    echo "Container $container not running."
  fi
done

# Remove network if it exists
if docker network inspect flink-net >/dev/null 2>&1; then
  echo "Removing Docker network flink-net..."
  docker network rm flink-net >/dev/null
else
  echo "Network flink-net not found."
fi

echo "✅ Flink cluster stopped and cleaned up successfully."
