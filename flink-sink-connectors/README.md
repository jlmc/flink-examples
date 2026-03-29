# Flink Sink Connectors

This module contains examples and documentation for various Flink Sink Connectors.
Data sinks consume DataStreams and forward them to files, sockets, external systems, or print them.

## Submodules

* [Socket Sink](socket-sink-connector): Consumes a DataStream and writes it to a socket.
* [Local File System Sink](local-file-system-sink-connector): Writes DataStream elements to the local file system.
* [CSV Sink](csv-sink-connector): Writes and reads DataStream elements in CSV format (local/remote).
* [S3 Sink](s3-sink-connector): Writes DataStream elements to AWS S3 (simulated with MinIO).
* [HDFS File System Sink](hdfs-file-system-sink-connector): Writes DataStream elements to HDFS.
* [JDBC Sink](jdbc-sink-connector): Writes DataStream elements to a relational database using JDBC.
* [Apache Kafka Sink](apache-kafka-sink-connector): Writes DataStream elements to an Apache Kafka topic.
* [MongoDB Sink](mongodb-sink-connector): Writes DataStream elements to a MongoDB collection.
* [Custom Sink](custom-sink-connector): Implementation of a custom sink connector.

## Documentation

For more information on Flink Sink Connectors, refer to the [official documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/overview/).
