# Flink JDBC Sink Connector Example

This module provides an example of using the Flink JDBC Sink connector to write data to a PostgreSQL database.

## Prerequisites

- Docker and Docker Compose
- JDK 11 (or use the provided build script which uses Docker)
- Maven

## How to Run

### 1. Build the project

You can build the project using the provided script which uses a Docker container with Maven and JDK 11:

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Start the infrastructure

Start the Flink cluster and PostgreSQL database using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- `jobmanager` at [http://localhost:8081](http://localhost:8081)
- `taskmanager`
- `postgres` (with database `flink_db`, user `flink_user`, and password `flink_password`)
- `postgres_setup` (a temporary container to create the `patients` table)

### 3. Deploy the Flink job

Upload and run the shaded JAR:

```bash
chmod +x upload-job.sh
./upload-job.sh
```

### 4. Verify the data

You can check the data in PostgreSQL:

```bash
docker exec -it postgres psql -U flink_user -d flink_db -c "SELECT * FROM patients LIMIT 10;"
```

### 5. Stop the infrastructure

To stop all services:

```bash
docker-compose down
```

## Delivery Semantics and Transaction Control

The Flink JDBC Sink supports two delivery semantics:

### 1. At-Least-Once (Default)
In this mode, the sink guarantees that each record is written at least once to the database. It uses regular JDBC connections and manages transactions based on the `JdbcExecutionOptions`:
- **Batch Size (`withBatchSize`)**: The number of records to buffer before triggering a commit.
- **Batch Interval (`withBatchIntervalMs`)**: The maximum time to wait before triggering a commit, even if the batch size hasn't been reached.
- **Max Retries (`withMaxRetries`)**: The number of times to retry a failed batch commit.

This mode is used via `buildAtLeastOnce()` and is generally more performant as it doesn't require XA transactions.

### 2. Exactly-Once
To achieve exactly-once semantics, the sink uses the [Two-Phase Commit (2PC)](https://en.wikipedia.org/wiki/Two-phase_commit_protocol) protocol. This requires:
- **XA-compliant Database**: The target database (like PostgreSQL) must support XA transactions.
- **XADataSource**: You must provide a `SerializableSupplier<XADataSource>`.
- **Flink Checkpointing**: Checkpointing must be enabled in the `StreamExecutionEnvironment`.

In this mode, transactions are tied to Flink checkpoints. A transaction is started at the beginning of a checkpoint and prepared when the checkpoint completes. It is finally committed only when the checkpoint is successfully acknowledged by the JobManager.

Use `buildExactlyOnce()` to enable this mode.

---

## Example Code

### 1. New Sink API (Recommended - SinkV2)

The recommended way to use the JDBC Sink is via the `Sink` (SinkV2) interface using `JdbcSink.builder()`:

```java
String sql = "INSERT INTO patients (id, name, age) VALUES (?, ?, ?) " +
             "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age";

Sink<Patient> jdbcSink = JdbcSink.<Patient>builder()
        .withQueryStatement(sql, (statement, patient) -> {
            statement.setInt(1, patient.id);
            statement.setString(2, patient.name);
            statement.setInt(3, patient.age);
        })
        .withExecutionOptions(JdbcExecutionOptions.builder()
                .withBatchSize(100)
                .withBatchIntervalMs(200)
                .withMaxRetries(5)
                .build())
        .buildAtLeastOnce(new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl("jdbc:postgresql://postgres:5432/flink_db")
                .withDriverName("org.postgresql.Driver")
                .withUsername("flink_user")
                .withPassword("flink_password")
                .build());

env.fromSource(source, WatermarkStrategy.noWatermarks(), "jdbc-data-generator")
   .sinkTo(jdbcSink);
```

### 2. Deprecated SinkFunction API

The older way using the `SinkFunction` interface (via `JdbcSink.sink()`) is still available but deprecated:

```java
String sql = "INSERT INTO patients (id, name, age) VALUES (?, ?, ?) " +
             "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age";

SinkFunction<Patient> jdbcSink = JdbcSink.sink(
        sql,
        (statement, patient) -> {
            statement.setInt(1, patient.id);
            statement.setString(2, patient.name);
            statement.setInt(3, patient.age);
        },
        JdbcExecutionOptions.builder()
                .withBatchSize(100)
                .withBatchIntervalMs(200)
                .withMaxRetries(5)
                .build(),
        new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl("jdbc:postgresql://postgres:5432/flink_db")
                .withDriverName("org.postgresql.Driver")
                .withUsername("flink_user")
                .withPassword("flink_password")
                .build()
);

env.fromSource(source, WatermarkStrategy.noWatermarks(), "jdbc-data-generator")
   .addSink(jdbcSink);
```

## Documentation

For more information, see the [Flink JDBC Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/jdbc/).
