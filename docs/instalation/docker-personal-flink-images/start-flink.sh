#!/bin/bash
set -e

# ---------------------------------------------
# Create the Flink network if it doesn't exist
# ---------------------------------------------
docker network inspect flink-net >/dev/null 2>&1 || docker network create flink-net

# ---------------------------------------------
# Stop & remove existing containers (if any)
# ---------------------------------------------
for container in jobmanager taskmanager; do
  if [ "$(docker ps -aq -f name=$container)" ]; then
    echo "Removing existing container: $container"
    docker rm -f $container >/dev/null
  fi
done

# ---------------------------------------------
# Build the custom Flink image
# ---------------------------------------------
docker build -t myflink:java21 .

# ---------------------------------------------
# Run the JobManager
# ---------------------------------------------
docker run -d --name jobmanager \
  --network flink-net \
  -p 8081:8081 \
  -e JOB_MANAGER_RPC_ADDRESS=jobmanager \
  myflink:java21

# ---------------------------------------------
# Run the TaskManager
# ---------------------------------------------
docker run -d --name taskmanager \
  --network flink-net \
  -e JOB_MANAGER_RPC_ADDRESS=jobmanager \
  myflink:java21 \
  bash -c "$FLINK_HOME/bin/taskmanager.sh start-foreground"

echo "✅ Flink cluster started successfully!"
echo "Open the Web UI at http://localhost:8081"
