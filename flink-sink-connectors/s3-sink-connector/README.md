# Flink S3 Sink Connector Example

This module demonstrates how to use the Apache Flink `FileSink` to write data in CSV format to an S3-compatible storage (simulated by MinIO).

## Prerequisites

- Docker and Docker Compose installed.
- Java 17.
- Maven.

## Helper Scripts

Several scripts are provided in this module to facilitate development and testing:

- `build-jdk11.sh`: Builds the module using a Docker container with JDK 11. This ensures the shaded JAR is compatible with the Flink cluster.
- `upload-job.sh`: Uploads the shaded JAR to the Flink JobManager and automatically starts the job. It relies on the S3 configuration provided by the Flink cluster environment.
- `remove-all-jobs.sh`: Fetches all active jobs from the Flink JobManager and cancels them.

## Infrastructure Setup

A `docker-compose.yaml` file is provided to start a MinIO instance and automatically create a bucket named `flink-s3-bucket`.
The data is persisted in a local folder named `./minio_data` within the submodule directory.

To start MinIO:

```sh
docker-compose up -d
```

- **MinIO Console:** [http://localhost:9001](http://localhost:9001)
- **MinIO API:** [http://localhost:9000](http://localhost:9000)
- **Access Key:** `minio`
- **Secret Key:** `minio123`

## Delivery Semantics and Checkpointing

This example enables Flink checkpointing to ensure reliable data delivery.

### Checkpointing Configuration

In the `S3SinkConnectorExample.java`, checkpointing is enabled as follows:

```java
env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);
```

### Why use Checkpoints?

1.  **Reliability**: Checkpoints allow Flink to recover the state of the job in case of failure.
2.  **Delivery Guarantees**: The `FileSink` (used for S3) relies on Flink's checkpointing mechanism to provide "Exactly-Once" delivery guarantees. Data is written to in-progress files and only committed (renamed to their final name) when a checkpoint is successfully completed. Without checkpointing, the files would remain in an in-progress state and never be finalized.

## Running the Example

The `S3SinkConnectorExample` class uses a data generator to produce `Patient` objects and write them to the `s3://flink-s3-bucket/output` path in proper CSV format using `JacksonCsvEncoder`.

### 1. Build the module
```sh
mvn clean install -DskipTests
```

### 2. Run the example
You can run it from your IDE or using the following Maven command (ensure MinIO is running).

**Important for Java 17+:**
Due to an incompatibility between older Hadoop libraries and Java 17+, you **must** add the following JVM argument to avoid the `java.lang.UnsupportedOperationException: getSubject is not supported` error:

`--add-opens=java.base/javax.security.auth=ALL-UNNAMED`

#### Running with Maven:
```sh
mvn exec:java \
  -Dexec.mainClass="io.github.jlmc.flink.sinks.S3SinkConnectorExample" \
  -Dexec.executable="java" \
  -Dexec.args="--add-opens=java.base/javax.security.auth=ALL-UNNAMED -cp %classpath io.github.jlmc.flink.sinks.S3SinkConnectorExample"
```

#### Running in IntelliJ:
1. Go to **Run/Debug Configurations**.
2. Select **S3SinkConnectorExample**.
3. Add `-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 --add-opens=java.base/javax.security.auth=ALL-UNNAMED` to **VM options**.
4. Set the **working directory** to the module root (`flink-sink-connectors/s3-sink-connector`).

*Note: Since Flink dependencies are set to `provided`, you might need to run it with the `compile` scope if running via Maven/IDE without a Flink cluster. You can change the scope in the `pom.xml` temporarily or configure your IDE to "include dependencies with 'Provided' scope".*

## Key Configurations

The example configures the S3 endpoint and credentials in the Flink cluster configuration via `docker-compose.yaml`. It uses the `fs.s3a.*` keys to ensure compatibility with `flink-s3-fs-hadoop`.

## Documentation
- [Flink File Sink](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/file_sink/)
- [Flink S3 FileSystem](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/deployment/filesystems/s3/)
