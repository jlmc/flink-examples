# Flink MySQL Sink Connector Example

This module provides an example of using the Flink JDBC Sink connector to write data to a MySQL database.

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

Start the Flink cluster and MySQL database using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- `jobmanager` at [http://localhost:8081](http://localhost:8081)
- `taskmanager`
- `mysql` (with database `flink_db`, user `flink_user`, and password `flink_password`)
- `mysql_setup` (a temporary container to create the `patients` table)

### 3. Deploy the Flink job

Upload and run the shaded JAR:

```bash
chmod +x upload-job.sh
./upload-job.sh
```

### 4. Verify the data

You can check the data in MySQL:

```bash
docker exec -it mysql mysql -u flink_user -pflink_password flink_db -e "SELECT * FROM patients LIMIT 10;"
```

### 5. Stop the infrastructure

To stop all services:

```bash
docker-compose down
```

## Example Code

The example uses the `JdbcSink` with `JdbcExecutionOptions` and `buildAtLeastOnce()`.

```java
// MySQL UPSERT syntax
final String sql = "INSERT INTO patients (id, name, age) VALUES (?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE name = VALUES(name), age = VALUES(age)";

Sink<Patient> jdbcSink = JdbcSink.<Patient>builder()
        .withQueryStatement(sql, new JdbcStatementBuilder<Patient>() {
            @Override
            public void accept(PreparedStatement statement, Patient patient) throws SQLException {
                statement.setInt(1, patient.id);
                statement.setString(2, patient.name);
                statement.setInt(3, patient.age);
            }
        })
        .withExecutionOptions(JdbcExecutionOptions.builder()
                .withBatchSize(100)
                .withBatchIntervalMs(200)
                .withMaxRetries(5)
                .build())
        .buildAtLeastOnce(new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                .withUrl("jdbc:mysql://mysql:3306/flink_db")
                .withDriverName("com.mysql.cj.jdbc.Driver")
                .withUsername("flink_user")
                .withPassword("flink_password")
                .build());

env.fromSource(source, WatermarkStrategy.noWatermarks(), "mysql-data-generator")
   .sinkTo(jdbcSink);
```

## Documentation

For more information, see the [Flink JDBC Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/jdbc/).
