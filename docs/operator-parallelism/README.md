# **Operator Parallelism in Apache Flink**

## **1. What Is Parallelism?**

In Flink, an application (job) consists of multiple **tasks**, which may represent:

* Sources
* Transformations / operators
* Sinks

Each task can be executed by multiple parallel instances called **subtasks**.

- ➡️ **Parallelism** = number of running instances (subtasks) for a given operator.
- ➡️ Each subtask processes a *partition* of the operator’s input stream.

Example:
If a map operator has parallelism **4**, then Flink creates four independent instances of this map operator running in parallel.

---

## **2. Maximum Parallelism**

Flink also defines a **maximum parallelism**, which is:

* The *upper bound* for how much the operator can scale in the future.
* Critical for stateful operators (affects key-group partitioning).
* Set once during job creation; cannot be changed without state repartitioning.

➡️ **Job maximum parallelism = max(maximum parallelism of all operators).**

---

## **3. Where Flink Parallelism Can Be Configured**

Flink parallelism can be specified at *four different levels*.
The **priority order** is:

### **1. Operator-level parallelism (highest priority)**

You can set parallelism for individual operators:

```java
dataStream.map(new MyMap()).setParallelism(4);
```

### **2. ExecutionEnvironment level**

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.setParallelism(8);
```

This becomes the default for all operators that do not explicitly specify parallelism.

### **3. Flink job submission CLI**

When submitting a job:

```
flink run -p 10 yourJob.jar
```

This overrides environment defaults *but not explicit operator-level parallelism*.

### **4. flink-conf.yaml (cluster-wide default)**

Inside your Flink configuration file:

```yaml
parallelism.default: 1
```

This is the lowest-priority source of parallelism configuration.

---

## **4. Parallelism Resolution Priority**

When running a job, Flink resolves parallelism using this order:

1. **Explicit operator-level `.setParallelism(x)`**
2. **`-p` option in `flink run`**
3. **ExecutionEnvironment `.setParallelism(x)`**
4. **`parallelism.default` from flink-conf.yaml**

- The parallelism priority: Operator > ExecutionEnvironment > Console Option > flink-config.yaml configuration

---

## **5. Notes**

* Sources and sinks may have parallelism limits (e.g., Kafka Source is parallel, some connectors are not).
* Chained operators typically share the same parallelism unless explicitly overridden.
* Maximum parallelism is often recommended to be a large power-of-two value (e.g., 128, 256, 512) for flexible future scaling.

---

