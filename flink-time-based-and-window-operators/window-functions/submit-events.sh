#!/bin/bash

# TOPIC name
TOPIC="sensors-data"

# Function to send an event
send_event() {
  ID=$1
  TEMP=$2
  # Current timestamp in ISO 8601 format
  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
  
  JSON="{\"id\":\"$ID\",\"timestamp\":\"$TS\",\"temperature\":$TEMP}"
  
  echo "Sending: $JSON"
  echo "$JSON" | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic $TOPIC
}

echo "Submitting events to Kafka topic: $TOPIC"

# Send some events
send_event "sensor-1" 20.5
sleep 1
send_event "sensor-1" 22.1
sleep 1
send_event "sensor-2" 18.0
sleep 1
send_event "sensor-1" 21.0
sleep 1
send_event "sensor-2" 19.5

echo "Done."
