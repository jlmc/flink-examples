#!/bin/bash

# TOPIC and event volume (can be overridden)
TOPIC="${TOPIC:-adt-events-data}"
EVENT_COUNT="${EVENT_COUNT:-500}"

echo "Submitting $EVENT_COUNT events to Kafka topic: $TOPIC"

# Produz eventos ADT válidos (HL7 Axx) em sequências por paciente.
# Ciclo por paciente (5 passos):
# 1) A01 admissão
# 2) A02 transferência
# 3) A21 saída temporária (leave out)
# 4) A22 regresso (leave in)
# 5) A03 alta

generate_event() {
  INDEX=$1

  PATIENT_SEQUENCE=$(((INDEX - 1) / 5))
  STEP=$(((INDEX - 1) % 5))

  ACCOUNT_ID="acc-$((PATIENT_SEQUENCE % 10))"
  PATIENT_ID="pat-$((100 + (PATIENT_SEQUENCE % 250)))"
  EVENT_KEY="${ACCOUNT_ID}_${PATIENT_ID}"

  BASE_WARD=$((PATIENT_SEQUENCE % 8))
  NEXT_WARD=$(((BASE_WARD + 1) % 8))

  TS=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")

  case "$STEP" in
    0)
      EVENT_TYPE="A01"
      LOCATION_ID="WARD-${BASE_WARD}"
      ;;
    1)
      EVENT_TYPE="A02"
      LOCATION_ID="WARD-${NEXT_WARD}"
      ;;
    2)
      EVENT_TYPE="A21"
      LOCATION_ID="EXIT-${BASE_WARD}"
      ;;
    3)
      EVENT_TYPE="A22"
      LOCATION_ID="WARD-${BASE_WARD}"
      ;;
    *)
      EVENT_TYPE="A03"
      LOCATION_ID="DISCHARGE-${BASE_WARD}"
      ;;
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
