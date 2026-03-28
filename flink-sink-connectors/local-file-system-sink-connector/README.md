# Local File System Sink Connector

This module demonstrates how to write DataStream elements to the local file system using Apache Flink.
The `FileSink` connector is the standard way to write data to files.

## Example

```java
FileSink<String> sink = FileSink
    .forRowFormat(new Path("output"), new SimpleStringEncoder<String>("UTF-8"))
    .withRollingPolicy(
        DefaultRollingPolicy.builder()
            .withRolloverInterval(Duration.ofMinutes(15))
            .withInactivityInterval(Duration.ofMinutes(5))
            .withMaxPartSize(MemorySize.ofMebibytes(1024))
            .build())
    .build();

stream.sinkTo(sink);
```

## Documentation

For more information, see the [Flink File Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/filesystem/).
