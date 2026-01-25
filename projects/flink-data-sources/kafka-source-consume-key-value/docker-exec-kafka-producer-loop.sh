#!/bin/bash

KAFKA_CONTAINER="kafka"
BROKER="localhost:9092"
TOPIC="person-location-events"
DELAY=5

USERS=("user-1" "user-2" "user-3" "user-4" "user-5")

echo "Starting Kafka producer"
echo "One message per user, ${DELAY}s delay, Ctrl+C to stop"
echo "Container: $KAFKA_CONTAINER | Topic: $TOPIC"
echo "--------------------------------------------------"

while true; do
  for USER in "${USERS[@]}"; do

    if [[ "$(uname)" == "Darwin" ]]; then
      TIMESTAMP=$(python3 -c "import time; print(int(time.time() * 1000))")
    else
      TIMESTAMP=$(date +%s%3N)
    fi

    LAT=$(awk -v min=40 -v max=45 'BEGIN{srand(); print min+rand()*(max-min)}')
    LON=$(awk -v min=-75 -v max=-70 'BEGIN{srand(); print min+rand()*(max-min)}')

    MESSAGE=$(cat <<EOF
{ "person_id": "$USER", "latitude": $LAT, "longitude": $LON, "event_timestamp": $TIMESTAMP}
EOF
)

    echo "[$(date +"%H:%M:%S")] Producing for $USER"

    echo "SEND MESSAGE: $USER:$MESSAGE"

    docker exec -i "$KAFKA_CONTAINER" bash -c "
      echo '$USER:$MESSAGE' | kafka-console-producer \
        --bootstrap-server $BROKER \
        --topic $TOPIC \
        --property parse.key=true \
        --property key.separator=:
    "

    sleep "$DELAY"
  done
done
