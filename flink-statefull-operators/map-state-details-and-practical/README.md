# Flink Keyed State - MapState Details and Practical

This module provides a practical and detailed `MapState` example in Apache Flink `1.20.3`, based on a classroom-style implementation flow.

## Goal

- Build a dedicated module under `flink-statefull-operators` focused on `MapState`.
- Demonstrate how to keep per-key map values in a `KeyedProcessFunction`.
- Implement average score by `courseName` for each `classId`.

## Keyed State

### General definition

Keyed state is maintained in what can be thought of as an embedded key/value store.
State is partitioned and distributed strictly together with streams consumed by stateful operators.
Therefore, access to key/value state is only possible on keyed streams (after `keyBy(...)`) and is restricted to values associated with the current event key.

Aligning stream keys and state guarantees local state updates, preserving consistency without transaction overhead.
This alignment also allows Flink to transparently redistribute state and adjust stream partitioning when scaling.

### State interface types

- `ValueState<T>`: keeps a single value that can be updated and retrieved via `update(T)` and `value()`.
- `ReducingState<T>`: keeps a single value representing the reduction of all values added to the state.
- `AggregatingState<IN, OUT>`: keeps a single aggregated value; unlike `ReducingState`, output type can differ from input type.
- `ListState<T>`: keeps a list of elements; supports `add(T)`, `addAll(List<T>)`, `get()` and `update(List<T>)`.
- `MapState<UK, UV>`: keeps key-value mappings; supports `put(UK, UV)`, `putAll(Map<UK, UV>)`, `get(UK)`, `entries()`, `keys()`, `values()`, and `isEmpty()`.

## Example Included

Main class:

- `src/main/java/io/github/jlmc/flink/stateful/mapstate/MapStateDetailsAndPracticalExample.java`

Core behavior implemented:

1. Read score events from a socket (`localhost:9999`).
2. Parse input format `classId,studentId,courseName,score`.
3. `keyBy(classId)` to scope state per class.
4. Use `MapState<String, Float>` where:
   - key = `courseName`
   - value = current average score for that course in that class
5. On each new score:
   - if no historical value exists for the course, store current score;
   - otherwise, update with `(newScore + previousAvg) / 2` (same logic as the transcription).
6. Emit `(classId, courseName, avgScore)` as `Tuple3<String, String, Float>`.

## Why `MapState` here

`MapState` is useful when each main key (here `classId`) needs a secondary dynamic lookup table (here `courseName -> avgScore`).

Compared to `ValueState`, this avoids storing a custom object containing all courses and allows direct access per course key.

### Practical advantages of `MapState`

- **Efficient partial updates**: you can update only one inner entry (`courseName`) without rewriting a full aggregate object.
- **Natural modeling for sparse/dynamic dimensions**: courses can appear/disappear per class without schema changes.
- **Lower application complexity**: direct `get/put` by sub-key avoids manual map serialization/deserialization logic.
- **Clear separation of concerns**: `keyBy(classId)` scopes the main partition, while `MapState` manages course-level values inside that partition.

### Why `MapState` helps with checkpoints

- `MapState` is part of Flink managed keyed state, so its contents are automatically included in checkpoints.
- On failure/restart, Flink restores the map entries for each key from the latest successful checkpoint, preserving continuity of calculations.
- This supports **exactly-once state consistency**: recovered state reflects a consistent snapshot point, not an arbitrary in-memory moment.
- With large keyed state and `EmbeddedRocksDBStateBackend`, checkpoints can be incremental at backend level, which reduces checkpoint I/O for state changes.
- Operationally, this means you can keep in-memory-like update ergonomics in code while still getting production-grade fault tolerance.

## How to Run

From repository root:

```bash
mvn -pl flink-statefull-operators/map-state-details-and-practical -am clean package
```

Start a local socket producer (terminal A):

```bash
nc -lk 9999
```

Run the Flink job from your IDE (`MapStateDetailsAndPracticalExample.main`) or package/run as usual.

Send sample events in terminal A:

```text
class-1,student-1,Math,18
class-1,student-2,Math,14
class-1,student-3,Physics,16
class-2,student-1,Math,11
class-1,student-4,Math,20
```

Expected output shape:

```text
(class-1,Math,18.0)
(class-1,Math,16.0)
(class-1,Physics,16.0)
(class-2,Math,11.0)
(class-1,Math,18.0)
```

## Important Implementation Details

- `MapState` is initialized in `open(...)` using `MapStateDescriptor`.
- `ctx.getCurrentKey()` provides the current `classId` from `keyBy(...)`.
- State is local to each key and managed by Flink runtime/checkpointing.
- The example intentionally follows the same simple averaging step from the class transcription.

## Official Documentation References

- Working with State (Keyed/Operator State):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
- Checkpoints and fault tolerance:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/checkpointing/
- State Backends (including incremental checkpoints context):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/
- `MapState` API details:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/MapState.html
- `KeyedProcessFunction` API details:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/streaming/api/functions/KeyedProcessFunction.html
