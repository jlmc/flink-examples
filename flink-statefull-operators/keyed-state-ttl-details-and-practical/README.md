# Flink Keyed State - Enable Time-To-Live (TTL): Details and Practical

This module provides a practical and theoretical example of enabling **Keyed State TTL** in Apache Flink `1.20.3`, aligned with the classroom flow from the transcription.

## Goal

- Build a dedicated module under `flink-statefull-operators` focused on `Keyed State TTL`.
- Demonstrate how to enable TTL in a `MapStateDescriptor`.
- Implement a `left join` semantic with `KeyedCoProcessFunction`, where right-side state expires automatically.

## Keyed State and TTL

### Keyed State (general definition)

Keyed state is maintained as an embedded key/value store.
State is partitioned and distributed together with keyed streams.
Therefore, keyed state can only be accessed after `keyBy(...)`, and only for the current key being processed.

This key alignment ensures local state updates (no distributed transactions per update) and allows Flink to transparently redistribute state during rescaling.

### State interfaces

- `ValueState<T>`: stores one value per key.
- `ReducingState<T>`: stores a reduced aggregation of values per key.
- `AggregatingState<IN, OUT>`: stores aggregation where input and output types can differ.
- `ListState<T>`: stores a list of elements per key.
- `MapState<UK, UV>`: stores key-value entries per key.

### What TTL does

When a TTL is configured for keyed state:

- each state value receives metadata with last-relevant-access timestamp;
- expired entries are not returned (when configured with `NeverReturnExpired`);
- cleanup is performed by Flink mechanisms (lazy/on-access + backend cleanup paths);
- for collection states (`ListState`, `MapState`), TTL works **per entry**.

## Example included

Main class:

- `src/main/java/io/github/jlmc/flink/stateful/ttl/KeyedStateTtlDetailsAndPracticalExample.java`

Core behavior implemented:

1. Read `User` events from socket `localhost:9998` (`id,name`).
2. Read `Address` events from socket `localhost:9999` (`id,country`).
3. Connect keyed streams by `id`.
4. In `open(...)`, create `MapStateDescriptor<Integer, Address>`.
5. Build `StateTtlConfig` with:
   - `Time.minutes(1)`
   - `UpdateType.OnCreateAndWrite`
   - `StateVisibility.NeverReturnExpired`
6. Enable TTL via `descriptor.enableTimeToLive(ttlConfig)`.
7. In `processElement2`, store address in map state.
8. In `processElement1`, emit `UserWithAddress(user, addressOrNull)` (left join semantics).

## Why this demonstrates left join + TTL

- Left join: when a user arrives and no address exists yet, output still emits user with `null` address.
- TTL effect: once address state entry expires, subsequent user events return `null` address again.

## TTL technical details and considerations

- TTL is evaluated on state access/update paths and by backend cleanup routines.
- `NeverReturnExpired` guarantees that expired data is filtered at read time, even if not yet physically deleted.
- `OnCreateAndWrite` refreshes TTL only when entries are created/updated; read operations do not extend lifetime.
- TTL metadata introduces extra storage overhead per state entry.
- In RocksDB backend, physical cleanup can happen during compaction, while logical expiry is enforced by Flink state layer.

## `open(...)` instruction-by-instruction (with justification)

Reference block from `LeftJoinFunction.open(...)`:

```java
StateTtlConfig ttlConfig = StateTtlConfig
        .newBuilder(Duration.ofMinutes(1))
        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
        .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
        .build();

MapStateDescriptor<Integer, Address> descriptor =
        new MapStateDescriptor<>("mapState", Types.INT, Types.POJO(Address.class));
descriptor.enableTimeToLive(ttlConfig);

addressState = getRuntimeContext().getMapState(descriptor);
```

Detailed breakdown:

1. `StateTtlConfig ttlConfig = StateTtlConfig ...`
   - Creates the TTL policy object used by Flink state runtime.
   - Justification: centralizes expiration behavior, making state lifecycle explicit and controlled.

2. `.newBuilder(Duration.ofMinutes(1))`
   - Defines retention time (`1 minute`) for each `MapState` entry.
   - Justification: in this example it makes expiration observable quickly in a demo; conceptually it prevents unbounded state growth.

3. `.setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)`
   - Resets TTL only when entry is created or updated (`put`, `putAll`, etc.).
   - Justification: reads should not revive old state. For a left join cache, this avoids stale addresses persisting forever just because users keep arriving.

4. `.setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)`
   - Expired entries are treated as non-existent on reads, even before physical cleanup.
   - Justification: guarantees deterministic join behavior: once expired, `addressState.get(id)` yields `null`, matching expected left-join semantics.

5. `.build()`
   - Finalizes immutable TTL configuration.
   - Justification: prevents accidental runtime mutation and provides a stable policy for the descriptor.

6. `new MapStateDescriptor<>("mapState", Types.INT, Types.POJO(Address.class))`
   - Declares state metadata: logical name + key/value types/serializers.
   - Justification:
     - `"mapState"` identifies this state in the operator.
     - `Types.INT` matches join key (`id`).
     - `Types.POJO(Address.class)` serializes the right-side payload stored in state.

7. `descriptor.enableTimeToLive(ttlConfig)`
   - Activates TTL on this specific state descriptor.
   - Justification: without this call, the state would never expire, defeating the objective of automatic cleanup.

8. `addressState = getRuntimeContext().getMapState(descriptor)`
   - Obtains runtime-managed keyed state instance.
   - Justification: connects the declared descriptor (with TTL) to Flink managed state, so entries are scoped per key, included in checkpoints, and restored on failure.

## How to run

From repository root:

```bash
mvn -pl flink-statefull-operators/keyed-state-ttl-details-and-practical -am clean package
```

Start user producer (terminal A):

```bash
nc -lk 9998
```

Start address producer (terminal B):

```bash
nc -lk 9999
```

Run `KeyedStateTtlDetailsAndPracticalExample.main` from IDE.

Send sample events:

Terminal A (`9998`):

```text
1,Alex
```

Terminal B (`9999`):

```text
1,CN
```

Terminal A (`9998`) again:

```text
1,Alex
```

Wait around one minute (TTL), then send again:

```text
1,Alex
```

Expected shape:

```text
UserWithAddress{userId=1, userName='Alex', country='null'}
UserWithAddress{userId=1, userName='Alex', country='CN'}
UserWithAddress{userId=1, userName='Alex', country='null'}
```

## Official documentation references (Flink 1.20.x)

- Working with State (Keyed State, TTL):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
- State TTL concept section:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/#state-time-to-live-ttl
- Checkpointing and fault tolerance:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/checkpointing/
- State backends (`HashMapStateBackend`, `EmbeddedRocksDBStateBackend`):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/
- API `StateTtlConfig`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/StateTtlConfig.html
- API `MapState`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/MapState.html
- API `MapStateDescriptor`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/MapStateDescriptor.html
- API `KeyedCoProcessFunction`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/streaming/api/functions/co/KeyedCoProcessFunction.html
- DataStream `ConnectedStreams` / `connect`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/overview/
