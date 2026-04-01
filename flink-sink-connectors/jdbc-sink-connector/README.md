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

## Example Code

The example uses `JdbcSink.sink` with an **UPSERT** (Insert or Update) SQL statement:

```java
String sql = "INSERT INTO patients (id, name, age) VALUES (?, ?, ?) " +
             "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, age = EXCLUDED.age";

JdbcSink.sink(
    sql,
    (statement, patient) -> {
        statement.setInt(1, patient.id);
        statement.setString(2, patient.name);
        statement.setInt(3, patient.age);
    },
    // ... execution and connection options
);
```

## Documentation

For more information, see the [Flink JDBC Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/jdbc/).
