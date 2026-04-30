package io.github.jlmc.flink.watermarks.outoforderness;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.time.Instant;


/**
 * Real-time data processing pipeline using Apache Flink.
 *
 * <p>This job consumes healthcare events in the FHIR ADT format (Admission, Discharge, Transfer)
 * from a Kafka topic, performs time-windowed aggregations, and publishes the results to another
 * Kafka topic.</p>
 *
 * <h2>Responsibilities</h2>
 *
 * <h3>1. Data Ingestion (Kafka Source)</h3>
 * <ul>
 *   <li>Consumes {@code FhirAdtEvent} records from a Kafka topic.</li>
 *   <li>Deserializes JSON into Java objects using {@code ObjectMapper}.</li>
 *   <li>Configures date/time support (Java 8 Time API).</li>
 *   <li>Starts consuming from the latest offset ({@code latest}).</li>
 * </ul>
 *
 * <h3>2. Watermark Strategy</h3>
 * <ul>
 *   <li>Uses <b>Event Time</b> based on the {@code eventTimestamp} field.</li>
 *   <li>Allows out-of-order events with up to 10 seconds of tolerance
 *       ({@code forBoundedOutOfOrderness}).</li>
 *   <li>Detects idle partitions after 1 minute ({@code idleness}) to avoid blocking time progress.</li>
 * </ul>
 *
 * <h3>3. Grouping and Windowing</h3>
 * <ul>
 *   <li>Groups events by {@code facilityId} and {@code eventType} ({@code keyBy}).</li>
 *   <li>Applies fixed 10-second tumbling windows.</li>
 *   <li>Performs efficient aggregation with {@code AdtCountAggregateFunction}:
 *     <ul>
 *       <li>Total events</li>
 *       <li>Admissions ({@code ADT_A01})</li>
 *       <li>Discharges ({@code ADT_A03})</li>
 *       <li>Transfers ({@code ADT_A02})</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>4. Final Processing and Output</h3>
 * <ul>
 *   <li>Uses {@code ProcessWindowFunction} to produce {@code AdtWindowResult} when a window closes.</li>
 *   <li>Includes aggregated metrics and the window time interval.</li>
 *   <li>Publishes results as JSON to a Kafka topic.</li>
 *   <li>Uses {@code facilityId:eventType} as the message key.</li>
 *   <li>Also prints results to the console for debugging.</li>
 * </ul>
 *
 * <h2>Main Entities</h2>
 * <ul>
 *   <li>{@code FhirAdtEvent} – Input event representing a clinical action.</li>
 *   <li>{@code AdtCountAccumulator} – Temporary accumulator used during window aggregation.</li>
 *   <li>{@code AdtWindowResult} – Final result emitted per window.</li>
 * </ul>
 *
 * <p><b>Summary:</b> This pipeline enables real-time monitoring of patient flow in healthcare
 * facilities by producing aggregated metrics every 10 seconds, even when events arrive out of order.</p>
 */
public class OutOfOrdernessTimestampKafkaExample {

    public static void main(String[] args) throws Exception {
        ParameterTool parameters = ParameterTool.fromArgs(args);
        String bootstrapServers = parameters.get("bootstrap.servers", "kafka:19092");
        String inputTopic = parameters.get("input.topic", "fhir-adt-events");
        String outputTopic = parameters.get("output.topic", "fhir-adt-window-counts");

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<FhirAdtEvent> source = KafkaSource.<FhirAdtEvent>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("out-of-orderness-fhir-adt-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(
                        FhirAdtEvent.class,
                        () -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            return objectMapper;
                        }
                ))
                .build();

        DataStream<FhirAdtEvent> adtEvents = env.fromSource(source, createWatermarkStrategy(), "FHIR ADT Kafka Source");

        DataStream<AdtWindowResult> resultStream = definePipeline(adtEvents);

        JsonSerializationSchema<AdtWindowResult> serializationSchema = new JsonSerializationSchema<>(
                () -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    return objectMapper;
                }
        );

        KafkaSink<AdtWindowResult> sink = KafkaSink.<AdtWindowResult>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.<AdtWindowResult>builder()
                                .setTopic(outputTopic)
                                .setKeySerializationSchema((SerializationSchema<AdtWindowResult>) element ->
                                        (element.facilityId + ":" + element.eventType)
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                )
                                .setValueSerializationSchema(serializationSchema)
                                .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        resultStream.sinkTo(sink).name("Kafka Sink");

        resultStream
                .map(AdtWindowResult::toString)
                .print("WINDOW_RESULT");

        env.execute("Out Of Orderness Timestamp FHIR ADT Kafka Example");
    }

    /**
     * Creates the watermark strategy used by the Event Time pipeline in this ADT/FHIR example.
     *
     * This strategy assumes up to 10 seconds of temporal disorder (out-of-orderness), extracts the
     * business timestamp ({@code eventTimestamp}) from each record, and enables idleness detection
     * so silent partitions do not block global watermark progress.
     *
     * Line by line:
     * 1) {@code forBoundedOutOfOrderness(Duration.ofSeconds(10))}
     *    - Defines a maximum tolerated delay of 10 seconds for out-of-order events.
     *    - Watermark progression is approximately {@code maxObservedTimestamp - 10s}.
     * 2) {@code withTimestampAssigner(...)}
     *    - Tells Flink which event timestamp to use (instead of machine clock time).
     *    - Converts {@code event.eventTimestamp} into epoch millis, Flink's expected internal format.
     * 3) {@code withIdleness(Duration.ofMinutes(1))}
     *    - Marks a partition/source as idle after 1 minute without events.
     *    - Prevents silent partitions from blocking operator watermark progress.
     */
    public static WatermarkStrategy<FhirAdtEvent> createWatermarkStrategy() {
        return WatermarkStrategy
                // 1) Accept up to 10s delay between actual event order and arrival order.
                .<FhirAdtEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                // 2) Extract Event Time from the FHIR ADT domain timestamp field.
                .withTimestampAssigner((SerializableTimestampAssigner<FhirAdtEvent>) (event, recordTimestamp) -> event.eventTimestamp.toEpochMilli())
                // 3) If a partition has no data for 1 minute, mark it as idle.
                .withIdleness(Duration.ofMinutes(1));
    }

    /**
     * Defines the core Event Time pipeline for ADT/FHIR events.
     *
     * The pipeline groups events by hospital and ADT type, applies 10-second tumbling Event Time
     * windows, and produces one {@link AdtWindowResult} per key/window with aggregated counters.
     *
     * Line by line:
     * 1) {@code keyBy(event -> event.facilityId + "|" + event.eventType)}
     *    - Partitions the stream by a composite business key: facility + ADT event type.
     *    - Ensures each key is aggregated independently (for example, {@code hospital-huc|ADT_A01}).
     * 2) {@code window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))}
     *    - Creates fixed, non-overlapping 10-second windows in Event Time.
     *    - Window boundaries are driven by watermarks (not by processing clock time).
     * 3) {@code aggregate(new AdtCountAggregateFunction(), new AdtCountWindowResultProcessWindowFunction())}
     *    - Uses an incremental aggregator to keep running counters efficiently inside each window.
     *    - Then uses a window process function to enrich results with key and window metadata
     *      (window start/end, facility, and event type) before emitting {@link AdtWindowResult}.
     */
    public static DataStream<AdtWindowResult> definePipeline(DataStream<FhirAdtEvent> adtEvents) {
        return adtEvents
                // 1) Group by hospital and ADT type to compute independent per-key window metrics.
                .keyBy(event -> event.facilityId + "|" + event.eventType)
                // 2) Use fixed 10-second tumbling windows in Event Time.
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))
                // 3) Incrementally aggregate counters and then format the final window output record.
                .aggregate(
                        new AdtCountAggregateFunction(),
                        new AdtCountWindowResultProcessWindowFunction()
                );
    }

    public static class AdtCountAccumulator implements java.io.Serializable {
        long totalEvents;
        long admits;
        long discharges;
        long transfers;

        public AdtCountAccumulator() {
            this.totalEvents = 0L;
            this.admits = 0L;
            this.discharges = 0L;
            this.transfers = 0L;
        }

        public AdtCountAccumulator add(FhirAdtEvent value) {
            this.totalEvents++;
            if ("ADT_A01".equals(value.eventType)) {
                this.admits++;
            } else if ("ADT_A03".equals(value.eventType)) {
                this.discharges++;
            } else if ("ADT_A02".equals(value.eventType)) {
                this.transfers++;
            }
            return this;
        }
    }

    public static class AdtCountAggregateFunction implements AggregateFunction<FhirAdtEvent, AdtCountAccumulator, AdtCountAccumulator> {
        @Override
        public AdtCountAccumulator createAccumulator() {
            return new AdtCountAccumulator();
        }

        @Override
        public AdtCountAccumulator add(FhirAdtEvent value, AdtCountAccumulator accumulator) {
            return accumulator.add(value);
        }

        @Override
        public AdtCountAccumulator getResult(AdtCountAccumulator accumulator) {
            return accumulator;
        }

        @Override
        public AdtCountAccumulator merge(AdtCountAccumulator a, AdtCountAccumulator b) {
            AdtCountAccumulator merged = new AdtCountAccumulator();
            merged.totalEvents = a.totalEvents + b.totalEvents;
            merged.admits = a.admits + b.admits;
            merged.discharges = a.discharges + b.discharges;
            merged.transfers = a.transfers + b.transfers;
            return merged;
        }
    }

    public static class AdtCountWindowResultProcessWindowFunction
            extends ProcessWindowFunction<AdtCountAccumulator, AdtWindowResult, String, TimeWindow> {
        @Override
        public void process(String key,
                            ProcessWindowFunction<AdtCountAccumulator, AdtWindowResult, String, TimeWindow>.Context context,
                            Iterable<AdtCountAccumulator> elements,
                            Collector<AdtWindowResult> out) {
            AdtCountAccumulator acc = elements.iterator().next();
            String[] keyParts = key.split("\\|", 2);
            out.collect(new AdtWindowResult(
                    keyParts[0],
                    keyParts[1],
                    acc.totalEvents,
                    acc.admits,
                    acc.discharges,
                    acc.transfers,
                    Instant.ofEpochMilli(context.window().getStart()),
                    Instant.ofEpochMilli(context.window().getEnd())
            ));
        }
    }
}
