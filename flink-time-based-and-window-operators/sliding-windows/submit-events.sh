#!/bin/bash

# TOPIC name
TOPIC="access-attempts"

# Function to send an event
send_event() {
  USER_ID=$1
  SUCCESS=$2
  # Current timestamp in ISO 8601 format
  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
  
  JSON="{\"userId\":\"$USER_ID\",\"success\":$SUCCESS,\"timestamp\":\"$TS\"}"
  
  echo "Sending: $JSON"
  echo "$JSON" | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic $TOPIC
}

echo "Submitting access events to Kafka topic: $TOPIC"

# Send 5 failed attempts for bot-1 (should trigger alert)
for i in {1..5}; do
  send_event "bot-1" false
  sleep 0.2
done

# Send some successful logins
send_event "user-A" true
send_event "user-B" true

# Send 3 failed attempts for user-C (should NOT trigger alert)
send_event "user-C" false
send_event "user-C" false
send_event "user-C" false
send_event "user-C" false

echo "Done."
