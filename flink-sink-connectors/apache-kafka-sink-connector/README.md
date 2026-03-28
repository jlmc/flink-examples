# Apache Kafka Sink Connector

This module provides an example of how to write DataStream elements to an Apache Kafka topic using Apache Flink.
The `KafkaSink` connector is the standard way to write data to Kafka.

## Example

```java
KafkaSink<String> sink = KafkaSink.<String>builder()
    .setBootstrapServers("localhost:9092")
    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
        .setTopic("my-topic")
        .setValueSerializationSchema(new SimpleStringSchema())
        .build()
    )
    .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
    .build();

stream.sinkTo(sink);
```

## Running the Example

You can start a local Kafka broker using the provided `docker-compose.yaml` file in the root directory.

## Documentation

For more information, see the [Flink Kafka Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/kafka/).
