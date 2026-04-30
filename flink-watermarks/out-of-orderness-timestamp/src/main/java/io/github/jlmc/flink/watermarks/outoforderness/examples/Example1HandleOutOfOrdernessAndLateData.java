package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Example focused on handling out-of-orderness with Event Time windows.
 *
 * <p>Difference from {@link Example0OutOfOrdernessAndLateDataExample}:</p>
 * <ul>
 *     <li><b>Example0</b> uses {@code forMonotonousTimestamps()}, which assumes perfectly ordered events.</li>
 *     <li><b>This class (Example1)</b> uses {@code forBoundedOutOfOrderness(Duration.ofSeconds(2))}, allowing
 *     events to arrive out of order within a 2-second bound before they are considered late for windowing.</li>
 * </ul>
 *
 * <p>Implementation key points and why they matter:</p>
 * <ul>
 *     <li><b>Socket source</b>: keeps the demo simple and interactive. You can manually send in-order,
 *     out-of-order and late records and observe the behavior.</li>
 *     <li><b>Event-time extraction</b>: event time comes from each payload ({@code timestamp} field), not from
 *     machine clock, preserving business-time semantics.</li>
 *     <li><b>Bounded watermark strategy (2s)</b>: delays watermark progression to tolerate small disorder and still
 *     emit deterministic windowed results.</li>
 *     <li><b>Keyed tumbling windows</b>: records are grouped per city and evaluated in fixed 5-second event-time
 *     intervals, making counts deterministic per key/window.</li>
 * </ul>
 */
public class Example1HandleOutOfOrdernessAndLateData {

    private static final String FORMATER = "HH:mm:ss.SSS";

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // this is important
        env.setParallelism(1);
        
        // Create a local socket producer before running this job.
        // Example (macOS/Linux):
        //   nc -lk 9999
        // Then send events in the expected CSV format: city,temperature,eventTimeMillis
        //   lisbon,10.1,1000
        //   lisbon,10.2,2000
        DataStreamSource<String> dataStreamSource = env.socketTextStream("localhost", 9999);

        SingleOutputStreamOperator<String> stream =
                definePipeline(
                        dataStreamSource
                                .map(Example1HandleOutOfOrdernessAndLateData::parseReading, Types.POJO(SensorReading.class))
                );

        stream.print();

        env.execute("window example 0");

    }

    static SensorReading parseReading(String line) {
        String[] data = line.split(",");
        return new SensorReading(data[0], Float.parseFloat(data[1]), Long.parseLong(data[2]));
    }

    /**
     * Defines event-time progression using bounded out-of-orderness.
     *
     * <p>Unlike Example0 (monotonic timestamps), this strategy allows events to arrive up to 2 seconds behind
     * the maximum observed timestamp. The timestamp assigner extracts event time from
     * {@link SensorReading#timestamp}.</p>
     */
    static WatermarkStrategy<SensorReading> createWatermarkStrategy() {
        return WatermarkStrategy.<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((event, timestamp) -> event.timestamp);
    }

    /**
     * Pipeline structure and rationale:
     * <ul>
     *     <li><b>assignTimestampsAndWatermarks</b>: makes event-time semantics active before any windowing.</li>
     *     <li><b>keyBy(city)</b>: isolates independent per-city aggregations.</li>
     *     <li><b>tumbling window (5s)</b>: fixed-size event-time buckets, useful for deterministic examples.</li>
     *     <li><b>process window function</b>: exposes window boundaries (`start`, `end`, `maxTimestamp`) plus
     *     record count for transparent debugging/learning.</li>
     *     <li><b>Practical effect vs Example0</b>: this pipeline can include slightly out-of-order events (within
     *     the 2-second bound) in their correct event-time window before the watermark closes it.</li>
     * </ul>
     */
    static SingleOutputStreamOperator<String> definePipeline(SingleOutputStreamOperator<SensorReading> stream) {
        return stream
                .assignTimestampsAndWatermarks(createWatermarkStrategy())
                .keyBy(sensorReading -> sensorReading.city)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .process(new ProcessWindowFunction<SensorReading, String, String, TimeWindow>() {

                    @Override
                    public void process(String city,
                                        ProcessWindowFunction<SensorReading, String, String, TimeWindow>.Context context,
                                        Iterable<SensorReading> elements, Collector<String> out) {

                        TimeWindow window = context.window();

                        long start = window.getStart();
                        long end = window.getEnd();
                        long maxTimestamp = window.maxTimestamp();
                        long estimateSize = elements.spliterator().estimateSize();

                        String str = """
                                city: %s, [%s - %s, maxTimestamp: %s, count: %d]
                                """.formatted(
                                city,
                                DateFormatUtils.format(start, FORMATER),
                                DateFormatUtils.format(end, FORMATER),
                                DateFormatUtils.format(maxTimestamp, FORMATER),
                                estimateSize
                        );

                        out.collect(str);
                    }
                });
    }

    public static class SensorReading {
        public String city;
        public float temperature;
        public long timestamp;

        public SensorReading() {
        }

        public SensorReading(String city, float temperature, long timestamp) {
            this.city = city;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }
    }
}
