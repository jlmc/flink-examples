# Kafka connector for Apache Flink

This project is an example of how to configure a simple Kafka source for Apache Flink using the `KafkaSource` connector. It demonstrates reading string values from a Kafka topic and printing them to the console.

## Prerequisites

- Docker and Docker Compose
- Java 17 or higher
- Maven

## Infrastructure

The project uses Docker Compose to manage the necessary infrastructure (Kafka and Kafka UI).

### Docker Compose Services

The following services are defined in the root `docker-compose.yaml`:

- **kafka**: The Kafka broker.
- **kafka-ui**: A web interface to manage and browse Kafka clusters.
- **kafka-init**: A helper service that automatically creates the required topics (`my-data-stream`, `user-events`) on startup.

To start the infrastructure, run from the project root:

```bash
docker compose up -d kafka kafka-ui kafka-init
```

### Web Interfaces

| Service          | URL                                                                                |
|:-----------------|:-----------------------------------------------------------------------------------|
| **Kafka UI**     | [http://localhost:8085](http://localhost:8085)                                     |
| **Flink Web UI** | [http://localhost:8081](http://localhost:8081) (Available when the job is running) |

## Kafka Topic Management

### Produce Messages (JSON)

You can produce JSON messages to the `my-data-stream` topic using the following command:

```bash
docker exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic my-data-stream <<EOF
{"id": 1, "message": "Hello Flink", "timestamp": "$(date +%s)"}
{"id": 2, "message": "Streaming with Kafka", "timestamp": "$(date +%s)"}
EOF
```

### Useful Commands

```bash
# List topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Describe topic
docker exec kafka kafka-topics --describe --topic my-data-stream --bootstrap-server localhost:9092

# Consume messages
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic my-data-stream --from-beginning --timeout-ms 5000

# Describe consumer group
docker exec kafka kafka-run-class kafka.admin.ConsumerGroupCommand --bootstrap-server localhost:9092 --group KafkaSourceConnectorStringValueOnly --describe
```

## Running the Example

1. Start the infrastructure: `docker compose up -d kafka kafka-ui kafka-init`.
2. Run the `KafkaSourceConnectorStringValueOnly` class from your IDE or using Maven.
3. Produce some messages using the commands above.
4. Check the console output or the Flink Web UI to see the processed data.
