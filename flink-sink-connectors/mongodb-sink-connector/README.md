# MongoDB Sink Connector

This module demonstrates how to write DataStream elements to a MongoDB collection using Apache Flink.
The `MongoSink` connector is used for this purpose.

## Example

```java
MongoSink<String> sink = MongoSink.<String>builder()
    .setUri("mongodb://localhost:27017")
    .setDatabase("my_db")
    .setCollection("my_collection")
    .setSerializationSchema(new MongoSerializationSchema<String>() {
        @Override
        public WriteModel<BsonDocument> serialize(String element, SerializationContext context) {
            return new InsertOneModel<>(BsonDocument.parse(element));
        }
    })
    .build();

stream.sinkTo(sink);
```

## Running the Example

Make sure you have a MongoDB instance running.
For a local MongoDB setup, refer to the [Docker Compose Services Guide](../../DOCKER-COMPOSE-SERVICES.md).

## Documentation

For more information, see the [Flink MongoDB Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/mongodb/).
