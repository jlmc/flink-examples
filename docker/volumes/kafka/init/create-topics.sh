#!/bin/bash

# Wait for Kafka to be fully ready before attempting to create topics
echo "Waiting for Kafka to be ready..."
/usr/bin/kafka-broker-api-versions --bootstrap-server kafka:19092 > /dev/null 2>&1
while [ $? -ne 0 ]; do
  sleep 5
  /usr/bin/kafka-broker-api-versions --bootstrap-server kafka:19092 > /dev/null 2>&1
done
echo "Kafka is ready!"

# Define Topics to Create:
# kafka-topics.sh --create --topic <TOPIC_NAME> --bootstrap-server <HOST:PORT> --partitions <N> --replication-factor <N>

/usr/bin/kafka-topics --create --topic my-data-stream --bootstrap-server kafka:19092 --partitions 3 --replication-factor 1
/usr/bin/kafka-topics --create --topic user-events --bootstrap-server kafka:19092 --partitions 2 --replication-factor 1

echo "All required topics created successfully."