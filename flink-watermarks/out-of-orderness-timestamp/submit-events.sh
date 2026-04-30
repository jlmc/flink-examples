#!/bin/bash

# TOPIC name
TOPIC="fhir-adt-events"

# Function to send an event
send_event() {
  MSG_ID=$1
  PATIENT_ID=$2
  FACILITY_ID=$3
  EVENT_TYPE=$4
  EVENT_TS=$5

  JSON="{\"messageId\":\"$MSG_ID\",\"patientId\":\"$PATIENT_ID\",\"facilityId\":\"$FACILITY_ID\",\"eventType\":\"$EVENT_TYPE\",\"eventTimestamp\":\"$EVENT_TS\"}"
  
  echo "Sending: $JSON"
  echo "$JSON" | docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic $TOPIC
}

echo "Submitting events to Kafka topic: $TOPIC"

# Send more out-of-order ADT events (2 hospitals: lisbon and huc)
send_event "msg-1001" "pat-100" "hospital-lisbon" "ADT_A01" "2026-04-30T10:00:01.000Z"
sleep 1
send_event "msg-1002" "pat-200" "hospital-huc" "ADT_A03" "2026-04-30T10:00:12.000Z"
sleep 1
send_event "msg-1003" "pat-300" "hospital-lisbon" "ADT_A01" "2026-04-30T10:00:02.000Z"
sleep 1
send_event "msg-1004" "pat-400" "hospital-huc" "ADT_A02" "2026-04-30T10:00:19.000Z"
sleep 1
send_event "msg-1005" "pat-500" "hospital-lisbon" "ADT_A03" "2026-04-30T10:00:25.000Z"
sleep 1
send_event "msg-1006" "pat-600" "hospital-huc" "ADT_A01" "2026-04-30T10:00:08.000Z"
sleep 1
send_event "msg-1007" "pat-700" "hospital-lisbon" "ADT_A02" "2026-04-30T10:00:15.000Z"
sleep 1
send_event "msg-1008" "pat-800" "hospital-huc" "ADT_A03" "2026-04-30T10:00:05.000Z"
sleep 1
send_event "msg-1009" "pat-900" "hospital-lisbon" "ADT_A01" "2026-04-30T10:00:29.000Z"
sleep 1
send_event "msg-1010" "pat-910" "hospital-huc" "ADT_A02" "2026-04-30T10:00:17.000Z"
sleep 1
send_event "msg-1011" "pat-920" "hospital-lisbon" "ADT_A03" "2026-04-30T10:00:11.000Z"
sleep 1
send_event "msg-1012" "pat-930" "hospital-huc" "ADT_A01" "2026-04-30T10:00:27.000Z"

echo "Done."
