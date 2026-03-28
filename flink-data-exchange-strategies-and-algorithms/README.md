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

Check out the full example in `ForwardPartitionerExample.java`.



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

Check out the full example in `RebalancePartitionerExample.java`.

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

Check out the full example in `BroadcastPartitionerExample.java`.

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


---

The **Global Partitioner** is the most "centralizing" distribution algorithm in Apache Flink. While other algorithms focus on spreading the workload across multiple instances, the Global partitioner does the exact opposite.

Here is everything you need to know about it:

---

## 1. What is the Global Partitioner?

The **Global Partitioner** sends **all elements** from the upstream source to a **single, specific instance** of the downstream operator (specifically, the instance with index 0).

* **Strategy:** N-to-1 (Many-to-One).
* **Destination Parallelism:** Even if the downstream operator has a parallelism of 100, only **one** subtask will receive data. The other 99 will remain idle.

---

## 2. Technical Behavior

* **Routing:** It ignores any previous partitioning (like keys or round-robin) and forces all network traffic to a single point.
* **Network Cost:** This can be high because data from every TaskManager in the cluster must travel over the network to one central node.
* **Bottleneck Risk:** This is the primary danger. Since only one instance processes everything, it becomes a performance bottleneck. If the data volume is too high, this single subtask will lag, causing backpressure across the entire job.

---

## 3. Real-World Use Cases

You should only use `.global()` when your business logic requires a **total, singular view** of every single record in the stream.

### A. Global Sorting

If you need to sort every record in the stream by a timestamp, you cannot do this in parallel because each machine would only see a fraction of the data. You must bring all data to one place.

```java
// Force all events to one machine to perform a final sort
stream.global()
      .process(new GlobalSortFunction()); 

```

### B. Single File Output

If your requirement is to generate a single final report file (CSV or TXT) instead of multiple part-files distributed across HDFS or S3.

```java
stream.global()
      .writeAsText("s3://my-bucket/reports/final_single_report.txt");

```

---

## 4. Code Example (Flink 1.20)

Here is how it looks in a pipeline, with a line-by-line explanation:

```java
DataStream<Integer> numbers = env.fromElements(1, 2, 3, 4, 5, 6)
    .setParallelism(3); // Source spread across 3 workers

// 1. Applying the Global Partitioner
DataStream<Integer> globalStream = numbers.global();

// 2. Downstream operator
globalStream.map(value -> "Processed centrally: " + value)
    .setParallelism(4) // Even with 4 requested, only subtask 0 is used
    .print();

```

Check out the full example in `GlobalPartitionerExample.java`.

**Line-by-Line Explanation:**

1. **`.setParallelism(3)`**: We start with data distributed across 3 instances for speed.
2. **`.global()`**: Flink creates a "funnel." All data leaving those 3 workers is serialized and sent to worker "zero" of the next stage.
3. **`.setParallelism(4)`**: This is the crucial detail. Even though you asked Flink to use 4 instances for the `map`, the `global()` partitioner forces the use of only **one**. Flink will technically deploy 4 instances, but 3 of them will receive 0 records.

---

## 5. Comparison: Global vs. Others

| Algorithm     | Distribution            | Primary Use                            |
|---------------|-------------------------|----------------------------------------|
| **Global**    | All to Subtask 0        | Global sorting, single-file sinks.     |
| **Broadcast** | All to **All** Subtasks | Sending rules/configs to every worker. |
| **Rebalance** | Round-Robin to All      | Load balancing to fix data skew.       |

> **Performance Tip:** Never use `global()` for massive data volumes (e.g., Terabytes per hour). A single JVM cannot handle that throughput alone, leading to `OutOfMemory` errors or extreme latency.

---

## 6. Custom Partitioner

- In Flink 1.2, a CustomPartitioner (or specifically the Partitioner interface) is used when you need manual control over how data is distributed across parallel instances of an operator. 
- Unlike keyBy, which uses an internal hash function to ensure keys go to specific "key groups," a custom partitioner allows you to define the exact destination subtask index.

### - 1. **The Partitioner Interface**

To create a custom partitioner, you must implement the org.apache.flink.api.common.functions.Partitioner<K> interface. This interface has a single method:

```java
import org.apache.flink.api.common.functions.Partitioner;

public class MyCustomPartitioner implements Partitioner<Integer> {
    @Override
    public int partition(Integer key, int numPartitions) {
        // Return the index of the target subtask (0 to numPartitions - 1)
        // Example: Send even keys to even partitions, odd to odd
        return key % numPartitions;
    }
}
```

### 2. Usage in DataStream API

In Flink 1.2, you apply the partitioner using the partitionCustom method. You provide both your partitioner and a KeySelector to identify which part of the data the partitioner should act upon.

```java
DataStream<Tuple2<Integer, String>> source = ...;

DataStream<Tuple2<Integer, String>> partitioned = source.partitionCustom(
    new MyCustomPartitioner(), 
    value -> value.f0 // KeySelector: partition based on the first field (Integer)
);
```
In this example, the MyCustomPartitioner will receive the integer key from the first field of the tuple and determine which subtask should process each record based on the logic defined in the partition method.

### Key Differences: partitionCustom vs keyBy

| Feature          | partitionCustom                                                                | keyBy                                                         |
|------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------|
| **Stream Type**  | Returns a DataStream                                                           | Returns a KeyedStream                                         |
| **State**        | Does not allow Keyed State                                                     | Required for Keyed State                                      |
| **Control**      | Precise control over the subtask index, Full control over partitioning logic   | Managed by Flink via Hash/Key Groups                          |
| **Use Case**     | Handling extreme data skew manually                                            | Standard grouping and stateful operations                     |
| **--**           | --                                                                             | --                                                            |
| **Key Selector** | Requires a KeySelector to extract keys                                         | No need for a KeySelector (uses the entire record as the key) |
| **Use Case**     | Complex partitioning strategies (e.g., custom load balancing)                  | Grouping by key for stateful operations (e.g., aggregations)  |
| **Performance**  | Can be optimized for specific patterns but may require more development effort | Optimized for common use cases and easier to implement        |

> ⚠️ [!IMPORTANT]
> A common pitfall: Using partitionCustom does not turn a stream into a KeyedStream.  
> This means you cannot use functions like RichFlatMapFunction with ValueState or ListState immediately after a custom partitioner.  
> If you need keyed state, you must use keyBy.


### USE CASES

- In Flink 1.2, while keyBy is the bread and butter of stream processing, it isn't always the smartest tool in the shed. Because keyBy uses a deterministic hash, it can be very "fair"—and in distributed systems, "fair" isn't always "balanced."
- Here are the most common use cases for a CustomPartitioner:

1. Combating Severe Data Skew (The "Hot Key" Problem)
  - This is the #1 reason to use a custom partitioner. Imagine you are processing logs from a global e-commerce site.
  - The Issue: During a sale, the key "Electronics" might represent 80% of your traffic, while "Books" represents 1%.
  - The Result: If you keyBy("category"), one worker is on fire while the others are idle.
  - The Solution: Use a CustomPartitioner to distribute the "Electronics" key randomly or round-robin across multiple subtasks to spread the load.
