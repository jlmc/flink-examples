# Apache Flink

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](#)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.flink/flink-core)](#)
[![Docker Pulls](https://img.shields.io/docker/pulls/apache/flink)](#)

**Apache Flink** is an open-source stream processing framework for **distributed, high-performance, and always-on data
streaming applications**.

It supports **batch processing**, **graph processing**, and **iterative processing**, and is widely recognized for its *
*extremely fast stream processing capabilities**.
Think of Flink as the **next-generation engine for stream processing**—like “4G for Big Data processing.”

In this examples we are using the flink version [1.20](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/)

---

## Table of Contents

1. [Key Features](#key-features)
2. [Installation](#installation)

    * [Docker](#installing-in-a-docker-container)
3. [Architecture](#architecture)
4. [Deployment](#deployment)

    * [Deployment Modes](docs/deploy-modes/README.md)
5. [Flink Local Environment for Development](#flink-local-environment-for-development)
6. [Flink Operator Parallelism](docs/operator-parallelism/README.md)

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

## Flink Local Environment for Development

* [Flink Local Environment for Development](projects/flink-local-environment-for-develop)

This setup allows you to run Flink locally with:

* **JobManager Web UI** for monitoring jobs
* **Custom logging** (colored console logs or JSON logs)
* **Checkpoints and savepoints** for stateful applications
* **Development-friendly configuration**

> Ideal for local development, debugging, and testing Flink jobs before deploying to production.

---

## Flink data streams api

- [From Collection, Elements, Sequence and Source](docs/data-streams-api/README.md)


