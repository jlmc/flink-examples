# Flink data exchange strategies and algorithms

## 1. Forward Partitioner (forward)

The Forward Partitioner is the most efficient data distribution method in Flink. 
It directs data from a source task instance only to the corresponding task instance in the next operator, provided they are running on the same TaskManager.

* **Technical Description**: Forwards elements only to the locally running downstream operation. This avoids data serialization and network transfer, acting as the foundation for "Operator Chaining."
* **When to use**: Use this to maximize performance by eliminating network overhead when you want to maintain data locality.
* **Requirement**: The parallelism of the sending operator and the receiving operator must be identical.

```java
DataStream<String> source = env.socketTextStream("localhost", 9999);

// Explicitly forcing a forward partition (though often automatic)
source.map(value -> value.toUpperCase())
      .forward() 
      .print();
```



## 2. Shuffle Partitioner (shuffle)

The Shuffle Partitioner distributes data uniformly and randomly across all parallel instances of the downstream operator.

In the Shuffle each upstream record (source element) is sent only to one instance of the downstream operator, but the selection of that instance is random. This means that the data is distributed in a non-deterministic way, which can help to balance the load across all instances. 
The goal balances the workload for heavy sources or heavy computations.
The result is, if we have 100 messages and 4 downstream instances, each instance will receive approximately 25 messages, but which specific messages go to which instance is random.

* **Technical Description**: Distributes the data equally by selecting one output channel randomly based on a uniform distribution. This breaks any previous data affinity or grouping.
* **When to use**: Use this to rebalance the workload when you have "data skew" (some subtasks are busier than others) or when you want to ensure a fair distribution of elements regardless of their keys.
* **Cost**: High. It involves network I/O and serialization/deserialization costs as data is moved between different TaskManagers.

```java
// 1. Source with low parallelism (e.g., a single file or small Kafka topic)
DataStream<String> heavyLogStream = env.readTextFile("hdfs:///logs/huge_file.txt")
    .setParallelism(1); 

// 2. Distribute the heavy load across the cluster
DataStream<Result> processedStream = heavyLogStream
    .shuffle()                            // Randomly sends data to all available workers
    .map(new HeavyComputeFunction())      // A CPU-intensive operation
    .setParallelism(10);                  // Scale up to 10 workers
```

Check out the full example in `ShufflePartitionerExample.java`.

---

| Partitioner | Strategy         | Network Impact               | Parallelism Requirement |
|-------------|------------------|------------------------------|-------------------------|
| Forward     | (1:1)            | Minimal (Zero if chained)    | Must be identical       |
| Shuffle     | Random (Uniform) | High (Network/Serialization) | Can be different        |


---

In Apache Flink 1.20, both **Rebalance** and **Broadcast** are used to redistribute data across the cluster, but they serve opposite purposes: one is for **workload balance** and the other is for **data synchronization**.

## 1. Rebalance Partitioner (`rebalance`)

The **Rebalance** partitioner uses a **Round-Robin** algorithm. It cycles through all available downstream parallel instances one by one to distribute the data.

* **Logic:** If you have 3 downstream tasks (A, B, C), the first record goes to A, the second to B, the third to C, the fourth back to A, and so on.
* **When to use:** It is the "cure" for **Data Skew** (when some partitions have much more data than others). It ensures every CPU core does exactly the same amount of work.
* **Difference from Shuffle:** While `shuffle` is random, `rebalance` is deterministic and perfectly even.

### Real-World Example: Evenly Spreading Log Processing

Imagine reading from a Kafka topic where one partition is much larger than the others.

```java
// 1. Source reading from Kafka (potentially skewed)
DataStream<String> skewedLogs = env.fromSource(kafkaSource, ...);

// 2. Use rebalance to spread the load perfectly across 10 workers
DataStream<Result> balancedStream = skewedLogs
    .rebalance()                          // Uniformly distributes records (Round-Robin)
    .map(new ComplexLogParser())          // Every worker gets an equal number of logs
    .setParallelism(10);

```

**Line-by-Line:**

* **Line 2:** The source might be sending 90% of data to one worker because of how Kafka keys were set.
* **Line 5:** `.rebalance()` intercepts the stream and forces a 1-2-3-1-2-3 distribution pattern.
* **Line 7:** Now, all 10 parallel instances of `ComplexLogParser` stay equally busy, preventing a single "hot" worker from slowing down the whole job.

---

## 2. Broadcast Partitioner (`broadcast`)

The **Broadcast** partitioner sends **every single record** to **every single parallel instance** of the next operator.

* **Logic:** If you have 10 downstream tasks, each task receives its own copy of every element.
* **When to use:** For small datasets that contain "rules," "configurations," or "thresholds" that every worker needs to know to process the main data stream.
* **Warning:** Never broadcast large streams (like raw transactions), as this will multiply your network traffic by the number of workers and likely crash your TaskManagers.

### Real-World Example: Dynamic Fraud Thresholds

Imagine you have a main stream of transactions and a second stream of "Dynamic Limits" set by administrators.

```java
// 1. The main high-volume stream
DataStream<Transaction> transactions = env.fromSource(transSource, ...);

// 2. A small stream of configuration updates (e.g., "Limit = $500")
DataStream<Double> thresholdUpdate = env.fromSource(configSource, ...);

// 3. Broadcast the limit so EVERY worker knows the current threshold
DataStream<Transaction> alerts = transactions
    .connect(thresholdUpdate.broadcast()) // Every parallel worker gets the same limit value
    .process(new FraudDetector());        // Each worker compares its local trans to the global limit

```

**Line-by-Line:**

* **Line 5:** We read a configuration stream that might only have one message per hour.
* **Line 8:** By calling `.broadcast()`, we ensure that if we have 50 workers processing transactions, **all 50** receive the "Limit = $500" message.
* **Line 9:** The `FraudDetector` on TaskManager #10 knows the same limit as TaskManager #1, ensuring consistent business logic across the cluster.

---

### Summary Table

| Feature          | **Rebalance**                | **Broadcast**                    |
|------------------|------------------------------|----------------------------------|
| **Pattern**      | Round-Robin (1 to 1)         | Replication (1 to All)           |
| **Main Goal**    | Performance & Load Balancing | Consistency & Shared Logic       |
| **Network Cost** | Medium (moves data once)     | Very High (multiplies data by N) |
| **Typical Data** | Heavy raw events             | Small config/rule sets           |
