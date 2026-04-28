# Apache Flink

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.flink/flink-core)](#)
[![Docker Pulls](https://img.shields.io/docker/pulls/apache/flink)](#)

**Apache Flink** is an open-source stream processing framework for **distributed, high-performance, and always-on data
streaming applications**.

It supports **batch processing**, **graph processing**, and **iterative processing**, and is widely recognized for its *
*extremely fast stream processing capabilities**.
Think of Flink as the **next-generation engine for stream processing**—like “4G for Big Data processing.”

In these examples we are using the Flink version [1.20](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/)
https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/dynamic-kafka/
---

## Table of Contents

1. [Key Features](#key-features)
2. [Installation](#installation)
    * [Docker](#installing-in-a-docker-container)
3. [Architecture](#architecture)
4. [Deployment](#deployment)
    * [Deployment Modes](docs/deploy-modes/README.md)
5. [Projects and Examples](#projects-and-examples)
    * [Flink Local Environment for Development](#flink-local-environment-for-development)
    * [Data Sources](#data-sources)
    * [Processing Examples](#processing-examples)
6. [Flink Operator Parallelism](docs/operator-parallelism/README.md)
7. [DataStream API](#datastream-api)
    * [Stream Data Transformations](docs/data-streams-api/DataStream-Transformations.md)

8. [Checkpoints and Fault Tolerance](docs/checkpoints/README.md)

---

## Key Features

* **High Performance:** Faster than Spark and Hadoop for streaming workloads.
* **High Throughput & Low Latency:** Can process millions of messages per second in real-time.
* **Fault Tolerance:** Robust recovery; applications can restart **exactly from the point of failure**.
* **Rich Libraries:** Includes libraries for graph processing, machine learning, relational APIs, and more.
* **Scalable State Management:** Application state is rescalable; resources can be added dynamically while maintaining *
  *exactly-once semantics**.

---

## Installation

### Installing in a Docker Container

* [Install Flink in a personal Docker container](docs/instalation/docker-personal-flink-images)
* [Run Flink in the official Docker container](docs/instalation/docker-official-flink-images)
* Local development infrastructure (Kafka, Redis, LocalStack, PostgreSQL, MongoDB): see [Docker Compose Services Guide](DOCKER-COMPOSE-SERVICES.md)

---

## Architecture

* [Architecture Overview](docs/architecture)

---

## Deployment

* [How to deploy a Flink job](docs/deploy-flink-job/README.md)
* [Deployment Modes](docs/deploy-modes/README.md)

---

## Projects and Examples

### Flink Local Environment for Development

* [Flink Local Environment for Development](flink-local-environment-for-develop)

This setup allows you to run Flink locally with:

* **JobManager Web UI** for monitoring jobs
* **Custom logging** (colored console logs or JSON logs)
* **Checkpoints and savepoints** for stateful applications
* **Development-friendly configuration**

> Ideal for local development, debugging, and testing Flink jobs before deploying to production.

---

### Data Sources

Examples of how to connect Flink to different data sources:

* **Collections and Sequences**: [From Collection, Elements, Sequence and Source](flink-data-sources/collections-source-connectors) ([Documentation](docs/data-streams-api/README.md))
* **Kafka**: [Kafka Source Connector](flink-data-sources/data-source-kafka-connector)
* **Kafka JSON**: [Kafka JSON Source Consumer](flink-data-sources/kafka-source-json-consumer)
* **Kafka Key-Value**: [Kafka Key-Value Source Consumer](flink-data-sources/kafka-source-key-value-consumer)
* **MongoDB**: [MongoDB Source Connector](flink-data-sources/data-source-mongodb)
* **Custom Rich Source**: [Custom Rich Source Connectors](flink-data-sources/custom-rich-source-connectors)

---

### Sink Connectors

Examples of how to connect Flink to different data sinks:

* **Socket**: [Socket Sink Connector](flink-sink-connectors/socket-sink-connector)
* **Local File System**: [Local File System Sink Connector](flink-sink-connectors/local-file-system-sink-connector)
* **HDFS**: [HDFS File System Sink Connector](flink-sink-connectors/hdfs-file-system-sink-connector)
* **JDBC**: [JDBC Sink Connector](flink-sink-connectors/jdbc-sink-connector)
* **Kafka**: [Apache Kafka Sink Connector](flink-sink-connectors/apache-kafka-sink-connector)
* **MongoDB**: [MongoDB Sink Connector](flink-sink-connectors/mongodb-sink-connector)
* **Custom Sink**: [Custom Sink Connector](flink-sink-connectors/custom-sink-connector)


### Processing Examples

Various processing patterns using Flink:

* **Hello World**: [Simple Flink Application](flink-hello-world)
* **Batch Processing**: [Word Count (Batch)](flink-works-counter-batch-processing)
* **Stream Processing**: [Word Count (Streaming)](flink-works-counter-stream-processing)
* **Socket Streaming**: [Socket Word Count](flink-works-counter-stream-socket-processing)
* **Java 8 Lambdas**: [DataStream API with Java 8 Lambdas](flink-data-stream-api-and-Java-8-Lambda-expression)
* **Stream Data Transformations**: [DataStream API Transformations Examples](flink-stream-data-transformations)
    * [Basic Transformations](flink-stream-data-transformations/src/main/java/io/github/jlmc/flink/transformations/BasicTransformationsExample.java) ([Test](flink-stream-data-transformations/src/test/java/io/github/jlmc/flink/transformations/BasicTransformationsExampleTest.java))
    * [KeyedStream Transformations](flink-stream-data-transformations/src/main/java/io/github/jlmc/flink/transformations/KeyedStreamTransformationsExample.java) ([Test](flink-stream-data-transformations/src/test/java/io/github/jlmc/flink/transformations/KeyedStreamTransformationsExampleTest.java))
    * [Multistream Transformations](flink-stream-data-transformations/src/main/java/io/github/jlmc/flink/transformations/MultistreamTransformationsExample.java) ([Test](flink-stream-data-transformations/src/test/java/io/github/jlmc/flink/transformations/MultistreamTransformationsExampleTest.java))
    * [Distribution Transformations](flink-stream-data-transformations/src/main/java/io/github/jlmc/flink/transformations/DistributionTransformationsExample.java) ([Test](flink-stream-data-transformations/src/test/java/io/github/jlmc/flink/transformations/DistributionTransformationsExampleTest.java))
    * [KeyedProcessFunction](flink-stream-data-transformations/src/main/java/io/github/jlmc/flink/transformations/KeyedProcessFunctionExample.java) ([Test](flink-stream-data-transformations/src/test/java/io/github/jlmc/flink/transformations/KeyedProcessFunctionExampleTest.java))
* **Multistream Transformations (connect)**: [DataStream API Connect Transformations](flink-stream-multistream-transformations)
    * [Connect Multiple DataStreams](flink-stream-multistream-transformations/src/main/java/io/github/jlmc/flink/multistream/ConnectMultipleDataStreamsToOneDataStreamExample.java) ([Test](flink-stream-multistream-transformations/src/test/java/io/github/jlmc/flink/multistream/ConnectMultipleDataStreamsToOneDataStreamExampleTest.java))
    * [Fire Alerting (CoMapFunction)](flink-stream-multistream-transformations/src/main/java/io/github/jlmc/flink/multistream/DataStreamConnectorForFireAlerting.java) ([Test](flink-stream-multistream-transformations/src/test/java/io/github/jlmc/flink/multistream/DataStreamConnectorForFireAlertingTest.java))
    * [Currency Converter (Broadcast CoMapFunction)](flink-stream-multistream-transformations/src/main/java/io/github/jlmc/flink/multistream/CurrencyConverterCoMapFunction.java) ([Test](flink-stream-multistream-transformations/src/test/java/io/github/jlmc/flink/multistream/CurrencyConverterCoMapFunctionTest.java))
    * [Fraud Detector (CoFlatMapFunction)](flink-stream-multistream-transformations/src/main/java/io/github/jlmc/flink/multistream/FraudDetectorCoFlatMapFunction.java) ([Test](flink-stream-multistream-transformations/src/test/java/io/github/jlmc/flink/multistream/FraudDetectorCoFlatMapFunctionTest.java))
    * [Real-Time Inventory Manager (CoProcessFunction)](flink-stream-multistream-transformations/src/main/java/io/github/jlmc/flink/multistream/RealTimeInventoryManagerCoProcessFunction.java) ([Test](flink-stream-multistream-transformations/src/test/java/io/github/jlmc/flink/multistream/RealTimeInventoryManagerCoProcessFunctionTest.java))
    * [DataStream Inner Join](flink-stream-multistream-transformations/src/main/java/io/github/jlmc/flink/multistream/DataStreamInnerJoin.java) ([Test](flink-stream-multistream-transformations/src/test/java/io/github/jlmc/flink/multistream/DataStreamInnerJoinTest.java))

---

---

## DataStream API

* [Stream Data Transformations](docs/data-streams-api/DataStream-Transformations.md) ([Portuguese Version](docs/data-streams-api/DataStream-Transformations_PT.md))
