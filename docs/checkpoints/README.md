# Checkpoints in Apache Flink

Checkpoints are the fundamental mechanism in Apache Flink for ensuring **fault tolerance** and **state consistency**. They allow Flink to recover a Job's state after a failure, ensuring that processing continues from where it left off.

---

## 1. What are Checkpoints?

A checkpoint is a consistent, distributed snapshot of the state of all operators in a Flink Job at a specific point in time.

*   **How they work:** Flink inserts "barriers" (Checkpoint Barriers) into the data stream. When an operator receives a barrier, it snapshots its current state to persistent storage (usually a State Backend like RocksDB or FileSystem).
*   **Chandy-Lamport Algorithm:** Flink uses a variant of this algorithm to perform checkpoints without interrupting processing (asynchronous checkpoints).

---

## 2. EXACTLY_ONCE vs. AT_LEAST_ONCE

The choice between these two modes comes down to the balance between **absolute data integrity** and **response speed (latency)**.

### 2.1. Justification for `AT_LEAST_ONCE`
**"Priority: Speed and Simplicity"**

Choose this option if your system needs to react as quickly as possible and if the downstream system consuming the results can handle occasional duplicates.

*   **Low Latency:** Data is sent to the Sink as soon as it is processed, without waiting for checkpoint cycles.
*   **Performance:** Lower overhead on the Flink cluster and the external Broker (e.g., Kafka), as there is no complex transaction management.
*   **Checkpoint Failure Resilience:** Even if the checkpoint storage system is slow, data continues to flow.
*   **Ideal Scenario:** Real-time dashboards, security/bot detection systems where "blocking an IP twice" is not a serious issue.

### 2.2. Justification for `EXACTLY_ONCE`
**"Priority: Precision and Consistency"**

Choose this option if every event triggers a critical action that must not, under any circumstances, be duplicated.

*   **Total Integrity:** Guarantees that even if the Job fails, the result will not be sent/processed twice after recovery.
*   **Transactional Coordination:** Flink uses the *Two-Phase Commit* (2PC) protocol to ensure that the Job's internal state and the external system's state (e.g., Kafka) are in perfect sync.
*   **Latency Cost:** The "price" is that results may be held in the Sink until the next checkpoint completes successfully (barrier alignment).
*   **Ideal Scenario:** Financial systems, billing, or pipelines where downstream processing is not idempotent.

---

## 3. Decision Comparison Table

| Criterion | `AT_LEAST_ONCE` | `EXACTLY_ONCE` |
| :--- | :--- | :--- |
| **Latency** | Minimum (Immediate) | High (Depends on Checkpoint Interval) |
| **Duplicates** | Possible in case of failure | Impossible |
| **Configuration** | Simple | Demanding (Requires Kafka Transactions, etc.) |
| **Kafka Impact** | Slight | High (Creates many transaction markers) |

---

## 4. Verdict and Recommendations

### For Bot Detection / Security
Latency is usually the most critical factor. If a bot is attacking, you want the alert in 1-5 seconds, not 30-60 seconds. Therefore, **`AT_LEAST_ONCE` with a short checkpoint interval** is often the most effective pragmatic solution.

### For Financial Systems
Precision is mandatory. Use **`EXACTLY_ONCE`**, but be sure to configure the `checkpoint.interval` to a value that balances acceptable latency with cluster overhead (e.g., 5-10 seconds).

---

## 5. Configuration Example (Java - Flink 1.20)

In Flink 1.20, configurations have been reorganized with new prefixes (`execution.checkpointing.*`, `state.*`, etc.). While the programmatic API still supports old methods, the recommendation is to follow the new configuration structure.

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// Enable checkpoints every 10 seconds
env.enableCheckpointing(10000);

// Configure the mode
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

// --- Recommended Features and Configurations ---

// 1. Unaligned Checkpoints (Essential for handling Backpressure)
// Allows checkpoint barriers to overtake data buffers.
env.getCheckpointConfig().enableUnalignedCheckpoints();

// 2. Barrier Alignment with Timeout (Mixed)
// Tries aligned, but switches to unaligned if it takes more than 5s
env.getCheckpointConfig().setAlignedCheckpointTimeout(Duration.ofSeconds(5));

// 3. Checkpoint Retention (Externalized Checkpoints)
// Keeps the checkpoint even after Job cancellation
env.getCheckpointConfig().setExternalizedCheckpointRetention(
    ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);

// 4. Minimum pause between checkpoints (Avoids "checkpoint flooding")
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);

// 5. Maximum number of simultaneous checkpoints
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
```

---

## 6. State Backends (Where state is stored)

Flink offers two main State Backend implementations:

### 6.1. HashMapStateBackend
*   **Where it stores:** In the JVM Heap memory.
*   **Performance:** Extremely fast (direct access to Java objects).
*   **Limitation:** Limited by available RAM size. Snapshots can be heavy on Garbage Collection.
*   **Ideal for:** Small states, simple windows, low latency.

**How to implement (Java):**
```java
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;

// In StreamExecutionEnvironment:
env.setStateBackend(new HashMapStateBackend());
```

### 6.2. EmbeddedRocksDBStateBackend
*   **Where it stores:** In RocksDB (embedded key-value database) which writes to local disk.
*   **Performance:** Slightly slower than Heap (requires serialization/deserialization) but very efficient.
*   **Scalability:** Practically unlimited (limited only by disk). Supports Terabyte-sized states.
*   **Incremental Checkpoints:** Fundamental for large states; saves only what has changed since the last checkpoint.
*   **Ideal for:** Large states, long windows, high availability.

**How to implement (Java):**
```java
import org.apache.flink.state.rocksdb.EmbeddedRocksDBStateBackend;

// In StreamExecutionEnvironment (Incremental Checkpoints enabled by default):
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
```

### 6.3. Persistence on S3 (Storage)

Regardless of the chosen State Backend (HashMap or RocksDB), checkpoints must be persisted in a distributed file system to ensure recovery in case of cluster failure.

**Required Dependencies (Maven):**
For Flink 1.20, it is recommended to use the `flink-s3-fs-presto` plugin for checkpointing.

```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-s3-fs-presto</artifactId>
    <version>1.20.0</version>
</dependency>
```

**Programmatic Configuration (Java):**
```java
import org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage;
import org.apache.flink.configuration.Configuration;

// 1. Define storage location (S3)
env.getCheckpointConfig().setCheckpointStorage("s3://my-bucket/flink/checkpoints");

// 2. If using MinIO or a custom S3 endpoint, configure via Configuration
Configuration config = new Configuration();
config.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
config.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, "s3://flink/checkpoints");
config.set(CheckpointingOptions.SAVEPOINTS_DIRECTORY, "s3://flink/savepoints");

// Specific S3 options (MinIO example)
config.setString("s3.endpoint", "http://localhost:9000");
config.setString("s3.access-key", "minioadmin");
config.setString("s3.secret-key", "minioadmin");
config.setBoolean("s3.path.style.access", true); // Required for MinIO

StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
```

**Tip:** In production, prefer placing the `flink-s3-fs-presto` (or `hadoop`) JAR in the `/plugins/s3-fs-presto/` folder of your Flink distribution instead of including it in the application's Fat JAR.

---

## 7. What's New in Flink 1.20 (Highlights)

### 7.1. Unified File Merging (MVP)
Flink 1.20 introduced a mechanism to merge small checkpoint files into larger files. This reduces pressure on the file system (e.g., S3, HDFS) by creating fewer files.

**How to configure:**
It can be configured in `flink-conf.yaml` or programmatically in the Job code:

```java
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CheckpointingOptions;

Configuration config = new Configuration();
// Enable Unified File Merging
config.set(CheckpointingOptions.FILE_MERGING_ENABLED, true);

StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
```

### 7.2. Configuration Reorganization
Configuration options have been categorized for better clarity. Here are the most common option classes to use with the new Flink 1.20 configuration system:

*   `CheckpointingOptions`: Options for `execution.checkpointing.*`
*   `StateBackendOptions`: Options for `state.backend.*`
*   `StateRecoveryOptions`: Options for `execution.state-recovery.*`

**Programmatic usage example:**
```java
config.set(CheckpointingOptions.CHECKPOINTING_MODE, CheckpointingMode.EXACTLY_ONCE);
config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(10));
config.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
```

---

## 8. Difference between Checkpoints and Savepoints

| Feature | Checkpoint | Savepoint |
| :--- | :--- | :--- |
| **Purpose** | Automatic failure recovery. | Maintenance, code updates, migrations. |
| **Trigger** | Automatically managed by Flink. | Manually triggered by the user. |
| **Lifecycle** | Removed when the Job is canceled (by default). | Persisted until manually removed. |
