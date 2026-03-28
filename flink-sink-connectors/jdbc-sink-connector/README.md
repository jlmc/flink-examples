# JDBC Sink Connector

This module demonstrates how to write DataStream elements to a relational database using JDBC in Apache Flink.
The `JdbcSink` is used for this purpose.

## Example

```java
JdbcSink.sink(
    "INSERT INTO my_table (id, value) VALUES (?, ?)",
    (statement, item) -> {
        statement.setInt(1, item.id);
        statement.setString(2, item.value);
    },
    JdbcExecutionOptions.builder()
        .withBatchSize(1000)
        .withBatchIntervalMs(200)
        .withMaxRetries(5)
        .build(),
    new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
        .withUrl("jdbc:postgresql://localhost:5432/my_db")
        .withDriverName("org.postgresql.Driver")
        .withUsername("my_user")
        .withPassword("my_password")
        .build()
);
```

## Running the Example

Make sure you have a database running.
For a local PostgreSQL setup, refer to the [Docker Compose Services Guide](../../DOCKER-COMPOSE-SERVICES.md).

## Documentation

For more information, see the [Flink JDBC Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/jdbc/).
