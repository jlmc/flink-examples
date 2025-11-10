# Apache Flink

Apache Flink is an open source stream processing framework for distributed, high-performance always available and data streaming applications.

Supports for Batch processing, Graph processing, Iterative processing.

famous for very fast stream processing.

Flick is the next generation Engine for stream processing. It can considered as 4G for Big data processing.

* Speed wise: Flink > Spark > Hadoop
* Can process millions do messages per second in real time.
* Flink gives us low latency and high throughput applications.
* Robust Fault-tolerance, application can restart exactly from same point where it failed.
* Has rich set of libraries - for Graph processing, Machine learning, string handling, relational apis etc.
* Flink's application state is rescalable, possible to add resources while app is running. Also maintains exactly once semantics.


# Installing in a docker container

- [Install flink in personal docker container](docs/instalation/docker-personal-flink-images)
- [running flink in official docker container](docs/instalation/docker-official-flink-images)

# Architecture

- [architecture small description](docs/architecture)