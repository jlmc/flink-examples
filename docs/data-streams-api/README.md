# Data streams api

- A few basic data sources and sink are built into flink and are always available. The predefined data-sources include reading from files, directory, and sockets and ingested data from collections and iterators. The predefined data sinks support writing to files, to stdout and stderr, and sockets. 

  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/overview/
  - https://bahir.apache.org/docs/flink/
  - https://bahir.apache.org/docs/flink/current/documentation/
  - https://bahir.apache.org/docs/flink/1.0/documentation/

- Java Collection source
- DataGeneratorSource
- Socket Source
- LocalFile Source
- HdfsFile System Source
- Mongodb Source
- Apache Kafka topic Source
- Customize Source


## Java Collection Source Connector

- `fromCollection`: creates a data stream from the given non-empty collection. The type of the data stream is that of the elements in the collection.
- `fromElements`: Creates a new data stream that contains the given elements. The elements must all be of the same type, for example, all elements of the type String or Integer.
- `fromSequence`: Creates a new data stream that contains a sequence of numbers (longs) and is useful for testing and for cases that just need a stream of N events of any kind. The generated source splits the sequence into many parallel sub-sequences and there are parallel source readers. Each sub-sequence will be produced in order, if the parallelism is limited to one, the source will produce one sequence in order.


## New types:

In modern Flink (1.18+), `fromCollection()` and `fromElements()` are deprecated and the recommended replacement is `fromData()`.

Here is why this change was made.


### ✅ 1. Unified API for all data sources

Historically, Flink had too many different ways of creating a data source:

  - fromCollection(...)
  - fromElements(...)
  - fromParallelCollection(...)
  - fromSequence(...)
  - addSource(...)
  - createInput(...)

This caused fragmentation and made the API harder to evolve.
env.fromData(...) is designed to be a single unified entry point for all in-memory or programmatic data sources.

### ✅ 2. Better type inference & serialization


Yes — in modern Flink (1.18+), **`fromCollection()`** and **`fromElements()`** are deprecated and the recommended replacement is **`fromData()`**.

Here is *why* this change was made.

---

# ✅ 1. **Unified API for all data sources**

Historically, Flink had too many different ways of creating a data source:

* `fromCollection(...)`
* `fromElements(...)`
* `fromParallelCollection(...)`
* `fromSequence(...)`
* `addSource(...)`
* `createInput(...)`

This caused fragmentation and made the API harder to evolve.

`env.fromData(...)` is designed to be a **single unified entry point** for all in-memory or programmatic data sources.

---

### ✅ 2. **Better type inference & serialization**

`fromData(...)` provides a consistent way to attach:

* type information
* serializers
* metadata
* parallelism decisions

In older APIs:

* `fromElements()` inferred types differently than `fromCollection()`
* some were eager, some lazy
* some required POJOs, others not
* error messages were inconsistent

`fromData(...)` gives Flink a unified, modern way to decide serialization and schema rules.

---

### ✅ 3. **Better support for the new Source API (FLIP-27)**

Flink introduced a new Source API (FLIP-27) with:

* bounded sources
* unbounded sources
* split enumerators
* better checkpointing
* better scalability

Older APIs like `fromCollection()` bypassed these improvements.

`fromData()` acts as a wrapper that uses the **new Source interface**, meaning your “data source from memory” behaves consistently with:

* Kafka source
* File source
* Pulsar source
* Elastic source
* etc.

This improves **backpressure handling**, **checkpointing**, and **scale-out behavior**.

---

### ✅ 4. **Cleaner semantics for bounded vs unbounded data**

Older methods blur the line:

* `fromCollection()` → bounded
* `fromElements()` → bounded
* `fromSequence()` → bounded or unbounded (depending on API variant)

The new API clearly separates:

* **`fromData()`** for bounded in-memory data
* **`fromSource()`** for real sources

This reduces confusion and makes boundedness explicit.

---

### ✅ 5. **Better maintainability for the Flink team**

Having 6+ ways of creating a simple in-memory stream resulted in duplicate logic and special cases.

`fromData()` simplifies the internal code and makes the API easier to maintain and evolve.

---

### 📘 Summary (simple)

`fromCollection()` and `fromElements()` were deprecated because:

| Reason                             | Benefit                            |
|------------------------------------|------------------------------------|
| Too many overlapping APIs          | Cleaner user experience            |
| Weak/inconsistent type inference   | Stronger serializer control        |
| Not compatible with new Source API | Better performance & checkpointing |
| Inconsistent boundedness semantics | Clear modern API                   |
| Hard to maintain                   | Less internal complexity           |

---
