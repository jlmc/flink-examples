Got it! Here’s the **updated README in English**, including Docker, the producer script, and the IntelliJ run
configuration.

---

# Kafka Key-Value Consumer with Flink

This project demonstrates a **Flink streaming job** that consumes key-value events from a Kafka topic, where the value
is a JSON representing `PersonLocationEvent`. The job deserializes both the key and value, processes the events, and
prints them to the console.

---

## **Project Structure**

```
src/main/java/io/github/jlmc/j10/
│
├── CustomKeyValueKafkaRecordDeserializationSchema.java
│   - Custom schema that reads the key (String) and value (JSON → PersonLocationEvent).
│
├── PersonLocationEvent.java
│   - POJO representing a person’s location event (fields: id, name, location, timestamp).
│
└── KafkaConsumeKeyValueJob.java
    - Flink job that consumes events from Kafka, deserializes them, and prints them to the console.
```

**Run Configuration:**
The project includes a ready-to-use IntelliJ run configuration:
`KafkaConsumeKeyValueJob.run.xml` — allows running the job directly from the IDE without using the terminal.

---

## **Prerequisites**

* Java 11+
* Apache Flink 1.16+ (or compatible version)
* Apache Kafka 3.x
* Docker and Docker Compose
* Maven or Gradle for building the project

---

## **Setup**

1. **Start Kafka using Docker**

It is necessary to start the Kafka container:

```bash
docker compose up -d kafka
```

Ensure Kafka is running before starting the Flink job.

2. **Create Kafka topic**

```bash
/usr/bin/kafka-topics.sh --create --topic person-location-events --bootstrap-server kafka:19092 --partitions 2 --replication-factor 1

```

3. **Build the project**

Using Maven:

```bash
mvn clean package
```

---

## **Producing messages with Key**

To produce messages with a key to Kafka, you can use the provided script:

```bash
docker-exec-kafka-producer-loop.sh
```

* This script sends JSON messages in the expected `PersonLocationEvent` format to the `person-location-events` topic.
* Each message has a **key** (String) and a **value** (JSON).

Example message:

* **Key**: `"user-123"`
* **Value**:

```json
{
  "id": "user-123",
  "name": "Alice",
  "location": "New York",
  "timestamp": 1674500000
}
```

---

## **Running the Flink Job**

Run locally with the web UI:

```bash
java -jar target/kafka-flink-consumer-job.jar
```

* Connects to Kafka at `localhost:9092` by default.
* Web UI available at [http://localhost:8081](http://localhost:8081)
* Expected console output:

```
Received event key: user-123, value: PersonLocationEvent{id='user-123', name='Alice', location='NYC', timestamp=1674500000}
```

Alternatively, run the job directly from IntelliJ using the included run configuration:
`KafkaConsumeKeyValueJob.run.xml`.

---

## **Kafka Consumer Configuration**

* **Bootstrap servers**: `localhost:9092`
* **Topic**: `person-location-events`
* **Group ID**: `KafkaSourceJsonConsumerJob`
* **Checkpointing**: enabled every 3 seconds (`EXACTLY_ONCE`)
* **Parallelism**: 1 (configurable)

---

## **Custom Deserialization**

`CustomKeyValueKafkaRecordDeserializationSchema` (or `PersonLocationEventKeyedDeserializationSchema`) handles:

* Key: `String`
* Value: JSON → `PersonLocationEvent` POJO
* Null-safe deserialization
* Logging:

    * `DEBUG` for record information
    * `TRACE` for emitted tuples
    * `WARN` for null keys or values

---

## **Extending the Project**

* Support multiple key types (e.g., Integer)
* Add Flink operators (filter, map, aggregations)
* Configure Kafka consumer properties (`max.poll.records`, `auto.offset.reset`, etc.)
* Implement watermarking for event-time processing

---
