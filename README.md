# Apache Flink

Apache Flink is an open-source stream processing framework designed for distributed, high-performance, and always-on data streaming applications.

It supports **batch processing**, **graph processing**, and **iterative processing**. Flink is widely recognized for its **extremely fast stream processing capabilities**.

Flink is often considered the next-generation engine for stream processing—think of it as "4G for Big Data processing."

**Key Features:**
- **Performance:** Flink > Spark > Hadoop in terms of speed for streaming workloads.
- **High Throughput & Low Latency:** Can process millions of messages per second in real-time.
- **Fault Tolerance:** Provides robust fault-tolerance; applications can restart exactly from the point of failure.
- **Rich Libraries:** Includes libraries for graph processing, machine learning, string handling, relational APIs, and more.
- **Scalable State Management:** Application state is rescalable. Resources can be added dynamically while maintaining **exactly-once semantics**.

---

# Installing in a Docker Container

- [Install Flink in a personal Docker container](docs/instalation/docker-personal-flink-images)
- [Run Flink in the official Docker container](docs/instalation/docker-official-flink-images)

---

# Architecture

- [Architecture Overview](docs/architecture)

---

# Deployment

- [How to deploy a Flink job](docs/deploy-flink-job/README.md)
