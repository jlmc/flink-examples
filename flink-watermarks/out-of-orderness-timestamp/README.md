# Out-of-orderness Timestamp (FHIR ADT)

This example demonstrates how to process Healthcare FHIR `ADT` events in Apache Flink using `event time`, when events arrive out of order in Kafka.

## Objective

- Show how to use `WatermarkStrategy.forBoundedOutOfOrderness(...)` to tolerate delay and temporal disorder.
- Ensure correct time-window aggregations based on `eventTimestamp` (business time), not arrival order.
- Demonstrate best practices with `withIdleness(...)` so inactive partitions do not block global watermark progress.

## Domain case it solves

In hospital integrations, ADT messages may arrive late or out of order due to network latency, retries, buffering, and differences between systems.

Without `event time` + `watermarks`, window-based calculations can become incorrect (for example, discharges and admissions assigned to the wrong window). This example solves that by:

- using the `eventTimestamp` embedded in each event;
- defining an out-of-orderness tolerance of `10s`;
- closing windows based on `event time` progress (watermark), instead of processing clock time.

## Data model

Input (`FhirAdtEvent`):

- `messageId`
- `patientId`
- `facilityId`
- `eventType` (`ADT_A01`, `ADT_A02`, `ADT_A03`)
- `eventTimestamp` (UTC)

Output (`AdtWindowResult`):

- `facilityId`
- `eventType`
- `totalEvents`
- subtotals: `admits` (`ADT_A01`), `transfers` (`ADT_A02`), `discharges` (`ADT_A03`)
- window `start` and `end`

## Pipeline

Implemented in `OutOfOrdernessTimestampKafkaExample`:

1. Consumes events from Kafka topic `fhir-adt-events`.
2. Assigns event timestamps and watermarks:
   - `forBoundedOutOfOrderness(Duration.ofSeconds(10))`
   - timestamp assigner: `event.eventTimestamp.toEpochMilli()`
   - `withIdleness(Duration.ofMinutes(1))`
3. Groups by key `facilityId|eventType`.
4. Applies `TumblingEventTimeWindows.of(Duration.ofSeconds(10))`.
5. Aggregates counts and publishes results to `fhir-adt-window-counts`.

### Diagram

```mermaid
flowchart LR
    A[ADT/FHIR Producers\nHospital systems] --> B[Kafka input\n`fhir-adt-events`]
    B --> C[Flink Source\nJSON -> `FhirAdtEvent`]
    C --> D[Timestamp assigner\n`eventTimestamp`]
    D --> E[WatermarkStrategy\nOut-of-orderness: 10s\nIdleness: 1m]
    E --> F["KeyBy('facilityId|eventType')"]
    F --> G[Event-time Tumbling Window\n10 seconds]
    G --> H[Aggregate + ProcessWindow\n`AdtWindowResult`]
    H --> I[Kafka output\n`fhir-adt-window-counts`]
```

## Out-of-order events example

The `submit-events.sh` script sends a sequence with 2 hospitals (`hospital-lisbon` and `hospital-huc`) and deliberately out-of-order timestamps (for example, an event at `10:00:05` may arrive after one at `10:00:19`).

This helps observe that:

- Flink uses event-time semantics to build windows;
- late events within the configured tolerance still go to the correct window;
- watermark trade-off applies: more tolerance increases confidence, but may increase output latency.

## How to run

From the module directory:

```bash
./build-jdk17.sh
./upload-job.sh
./submit-events.sh
```

To monitor results:

- logs with `WINDOW_RESULT` (application stdout);
- Kafka output topic `fhir-adt-window-counts`.

## Practical value

This case supports healthcare operational monitoring (occupancy, admissions/discharges/transfers throughput per hospital) with higher robustness against delayed and out-of-order data, preserving temporal correctness of indicators.

## Additional topic-focused examples (0-138)

The module now also includes focused classes in `src/main/java/.../examples` to study each topic in isolation:

- `0. Baseline event-time windowing (monotonic timestamps)`
  - Class: `Example0OutOfOrdernessAndLateDataExample`
  - Demonstrates the baseline setup with `forMonotonousTimestamps()` and explains why this is suitable only when events are strictly ordered.

- `1. Handling out-of-orderness with bounded delay`
  - Class: `Example1HandleOutOfOrdernessAndLateData`
  - Demonstrates `forBoundedOutOfOrderness(Duration.ofSeconds(2))` to include slightly out-of-order records in the correct event-time window before they become late.

- `129. WatermarkStrategy(TimestampAssigner&WatermarkGenerator)`
  - Class: `Example2WatermarkStrategyWithAssignerAndGenerator`
  - Shows explicit construction of `WatermarkStrategy` combining:
    - custom timestamp extraction (`eventTime`);
    - custom generator (`onEvent` + `onPeriodicEmit`).

- `130. Dive into Flink source-code logic for watermarks`
  - Class: `Example3DiveIntoWatermarkLifecycle`
  - Distills the runtime lifecycle into the two critical callbacks:
    - `onEvent(...)`: update local progress state;
    - `onPeriodicEmit(...)`: publish watermark to downstream operators.

- `131. Custom periodic watermark generator`
  - Class: `Example4CustomPeriodicWatermarkGenerator`
  - Demonstrates periodic strategy (`maxSeenTs - outOfOrderness - 1`) and why periodic emission controls latency/completeness trade-off.

- `132. Custom punctuated watermark generator`
  - Class: `Example5CustomPunctuatedWatermarkGenerator`
  - Demonstrates punctuated approach where watermark is emitted only on marker events.

- `133. Watermark propagation`
  - Class: `Example6WatermarkPropagation`
  - Demonstrates that downstream progress is constrained by the minimum upstream watermark.

- `134. Idle source handling`
  - Class: `Example7IdleSourceHandling`
  - Demonstrates `withIdleness(...)` to prevent idle partitions/sources from stalling global event-time progress.

- `135. WindowedStream allowed lateness`
  - Class: `Example8WindowAllowedLateness`
  - Demonstrates `.allowedLateness(...)` to keep a window open for late-but-acceptable updates.

- `136. Side output for late events`
  - Class: `Example9SideOutputLateEvents`
  - Demonstrates `.sideOutputLateData(...)` so very late records are captured instead of silently dropped.

- `137. Two-stream window join`
  - Class: `Example10TwoStreamWindowJoin`
  - Demonstrates event-time window join between two streams using a tumbling window.

- `138. Two keyed streams interval join`
  - Class: `Example11TwoKeyedStreamsIntervalJoin`
  - Demonstrates interval-based correlation (`between(-2s, +2s)`) for keyed streams in event time.
