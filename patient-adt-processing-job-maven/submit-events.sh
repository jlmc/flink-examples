#!/bin/bash

# TOPIC name
TOPIC="hls-providers.hl7.adt"

# Function to send an event
send_event() {
  ACCOUNT_ID=$1
  PATIENT_ID=$2
  EVENT_TYPE=$3
  LOCATION_ID=$4
  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")

  JSON="{\"accountId\":\"$ACCOUNT_ID\",\"patientId\":\"$PATIENT_ID\",\"eventType\":\"$EVENT_TYPE\",\"locationId\":\"$LOCATION_ID\",\"eventTimestamp\":\"$TS\"}"

  echo "Sending: $JSON"
  echo "$JSON" | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic $TOPIC
}

echo "Submitting events to Kafka topic: $TOPIC"

send_event "acc-1" "pat-100" "ADMIT" "ER"
sleep 1
send_event "acc-1" "pat-100" "TRANSFER" "WARD-A"
sleep 1
send_event "acc-1" "pat-101" "ADMIT" "ER"
sleep 1
send_event "acc-1" "pat-100" "DISCHARGE" "WARD-A"
sleep 1
send_event "acc-2" "pat-900" "ADMIT" "ICU"

echo "Done."
