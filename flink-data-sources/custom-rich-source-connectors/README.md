# RichSourceFunction vs RichParallelSourceFunction (Flink)

Even though the names are similar, **their behaviour is very different**, and that has a direct impact on how your Flink job runs.

---

## 🔹 `RichSourceFunction<T>`

👉 **Non-parallel source**

```java
public abstract class RichSourceFunction<T>
    implements SourceFunction<T>
```

### Key characteristics:

* Always runs with **parallelism = 1**
* Even if you configure:

  ```java
  .setParallelism(4)
  ```

  👉 Flink **ignores it**
* Only **one instance** of the source exists
* Best suited when:

    * the source **cannot be parallelised**
    * there is a **single shared resource** (e.g. one connection)
    * you want simplicity or a teaching example

### Example:

```java
env.addSource(new SimpleRichSourceFunction())
   .print();
```

---

## 🔹 `RichParallelSourceFunction<T>`

👉 **Parallel source**

```java
public abstract class RichParallelSourceFunction<T>
    implements ParallelSourceFunction<T>
```

### Key characteristics:

* Supports **parallelism > 1**
* Each subtask is an **independent instance**
* `.setParallelism(n)` **works**
* You have access to:

  ```java
  getRuntimeContext().getIndexOfThisSubtask();
  getRuntimeContext().getNumberOfParallelSubtasks();
  ```
* Ideal when:

    * the input can be **split / sharded**
    * you want **scalability and higher throughput**
    * each subtask can work independently

### Example:

```java
env.addSource(new SimpleRichParallelSourceFunction())
   .setParallelism(4)
   .print();
```

---

## ⚠️ Common pitfall

```java
env.addSource(new RichSourceFunction<>())
   .setParallelism(4); // ❌ has no effect
```

The job will **still run with a single subtask**, without any warning.

---

## 🧠 Practical difference

| Aspect      | RichSourceFunction | RichParallelSourceFunction |
|-------------|--------------------|----------------------------|
| Parallelism | Always 1           | Configurable               |
| Instances   | 1                  | N                          |
| Subtasks    | Not relevant       | Essential                  |
| Scalability | ❌                  | ✅                          |

---

## 🧪 When to use each one

### Use `RichSourceFunction` when:

* the source **cannot be parallelised**
* there must be **only one producer**
* the state is **global**
* simplicity is more important than performance

### Use `RichParallelSourceFunction` when:

* the source can be **split**
* you need to **scale**
* you want better throughput
* each subtask can run independently

---

## 🔔 Important note (modern Flink)

Since **Flink 1.12+**, `SourceFunction` is **deprecated**.

The recommended approach is the **new Source API**:

```java
Source<T>
```

(with `SourceReader`, `SplitEnumerator`, native checkpointing, etc.)

That said:

* `RichSourceFunction` → still great for learning and demos
* `RichParallelSourceFunction` → very common in legacy code

---

## TL;DR 🧩

* `RichSourceFunction` → **non-parallel**
* `RichParallelSourceFunction` → **parallel**
* `.setParallelism()` **only works** with the parallel one
* For modern production code → `Source<T>`



---
Nice, let’s do it properly with the **new Source API** 👍
I’ll rewrite your **custom parallel source that emits `Long` values**, using **Flink 1.20 style**.

I’ll keep it **simple but correct**, so it’s easy to map from what you already had with `RichParallelSourceFunction`.

---

## 1️⃣ What replaces `RichParallelSourceFunction`?

Old API

```java
RichParallelSourceFunction<Long>
```

New API

```java
Source<Long, SplitT, CheckpointStateT>
```

Where:

* `Long` → output type
* `SplitT` → how the source is split for parallelism
* `CheckpointStateT` → state stored in checkpoints

For a simple generator, we’ll use:

* **one split per subtask**
* **no checkpointed state** (or minimal)

---

## 2️⃣ Simple design

We’ll implement:

* `Source<Long, NumberSplit, Void>`
* `NumberSplit` → represents a subtask range
* `NumberSplitEnumerator` → assigns one split per subtask
* `NumberSourceReader` → emits numbers

This is the **direct equivalent** of your `RichParallelSourceFunction`.

---

## 3️⃣ The Split

```java
import org.apache.flink.api.connector.source.SourceSplit;

public class NumberSplit implements SourceSplit {

    private final String splitId;

    public NumberSplit(String splitId) {
        this.splitId = splitId;
    }

    @Override
    public String splitId() {
        return splitId;
    }
}
```

---

## 4️⃣ The SourceReader (replaces `run()`)

```java
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.ReaderOutput;

import java.util.List;

public class NumberSourceReader implements SourceReader<Long, NumberSplit> {

    private volatile boolean running = true;
    private long counter = 0;

    @Override
    public void start() {}

    @Override
    public InputStatus pollNext(ReaderOutput<Long> output) throws Exception {
        if (!running) {
            return InputStatus.END_OF_INPUT;
        }

        output.collect(counter++);
        Thread.sleep(100);

        return InputStatus.MORE_AVAILABLE;
    }

    @Override
    public List<NumberSplit> snapshotState(long checkpointId) {
        return List.of();
    }

    @Override
    public void addSplits(List<NumberSplit> splits) {}

    @Override
    public void notifyNoMoreSplits() {}

    @Override
    public void close() {
        running = false;
    }
}
```

📌 This is where your old `while (running) { ctx.collect(...) }` logic lives now.

---

## 5️⃣ The SplitEnumerator (controls parallelism)

```java
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.SplitEnumeratorContext;

import java.util.ArrayList;
import java.util.List;

public class NumberSplitEnumerator implements SplitEnumerator<NumberSplit, Void> {

    private final SplitEnumeratorContext<NumberSplit> context;

    public NumberSplitEnumerator(SplitEnumeratorContext<NumberSplit> context) {
        this.context = context;
    }

    @Override
    public void start() {
        int parallelism = context.currentParallelism();
        List<NumberSplit> splits = new ArrayList<>();

        for (int i = 0; i < parallelism; i++) {
            splits.add(new NumberSplit("split-" + i));
        }

        context.assignSplits(splits);
    }

    @Override
    public void handleSplitRequest(int subtaskId, String requesterHostname) {}

    @Override
    public void addSplitsBack(List<NumberSplit> splits, int subtaskId) {}

    @Override
    public Void snapshotState(long checkpointId) {
        return null;
    }

    @Override
    public void close() {}
}
```

📌 This is what replaces:

```java
getRuntimeContext().getIndexOfThisSubtask()
```

---

## 6️⃣ The Source itself

```java
import org.apache.flink.api.connector.source.Source;
import org.apache.flink.api.connector.source.SourceReader;
import org.apache.flink.api.connector.source.SplitEnumerator;
import org.apache.flink.api.connector.source.Boundedness;
import org.apache.flink.core.io.SimpleVersionedSerializer;

public class NumberSource implements Source<Long, NumberSplit, Void> {

    @Override
    public Boundedness getBoundedness() {
        return Boundedness.CONTINUOUS_UNBOUNDED;
    }

    @Override
    public SourceReader<Long, NumberSplit> createReader(SourceReaderContext context) {
        return new NumberSourceReader();
    }

    @Override
    public SplitEnumerator<NumberSplit, Void> createEnumerator(
            SplitEnumeratorContext<NumberSplit> context) {
        return new NumberSplitEnumerator(context);
    }

    @Override
    public SplitEnumerator<NumberSplit, Void> restoreEnumerator(
            SplitEnumeratorContext<NumberSplit> context,
            Void checkpoint) {
        return new NumberSplitEnumerator(context);
    }

    @Override
    public SimpleVersionedSerializer<NumberSplit> getSplitSerializer() {
        return new NumberSplitSerializer();
    }

    @Override
    public SimpleVersionedSerializer<Void> getEnumeratorCheckpointSerializer() {
        return new VoidSerializer();
    }
}
```

---

## 7️⃣ Serializers (minimal versions)

```java
import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.*;

public class NumberSplitSerializer implements SimpleVersionedSerializer<NumberSplit> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public byte[] serialize(NumberSplit split) throws IOException {
        return split.splitId().getBytes();
    }

    @Override
    public NumberSplit deserialize(int version, byte[] serialized) {
        return new NumberSplit(new String(serialized));
    }
}
```

```java
import org.apache.flink.core.io.SimpleVersionedSerializer;

public class VoidSerializer implements SimpleVersionedSerializer<Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public byte[] serialize(Void obj) {
        return new byte[0];
    }

    @Override
    public Void deserialize(int version, byte[] serialized) {
        return null;
    }
}
```

---

## 8️⃣ Using it in your job

```java
StreamExecutionEnvironment env =
        StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());

env.fromSource(
        new NumberSource(),
        WatermarkStrategy.noWatermarks(),
        "Custom Number Source"
)
.setParallelism(4)
.print();

env.execute("New Source API Example");
```

✅ Parallel
✅ No `.returns()` needed
✅ No type-erasure issues
✅ Future-proof

---

# 🧠 Mapping old → new (mental model)

| Old API                      | New API                        |
|------------------------------|--------------------------------|
| `run()`                      | `pollNext()`                   |
| `cancel()`                   | `close()`                      |
| `Parallelism`                | `SplitEnumerator`              |
| `ctx.collect()`              | `ReaderOutput.collect()`       |
| `RichParallelSourceFunction` | `Source + Reader + Enumerator` |

---
