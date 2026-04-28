# Apache Flink 1.20.3 - Watermarks Guide and Examples

This module consolidates practical guidance on event-time processing with **Watermarks** in Apache Flink **1.20.3**.

## 1. Time Semantics

Flink supports different time semantics. Choosing the correct one is key for windowing and joins.

### Processing Time
- Uses the local system clock of the machine executing the operator.
- Lowest latency and simplest setup.
- Not deterministic under replay, backfill, or delayed ingestion.

### Event Time
- Uses timestamps carried by the events themselves.
- Deterministic and robust for out-of-order streams.
- Requires timestamp assignment and watermark generation.

### Processing Time vs Event Time
- **Determinism:** Event Time wins.
- **Latency:** Processing Time usually wins.
- **Resilience to delay/disorder:** Event Time wins.
- Rule of thumb: use Event Time whenever business correctness depends on when events actually happened.

## 2. Watermark Introduction

### What is a Watermark?
A watermark is Flink's progress signal for Event Time. A watermark at `T` means Flink assumes no more events with timestamp `<= T` should arrive (except late events, if tolerated).

### Out-of-orderness Elements
Real streams are not perfectly ordered. Flink models expected disorder by delaying watermark progression (for example, bounded out-of-orderness).

### Late Data
Events that arrive after watermark progression past their window boundary are late.
- Some late events can still be accepted using allowed lateness.
- Very late events can be redirected to side output.

## 3. Watermark API (Flink 1.20.3)

### WatermarkStrategy (recommended standard)
In Flink 1.20.3, `WatermarkStrategy` is the standard and recommended API for timestamp extraction + watermark generation.

```java
WatermarkStrategy<MyEvent> strategy = WatermarkStrategy
    .<MyEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withTimestampAssigner((event, ts) -> event.getEventTime());

DataStream<MyEvent> stream = source.assignTimestampsAndWatermarks(strategy);
```

### Watermark Propagation
- Watermarks propagate through operators.
- For parallel streams, downstream progression follows the **minimum** watermark from upstream partitions/subtasks.
- A slow partition can hold back global event-time progress.

### Idle Sources
Use idleness detection so temporarily silent partitions do not block the global watermark:

```java
WatermarkStrategy<MyEvent> strategy = WatermarkStrategy
    .<MyEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withTimestampAssigner((event, ts) -> event.getEventTime())
    .withIdleness(Duration.ofSeconds(30));
```

### Allowed Lateness
Window operators can accept late records for a configured grace period:

```java
.window(TumblingEventTimeWindows.of(Time.minutes(1)))
.allowedLateness(Time.seconds(30))
```

### Late Data Side Output
Very late records can be diverted for auditing, alerts, or compensation:

```java
final OutputTag<MyEvent> lateTag = new OutputTag<>("late-events", Types.POJO(MyEvent.class));

SingleOutputStreamOperator<Result> main = stream
    .keyBy(MyEvent::getKey)
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .sideOutputLateData(lateTag)
    .process(new MyProcessWindowFunction());

DataStream<MyEvent> late = main.getSideOutput(lateTag);
```

## 4. DataStream Join with Time Semantics

### Window Join
Join two streams when both events fall in the same window.

Use when:
- both streams represent correlated facts in aligned time buckets,
- and you can tolerate join granularity by window boundaries.

### Interval Join
Join stream `A` with stream `B` when `B.timestamp` is inside a relative interval from `A.timestamp`.

Use when:
- you need asymmetric temporal relations,
- and explicit `before/after` constraints (for example, `A.ts - 2s <= B.ts <= A.ts + 5s`).

## Flink 1.20.3 Notes

- `WatermarkStrategy` remains the recommended modern API (instead of legacy timestamp assigners).
- Improvements in checkpointing and state management in the 1.20.x line help stabilize workloads with intensive event-time operations and large stateful windows.

## Suggested Hands-on Progression

1. Start with Event Time + bounded out-of-orderness.
2. Add idleness for sparse partitions.
3. Configure allowed lateness for controlled corrections.
4. Route very late events to side output.
5. Add one Window Join and one Interval Join scenario.
