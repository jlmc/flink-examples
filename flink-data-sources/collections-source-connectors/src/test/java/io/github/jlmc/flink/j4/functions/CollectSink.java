package io.github.jlmc.flink.j4.functions;

import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.util.concurrent.ConcurrentLinkedQueue;

 /// ## 1 CopyOnWriteArrayList
 ///
 /// ### How it works
 ///
 /// * Thread-safe **list** implementation
 /// * Every modification (`add`, `remove`, etc.) creates a **new copy of the underlying array**
 /// * Reads are **lock-free and very fast**
 /// * Writes are expensive due to copying the entire array
 ///
 /// ### Pros
 ///
 /// * Safe for **iteration while writing** without `ConcurrentModificationException`
 /// * Good if you mostly **read** and rarely **write**
 /// * Maintains **order of insertion** and supports **index-based access**
 ///
 /// ### Cons
 ///
 /// * Expensive for **high write rates**
 /// * Memory usage can spike if the list is large
 /// * Not ideal for pipelines with **lots of parallel writes**
 ///
 /// ---
 ///
 /// ## 2 ConcurrentLinkedQueue
 ///
 /// ### How it works
 ///
 /// * Thread-safe **queue** implementation
 /// * Non-blocking **linked nodes** (lock-free algorithms)
 /// * Inserts (`add`) and removes (`poll`) are cheap and scalable
 /// * Iteration is weakly consistent (may not reflect all concurrent updates)
 ///
 /// ### Pros
 ///
 /// * Very efficient for **high-frequency writes**
 /// * Low memory overhead
 /// * Perfect for **concurrent collection** in multi-threaded environments (like Flink with parallelism > 1)
 ///
 /// ### Cons
 ///
 /// * Does **not support random access** (no index-based get)
 /// * Iteration is **weakly consistent** (may not see all writes if iterating concurrently)
 /// * No `size()` guarantee in highly concurrent scenarios
 ///
 /// ---
 ///
 /// ## 3 Comparison Table
 ///
 /// | Feature         | CopyOnWriteArrayList          | ConcurrentLinkedQueue          |
 /// | --------------- | ----------------------------- | ------------------------------ |
 /// | Thread safety   | Yes                           | Yes                            |
 /// | Best for        | Mostly reads, few writes      | Many writes / high concurrency |
 /// | Iteration       | Safe during concurrent writes | Weakly consistent              |
 /// | Memory          | High for many writes          | Low                            |
 /// | Access by index | Yes                           | No                             |
 /// | Order           | Preserved                     | Preserved (FIFO)               |
 /// | Writes cost     | O(n) per write (array copy)   | O(1) amortized                 |
 ///
 /// ---
 ///
 /// ## 4 In Flink tests
 ///
 /// * **CopyOnWriteArrayList** → convenient if you need **order and random access**; small streams are fine
 /// * **ConcurrentLinkedQueue** → recommended for **real concurrent sinks**, **high parallelism**, or **large streams**, even if you just iterate at the end
 ///
 /// > In most Flink unit/integration tests, **ConcurrentLinkedQueue** is preferable for sinks because it’s fast, thread-safe, and avoids unnecessary array copying.
 ///
 /// ---
public class CollectSink<T> implements SinkFunction<T> {

    private final ConcurrentLinkedQueue<T> collectedValues;

    public CollectSink(ConcurrentLinkedQueue<T> initialValues) {
        assert initialValues != null;
        this.collectedValues = new ConcurrentLinkedQueue<>(initialValues);
    }

    @Override
    public void invoke(T value, Context context) {
        System.out.println(this.collectedValues + ": Adding value: " + value);
        collectedValues.add(value);
    }

 }
