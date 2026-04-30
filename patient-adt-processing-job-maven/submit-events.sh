#!/bin/bash

# TOPIC and event volume (can be overridden)
TOPIC="${TOPIC:-adt-events-data}"
EVENT_COUNT="${EVENT_COUNT:-500}"

echo "Submitting $EVENT_COUNT events to Kafka topic: $TOPIC"

generate_event() {
  INDEX=$1
  ACCOUNT_ID="acc-$((INDEX % 10))"
  PATIENT_ID="pat-$((100 + (INDEX % 250)))"
  LOCATION_ID="WARD-$((INDEX % 8))"
  EVENT_KEY="${ACCOUNT_ID}_${PATIENT_ID}"
  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")

  case $((INDEX % 4)) in
    0) EVENT_TYPE="ADMIT" ;;
    1) EVENT_TYPE="TRANSFER" ;;
    2) EVENT_TYPE="UPDATE" ;;
    *) EVENT_TYPE="DISCHARGE" ;;
  esac

  printf '%s|{"accountId":"%s","patientId":"%s","eventType":"%s","locationId":"%s","eventTimestamp":"%s"}\n' \
    "$EVENT_KEY" "$ACCOUNT_ID" "$PATIENT_ID" "$EVENT_TYPE" "$LOCATION_ID" "$TS"
}

for ((i=1; i<=EVENT_COUNT; i++)); do
  generate_event "$i"
done | docker exec -i kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic "$TOPIC" \
  --property parse.key=true \
  --property key.separator='|'

echo "Done. Produced $EVENT_COUNT events."
