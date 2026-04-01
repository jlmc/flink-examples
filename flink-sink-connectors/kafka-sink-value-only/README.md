# Flink Kafka Sink Value-Only Example

This module provides an example of using the Flink Kafka Sink connector to write Value-Only messages to a Kafka topic.

## Prerequisites

- Docker and Docker Compose
- JDK 11 (or use the provided build script which uses Docker)
- Maven

## Delivery Semantics and Checkpointing

This example enables Flink checkpointing to ensure reliable data delivery.

### Checkpointing Configuration

In the `KafkaSinkValueOnlyExample.java`, checkpointing is enabled as follows:

```java
env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);
```

### Why use Checkpoints?

1.  **Reliability**: Checkpoints allow Flink to recover the state of the job in case of failure.
2.  **Delivery Guarantees**:
    *   **At-Least-Once**: By default, the `KafkaSink` provides at-least-once delivery guarantees if checkpointing is enabled. This means that in case of a failure, some messages might be redelivered to Kafka, but no messages will be lost.
    *   **Exactly-Once**: To achieve exactly-once semantics with Kafka, the sink uses the Kafka Transactions API. This requires setting the `DeliveryGuarantee.EXACTLY_ONCE` in the sink builder and ensures that even in the event of a failure, each message is written to the destination topic exactly once.

In this example, we use `EXACTLY_ONCE` checkpointing mode at the environment level, which is a prerequisite for any consistent delivery guarantee.

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
