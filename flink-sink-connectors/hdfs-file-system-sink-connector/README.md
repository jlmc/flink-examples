# HDFS File System Sink Connector

This module provides an example of how to write DataStream elements to the HDFS (Hadoop Distributed File System) using Apache Flink.
The `FileSink` connector is used for this purpose.

## Example

```java
FileSink<String> sink = FileSink
    .forRowFormat(new Path("hdfs://localhost:9000/output"), new SimpleStringEncoder<String>("UTF-8"))
    .build();

stream.sinkTo(sink);
```

## Running the Example

Make sure you have a running Hadoop/HDFS cluster.
The `hadoop-client` dependency should be included in your project.

## Documentation

For more information, see the [Flink File Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/filesystem/).
