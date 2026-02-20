# 🚀 Apache Flink Strategy: Side Outputs

This document outlines the architectural pattern of using **Side Outputs** in Apache Flink for high-performance data streaming pipelines.

## 📌 Overview

In traditional data pipelines, splitting a stream often requires multiple `.filter()` operations, leading to redundant processing. **Side Outputs** allow a single operator to emit multiple streams of different types simultaneously, optimizing CPU and memory usage by reading the data only once.

---

## 🛠️ Key Utilities and Use Cases

### 1. Dead Letter Queue (DLQ)

The most common utility. When an event arrives corrupted or fails schema validation (e.g., invalid JSON, null fields), it is diverted to a Side Output instead of crashing the Job or causing exceptions in the main flow.

* **Benefit:** Fault tolerance and isolation of "dirty" data.

### 2. Business Logic Branching (The Splitter)

Allows routing data to different sinks based on internal logic.

* *Example:* A transaction stream where purchases over $10,000 are sent to a "High Priority" Side Output for manual review, while others continue through the regular flow.

### 3. Late Data Handling

When working with **Windows**, events arriving after the window has closed (beyond the *Allowed Lateness*) are dropped by default. Side Outputs allow you to capture these "latecomers" for separate processing or auditing.

### 4. Real-time Monitoring & Alerts

Emitting technical metrics or security alerts to monitoring systems (like Prometheus, Slack, or ELK) without interfering with the main data transformation logic.

### 5. Multi-Typing (Hybrid Outputs)

Unlike the main stream, Side Outputs can emit objects of entirely different classes.

* **Main Stream:** `OrderObject`
* **Side Output 1:** `LogString` (Errors)
* **Side Output 2:** `AlertNotification` (Security)

---

## 📊 Comparison: Standard Filter vs. Side Output

| Feature         | Multiple Filters (`.filter`) | Side Output (`ctx.output`)      |
|-----------------|------------------------------|---------------------------------|
| **Data Passes** | Multiple (one per filter)    | **Single Pass**                 |
| **Data Typing** | Same as original stream      | **Can be completely different** |
| **Complexity**  | Simple, but inefficient      | Requires a `ProcessFunction`    |
| **Performance** |                              | ****                            |

---

## 💻 Implementation Pattern (Java)

### 1. Define the Output Tags

Tags must be static and typed to ensure data integrity during serialization.

```java
public static final OutputTag<String> DLQ_TAG = new OutputTag<String>("dead-letter-queue"){};

```

### 2. Implementation in the Processor

```java
public class MyProcessor extends ProcessFunction<Input, Output> {
    @Override
    public void processElement(Input value, Context ctx, Collector<Output> out) {
        if (isValid(value)) {
            // Main Flow
            out.collect(transform(value)); 
        } else {
            // Side Output (DLQ)
            ctx.output(DLQ_TAG, "Validation failed for ID: " + value.getId()); 
        }
    }
}

```

### 3. Extracting the Resulting Streams

```java
DataStream<Output> mainStream = input.process(new MyProcessor());
DataStream<String> sideStream = mainStream.getSideOutput(DLQ_TAG);

```

---

## 🧪 Testing with Kafka

To test Flink jobs that interact with Kafka, this project provides a JUnit 5 extension that manages a `KafkaContainer` using Testcontainers.

### 1. Using `@EnableKafka`

The simplest way is to use the `@EnableKafka` meta-annotation on your test class:

```java
@EnableKafka
class MyKafkaIT {
    @Test
    void testWithKafka() {
        // Retrieve the bootstrap servers
        String brokers = KafkaExtension.getBootstrapServers();
        
        // Alternatively, use the system property "brokers" which is automatically set
        String brokersProp = System.getProperty("brokers");
        
        // Setup your Kafka producer/consumer and Flink job...
    }
}
```

### 2. Kafka Extension Details

The `KafkaExtension` ensures that:
- A single Kafka container is shared across all test classes in the same suite for performance.
- The `brokers` system property is set before any test starts.
- It includes automatic Docker socket detection for macOS users (supporting Docker Desktop and OrbStack).

---

## 🚀 Conclusion

The Side Output strategy is essential for building robust, resilient, and performant streaming systems. It turns error handling and data segmentation into low-cost, high-visibility operations.

---

**Maintenance Tip:** Always monitor the data volume in your Side Outputs. A sudden spike in the DLQ might indicate an Upstream schema change or a latent bug in your validation logic.

---

In Apache Flink, while `map` and `flatMap` are simpler to write, they **cannot** produce Side Outputs. Only the `ProcessFunction` family has access to the `Context` object required to "side-channel" data.

Here is the breakdown of why and how you might combine them.

---

## 1. The Core Limitation

* **`map` / `flatMap`:** These are "One-In, One-Out" (or Zero-Out for flatMap) operations. They return a single `DataStream`. There is no mechanism to send a record to a different "branch" or tag.
* **`process`:** This is the "Swiss Army Knife." It provides a `Context` that allows you to call `ctx.output(tag, value)`.

---

## 2. Using `map` or `flatMap` BEFORE `process`

This is a very common "Cleaner" pattern. You use the simpler operators to prepare the data, and only use `process` for the final routing.

**The Strategy:**

1. **Map:** Convert raw strings into Objects or POJOs.
2. **Process:** Handle the business logic and split into **Success** vs. **DLQ**.

### Java Example:

```java
// 1. Clean data first using Map
DataStream<Transaction> cleanedStream = rawInput
    .map(value -> Transaction.parse(value)); // Simple transformation

// 2. Route data using Process
SingleOutputStreamOperator<Transaction> routedStream = cleanedStream
    .process(new ProcessFunction<Transaction, Transaction>() {
        @Override
        public void processElement(Transaction txn, Context ctx, Collector<Transaction> out) {
            if (txn.isValid()) {
                out.collect(txn); // Main Stream (Success)
            } else {
                ctx.output(ERROR_TAG, txn.getRawData()); // Side Output (DLQ)
            }
        }
    });

```

---

## 3. Can `flatMap` be an alternative?

Technically, **no**, it cannot replace Side Outputs if you need **separate streams** with **different types**.

However, people sometimes use `flatMap` as a "poor man's filter":

* **FlatMap Strategy:** If the data is bad, emit nothing. If it's good, emit the result.
* **The Problem:** You lose the bad data forever. You don't have a Dead Letter Queue (DLQ).

---

## 4. Comparison Table

| Feature             | Map / FlatMap          | ProcessFunction                   |
|---------------------|------------------------|-----------------------------------|
| **Simplicity**      | High (Lambda friendly) | Medium (Requires class/anonymous) |
| **Side Outputs**    | ❌ Not Possible         | ✅ Supported                       |
| **Access to State** | ❌ No                   | ✅ Yes (Keyed State)               |
| **Timers**          | ❌ No                   | ✅ Yes (Event/Processing time)     |
| **Ideal for...**    | Simple transformations | Complex logic, Routing, DLQs      |

---

## Summary for your README

If you are documenting this strategy, include this tip:

> **Architecture Tip:** Use `map` for simple, stateless cleaning to keep your code readable. Switch to `process` only when you need to route data to multiple Kafka topics (Success vs. Error) or when you need access to Flink's managed state and timers.

---

**Would you like me to show you how to use a `KeyedProcessFunction`? This allows you to handle Side Outputs while also remembering "state" (e.g., flagging a user after 3 failed transactions).**
