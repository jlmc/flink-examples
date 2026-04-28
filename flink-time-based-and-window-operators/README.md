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
Windowing is the mechanism used to group stream elements into finite sets based on time or count. 
It allows you to answer questions like "How many logins occurred in the last 5 minutes?" or "What is the average price of the last 100 transactions?"
Windowing is a technique used in stream processing to group data into finite chunks based on time or other criteria, allowing operations to be applied to these chunks. 
In Flink, windows can be:
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

The Window Assigner defines how elements are assigned to windows. 
This is done by specifying a `WindowAssigner` of your choice in the `window(...)` (for keyed streams) or the `windowAll(...)` (for non-keyed streams) method. The most common types of Window Assigners are:


1.  **Tumbling Windows**: Fixed size, no overlap. Ideal for hourly or daily reports.
2.  **Sliding Windows**: Fixed size but sliding. Useful for real-time trend analysis (e.g., "last 5 minutes of data, updated every 1 minute").
3.  **Session Windows**: Based on activity gaps. Perfect for analyzing user behavior sessions.
4.  **Global Windows**: Groups everything into one window. Requires a custom `Trigger` to produce results.
5.  **Count Windows**: Triggered after a specific number of elements (e.g., every 100 events).


---

## Window lifecycle

- A window is created as soon as the first element that belongs to it arrives.
- The window is completely removed when the time (event or precessing time) passes its end timestamp plus the user-specified allowed lateness.
- Each window will be **Trigger** and a **function** attached to it. The function will contain the computation to be applied to the content of the window, while the trigger specifies the conditions under which the window is considered ready for the function to be applied (e.g., when the watermark passes the end of the window or when a count threshold is reached).
- A trigger cam also decide to purge a window's contents and time between its creation and removal. Purging in the case only referes to the elements in the window, and not the window metadata (e.g., timestamps, state, etc.). This means that new data can still be added to that window. This is useful for scenarios where you want to keep the window active but reset its contents based on certain conditions (e.g., after processing a batch of events).

- The window remains active until it is triggered (e.g., when the watermark passes the end of the window or when a count threshold is reached).
- Once triggered, the window is processed and then destroyed, freeing up resources. 
- If late data arrives after the window has been destroyed, it can be handled using allowed lateness or side outputs, depending on the configuration.

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

### Local Execution (IDE)

If you are running the examples directly in your IDE (like IntelliJ IDEA) with **Java 17 or higher**, you must add the following arguments to the VM configuration to avoid JDK encapsulation errors (`InaccessibleObjectException`).

**In IntelliJ IDEA:**
1. Open the `Run/Debug Configurations` (top right).
2. Select your application configuration (e.g., `WindowOperatorExamples`).
3. Click `Modify options` -> `Add VM options`.
4. Paste the following into the `VM options` field:

```bash
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.time=ALL-UNNAMED
--add-opens=java.base/sun.net.util=ALL-UNNAMED
```

> **Note**: In the `basic-examples` module, the Flink dependencies are no longer marked as `provided` to facilitate direct execution from the IDE without extra configuration.

### Maven Execution

You can run the main example of the `basic-examples` module using the following command:

```bash
mvn exec:java -pl flink-time-based-and-window-operators/basic-examples
```

This command already includes the necessary arguments configured in the `pom.xml`.

### Docker Execution

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
