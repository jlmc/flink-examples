# Flink HDFS Sink Connector Example

This example demonstrates how to use Apache Flink's `FileSink` to write data to **HDFS (Hadoop Distributed File System)**.

## Project Structure

- `HdfsFileSystemSinkConnectorExample.java`: Defines the Flink Job that generates messages and writes them to HDFS.
- `docker-compose.yaml`: Provisions an HDFS cluster (NameNode, DataNode) and a Flink cluster.
- `hadoop-data/`: Local directory where NameNode and DataNode data is persisted via Docker volumes.
- `hadoop.env`: Environment variables to configure the Hadoop cluster.

## How to Run

### 1. Build the Project

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Start the Environment

This will start HDFS and Flink:

```bash
docker-compose up -d
```

Make sure the NameNode is out of safe mode before submitting the job, or wait a few seconds.

### 3. Deploy the Job

```bash
chmod +x upload-job.sh
./upload-job.sh
```

**Note:** The Job generates data indefinitely to ensure files are finalized via checkpoints.

## Verify Results

You can check the generated files on HDFS using the `hdfs dfs` command inside the NameNode container:

```bash
docker exec -it namenode hdfs dfs -ls -R /flink/output/hdfs-sink
```

To view the content of a file:

```bash
docker exec -it namenode hdfs dfs -cat /flink/output/hdfs-sink/<bucket>/<file>
```

You can also access the NameNode web interface at [http://localhost:9870](http://localhost:9870) and browse the file system (Utilities -> Browse the file system).

## Hadoop Configuration in Flink

For Flink to be able to write to HDFS, it needs Hadoop libraries on its classpath. In this project's `pom.xml`, we include `hadoop-client` to make the example self-contained. In production environments, Flink usually uses the `HADOOP_CONF_DIR` environment variable to locate cluster configurations.
