# Stream data transformations

* A stream transformation is applied on one or more streams and converts them into one or more output streams. Writing a DataStream API program essentially boils down to combining such transformations to create a dataflow graph that implements the application logic.
* Most stream transformations are based on user-defined functions. The functions encapsulate the user application logic and define how the elements of the input stream are transformed into the elements of the output stream.
* Most function interfaces are designed as SAM (single abstract method) interfaces and they can be implemented as Java 8 lambda functions. The Scala DataStream API also has built-in support for lambda functions.
* The DataStream API provides transformations for the most common data transformation operations. We present the transformations of the DataStream API in four categories:
  * **Basic transformations** are transformations on individual events.
  * **KeyedStream transformations** are transformations that are applied to events in the context of a key.
  * **Multistream transformations** merge multiple streams into one stream or split one stream into multiple streams.
  * **Distribution transformations** reorganize stream events.



---

## 1. Basic Transformations

These operate independently on each event. The most common example is `Map`, which transforms one element into another.
These act on individual events. Common operators include `map`, `filter`, and `flatMap`.
```java
// Example: Converter a string into upper case using a Map transformation
DataStream<String> input = env.fromElements("flink", "stream", "data");

DataStream<String> upperCase = input.map(s -> s.toUpperCase());
```

## 2. KeyedStream Transformations

These require a `keyBy` call first. 
This logically partitions the stream so that all elements with the same key are processed by the same operator instance.
```java
// Example: Word count (aggregating by word)
DataStream<WordEntry> stream = env.fromElements(
    new WordEntry("apple", 1), 
    new WordEntry("apple", 1), 
    new WordEntry("banana", 1)
);

DataStream<WordEntry> counts = stream
    .keyBy(entry -> entry.word) // Partition by the "word" field
    .reduce((val1, val2) -> new WordEntry(val1.word, val1.count + val2.count));
```

```java
// Example: Sum values by category
DataStream<SensorReading> readings = ...;

DataStream<SensorReading> totals = readings
    .keyBy(sensor -> sensor.id)
    .sum("value"); // Sums the "value" field for each sensor ID.
```

## 3. Multistream Transformations

Used to unite (`union`) or connect (`connect`) different streams. The `connect` is very powerful because it allows two streams to share state, although they retain their original types.
```java
// Example: union of two input data of the same type
DataStream<String> stream1 = env.fromElements("A", "B");
DataStream<String> stream2 = env.fromElements("C", "D");

DataStream<String> merged = stream1.union(stream2);
```

These involve merging or connecting different streams. While `union` merges streams of the same type, `connect` allows you to process two different types of streams together.
```java
// Example: Connect a data stream with a control/rules stream
DataStream<String> data = env.fromElements("user_1", "user_2");
DataStream<Boolean> control = env.fromElements(true);

DataStream<String> result = data.connect(control.broadcast())
    .flatMap(new CoFlatMapFunction<String, Boolean, String>() {
        private boolean shouldProcess = true;

        @Override
        public void flatMap1(String value, Collector<String> out) {
            if (shouldProcess) out.collect("Processing: " + value);
        }

        @Override
        public void flatMap2(Boolean value, Collector<String> out) {
            shouldProcess = value; // Update behavior based on the second stream
        }
    });
```

### 4. Distribution Transformations

These reorganize how data is physically sent to the next parallel task. They are often used to fix performance issues like "data skew."
```java
// Example: Rebalance data to fix uneven load across CPU cores
DataStream<Long> heavyData = env.fromSequence(1, 10000);

DataStream<Long> output = heavyData
    .rebalance() // Evenly distributes data in a Round-Robin fashion
    .map(val -> performHeavyComputation(val));
```

```java
// Example: Redistribute the data evenly (Round-Robin)
DataStream<Long> numbers = env.fromSequence(1, 1000);

DataStream<Long> balanced = numbers
    .rebalance() // Ensures that each worker receives the same amount.
    .map(n -> n * 2);
```


## Key Technical Notes for Flink 1.20:
- Java Lambdas: Flink 1.20 fully supports Java 8+ lambdas, but remember that for complex types, you might still need to use .returns(Types...) to help Flink's Type Extraction system.
- Unified Sink: Since version 1.15 and refined in 1.20, it is recommended to use the new SinkV2 API for outputs, which handles batch and stream modes more consistently.

---

# KeyedProcessFunction example and ir explanation

The `KeyedProcessFunction` is often called the "Swiss Army Knife" of the DataStream API. It is a low-level stream processing operation that gives you full control over two critical components: **State** (remembering information across events) and **Timers** (taking action based on time passing).

### How it Works

When you use a `KeyedProcessFunction`, Flink routes all events with the same key to the same instance of your function. It provides:

1. **Context**: Access to the current key and timestamp.
2. **State**: Fault-tolerant storage (like a built-in database) scoped to that specific key.
3. **TimerService**: The ability to register "callbacks" in the future (e.g., "If I don't see another event for this user in 1 minute, do X").

---

### Practical Example: Inactivity Alert

Imagine you want to send an alert if a specific sensor stops sending data for more than 10 seconds.

```java
public class InactivityAlertFunction extends KeyedProcessFunction<String, SensorReading, String> {

    // State to keep track of the registered timer's timestamp
    private ValueState<Long> timerState;

    @Override
    public void open(OpenContext ctx) {
        // Initialize state
        timerState = getRuntimeContext().getState(new ValueStateDescriptor<>("timer-state", Long.class));
    }

    @Override
    public void processElement(SensorReading value, Context ctx, Collector<String> out) throws Exception {
        // 1. Calculate a timeout timestamp (10 seconds from now)
        long currentTime = ctx.timerService().currentProcessingTime();
        long timeout = currentTime + 10_000;

        // 2. Clean up any existing timer for this key
        Long lastTimer = timerState.value();
        if (lastTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(lastTimer);
        }

        // 3. Register the new timer and save it to state
        ctx.timerService().registerProcessingTimeTimer(timeout);
        timerState.update(timeout);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) {
        // This code runs only when 10 seconds pass without processElement being called
        out.collect("Alert: Sensor " + ctx.getCurrentKey() + " has been inactive for 10s!");
        timerState.clear();
    }
}

```

---

### Key Components Explained

| Component              | Description                                                                                       |
|------------------------|---------------------------------------------------------------------------------------------------|
| **`processElement()`** | Called for every record. Here you update state and manage timers.                                 |
| **`onTimer()`**        | Called when a registered timer fires. This is where you handle timeouts or periodic logic.        |
| **`Context`**          | Allows you to query the current timestamp and interact with the `TimerService`.                   |
| **`ValueState`**       | A persistent variable. If the Flink cluster crashes, Flink restores this value from a checkpoint. |

### Why use this over a simple `Map`?

A `Map` or `Filter` is **stateless**; it forgets the event as soon as it's processed. The `KeyedProcessFunction` is **stateful**. It allows you to build complex logic like:

* **Sessionization:** Grouping user clicks into a session that expires after 30 minutes.
* **Deduplication:** Checking if you've seen a specific ID in the last hour.
* **Fraud Detection:** Comparing a current transaction against the average of the last 10 transactions for that specific user.

Would you like me to show you how to test this function using Flink's **TestHarness**, or shall we look at how to handle **Event Time** instead of Processing Time?
