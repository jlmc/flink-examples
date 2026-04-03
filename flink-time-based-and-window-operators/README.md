# Flink Windowing and Time-Based Operators

This module explores the **Flink Window API** and various time-based operators. It provides examples and explanations on how to process infinite data streams by splitting them into finite "buckets" or windows.

## Project Structure

This project is divided into several sub-modules, each focusing on a specific type of Window Assigner or Window Function:

*   **[tumbling-windows](./tumbling-windows)**: Fixed-size, non-overlapping windows.
*   **[sliding-windows](./sliding-windows)**: Fixed-size windows that "slide" based on a defined interval, allowing overlaps.
*   **[session-windows](./session-windows)**: Windows that close after a period of inactivity (gap).
*   **[global-windows](./global-windows)**: A single giant window per key, requiring a custom Trigger to fire.
*   **[count-windows](./count-windows)**: Windows grouped by a fixed number of events rather than time.
*   **[window-functions](./window-functions)**: Examples of `ReduceFunction`, `AggregateFunction`, and `ProcessWindowFunction`.

---

## Core Concepts

### What is Windowing?
Windowing is a technique used in stream processing to group data into finite chunks based on time or other criteria, allowing operations to be applied to these chunks. In Flink, windows can be:
*   **Time-driven** (`Time Window`)
*   **Data-driven** (`Count Window`)

### Keyed vs. Non-Keyed Windows

Before performing a window operation, you must specify whether the stream should be keyed:

*   **Keyed Windows** (`.keyBy(...).window(...)`):
    *   The stream is partitioned by a key.
    *   Computations are performed in parallel across multiple tasks.
    *   Each logical keyed stream is processed independently.
*   **Non-Keyed Windows** (`.windowAll(...)`):
    *   The stream is not partitioned.
    *   All windowing logic is performed by a **single task** (parallelism of 1).
    *   **Warning**: This can lead to performance bottlenecks for large data volumes.

---

## Window Assigners

1.  **Tumbling Windows**: Fixed size, no overlap. Ideal for hourly or daily reports.
2.  **Sliding Windows**: Fixed size but sliding. Useful for real-time trend analysis (e.g., "last 5 minutes of data, updated every 1 minute").
3.  **Session Windows**: Based on activity gaps. Perfect for analyzing user behavior sessions.
4.  **Global Windows**: Groups everything into one window. Requires a custom `Trigger` to produce results.
5.  **Count Windows**: Triggered after a specific number of elements (e.g., every 100 events).

---

## Processing Functions

Once data is grouped, it must be processed using one of these functions:

*   **Incremental Aggregations** (`ReduceFunction`, `AggregateFunction`): More efficient as Flink only stores the partial result (e.g., a running sum).
*   **Full Window Functions** (`ProcessWindowFunction`): Flink stores all elements of the window in memory and delivers them at once. Heavier but allows access to window metadata (like timestamps).

---

## Time and Lateness Logic

For robust stream processing, Flink provides:

*   **Watermarks**: Signals how much "event time" has progressed.
*   **Allowed Lateness**: Allows late-arriving data to be included in a window before it's destroyed.
*   **Side Outputs**: A mechanism to capture extremely late data that would otherwise be lost.

---

## How to Run

This project uses **Docker Compose** to manage the Flink and Kafka infrastructure.

Each sub-module contains:
- A `docker-compose.yaml` to spin up the environment.
- An `upload-job.sh` script to build and submit the Flink job.
- A `submit-events.sh` script to send test JSON events to Kafka.

Refer to the specific README in each directory for execution details.

---

## Documentation
For more details, refer to:
- [Apache Flink Windowing Documentation (Stable)](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/windows/)
- [Apache Flink Windowing Documentation (v1.20.x)](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/windows/)
