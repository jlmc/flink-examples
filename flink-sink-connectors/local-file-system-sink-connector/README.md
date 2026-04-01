# Local File System Sink Connector

This module demonstrates how to write DataStream elements to the local file system using Apache Flink.
The `FileSink` connector is the standard way to write data to files.

The primary class in this module is:
- `LocalFileSystemSinkConnectorExample.java`: Demonstrates writing plain text to a local directory.

## Examples

### Local File System Sink Example (Text)

This example generates text lines and writes them into a local directory `/tmp/flink-output`.

```java
FileSink<String> fileSink = FileSink
        .<String>forRowFormat(new Path(outputFilePath), new SimpleStringEncoder<>())
        .withRollingPolicy(
                DefaultRollingPolicy.builder()
                        .withMaxPartSize(MemorySize.parse("250", MemorySize.MemoryUnit.BYTES))
                        .withRolloverInterval(Duration.ofSeconds(30))
                        .build()
        )
        .build();
```

## Running the Examples

1. Build the module:
   ```sh
   mvn clean install -DskipTests
   ```

2. Run the text example:
   ```sh
   mvn exec:java -Dexec.mainClass="io.github.jlmc.flink.sinks.LocalFileSystemSinkConnectorExample"
   ```

## Documentation

For more information, see the [Flink File Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/filesystem/).
