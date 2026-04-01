# Flink Kafka Sink Value-Only Example

This module provides an example of using the Flink Kafka Sink connector to write Value-Only messages to a Kafka topic.

## Prerequisites

- Docker and Docker Compose
- JDK 11 (or use the provided build script which uses Docker)
- Maven

## How to Run

### 1. Build the project

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Start the infrastructure

Start the Flink cluster and Kafka broker using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- `jobmanager` at [http://localhost:8081](http://localhost:8081)
- `taskmanager`
- `kafka` (accessible at `localhost:9092` externally, `kafka:19092` internally)
- `kafka-init` (to create the `value-only-topic` topic)
- `kafka-ui` at [http://localhost:8085](http://localhost:8085)

### 3. Deploy the Flink job

Upload and run the shaded JAR:

```bash
chmod +x upload-job.sh
./upload-job.sh
```

### 4. Verify the data

Check the messages in the Kafka topic using the Kafka console consumer:

```bash
docker exec -it kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic value-only-topic --from-beginning --max-messages 10
```

Alternatively, use the Kafka UI at [http://localhost:8085](http://localhost:8085).

### 5. Stop the infrastructure

```bash
docker-compose down
```
