# Kafka Source JSON Consumer

In this module, we demonstrate how to consume JSON messages from a Kafka topic using Apache Flink's `KafkaSource` and `JsonDeserializationSchema`.

## Overview

This example sets up a Flink DataStream job that:
1. Connects to a Kafka broker.
2. Reads messages from the `person-location-events` topic.
3. Deserializes JSON payloads into Java `record` objects (`PersonLocationEvent`).
4. Prints the deserialized events to the standard output.

This example is based on the official Flink documentation:
- [Kafka Source Connector](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/sources/kafka/)
- [JSON Format](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/formats/json/)

## Prerequisites

- **Java 17**
- **Maven 3.x**
- **Docker and Docker Compose** (for running Kafka)
- **Python 3** (optional, used by the producer script on macOS)

## Getting Started

### 1. Start Kafka Broker

Use the provided `docker-compose.yaml` in the project root to start the Kafka environment:

```bash
docker-compose up -d kafka-ui
```

The Kafka broker will be available at `localhost:9092`.

### 2. Dependencies

To use the JSON format in Flink, ensure the `flink-json` dependency is included in your `pom.xml`. Since it's usually provided by the Flink cluster, we set the scope to `provided`:

```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-json</artifactId>
    <version>1.20.3</version>
    <scope>provided</scope>
</dependency>
```

**Note:** In this module, we use Flink's shaded Jackson annotations (`org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty`) in the `PersonLocationEvent` model to ensure compatibility with Flink's internal Jackson version.

### 3. Run the Flink Job

#### From IntelliJ IDEA
- Open the project in IntelliJ.
- Locate the `.run/KafkaSourceJsonConsumerJob.run.xml` file.
- Right-click and select **Run 'KafkaSourceJsonConsumerJob'**.
- The Flink Web UI will be available at [http://localhost:8081](http://localhost:8081).

#### From Command Line
Build the shaded JAR:
```bash
mvn clean package -pl projects/flink-data-sources/kafka-source-json-consumer
```
Run the job (ensure a Flink cluster is running or run it locally):
```bash
java -cp projects/flink-data-sources/kafka-source-json-consumer/target/kafka-source-json-consumer-1.0-SNAPSHOT-shaded.jar io.github.jlmc.j9.KafkaSourceJsonConsumerJob
```

### 4. Produce Sample Data

Use the provided script to generate sample JSON messages in a loop:

```bash
./projects/flink-data-sources/kafka-source-json-consumer/docker-exec-kafka-producer-loop.sh
```

The script produces messages in the following format:
```json
{
  "person_id": "user-1",
  "latitude": 42.3118,
  "longitude": -72.6882,
  "event_timestamp": 1769358411300
}
```

## Project Structure

- `KafkaSourceJsonConsumerJob.java`: The main Flink job configuration.
- `PersonLocationEvent.java`: The POJO (Java record) representing the JSON event, using shaded Jackson annotations.
- `docker-exec-kafka-producer-loop.sh`: A shell script to simulate a data producer.
- `PersonLocationEventDeserializationTest.java`: Unit tests for verifying JSON deserialization.

## Troubleshooting

- **UnrecognizedPropertyException**: Ensure you are using the shaded Jackson annotations from `org.apache.flink.shaded.jackson2`. Standard Jackson annotations are ignored by Flink's `JsonDeserializationSchema`.
- **JsonParseException (macOS)**: If you see errors related to the timestamp (e.g., trailing 'N'), ensure the producer script is using `python3` for millisecond generation, as the BSD `date` command on macOS doesn't support `%N`.
