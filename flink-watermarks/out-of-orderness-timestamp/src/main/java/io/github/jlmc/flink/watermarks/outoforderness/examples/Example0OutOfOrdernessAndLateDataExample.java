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
 * Example focused on Event Time windowing with watermarks.
 *
 * <p>Implementation key points and why they matter:</p>
 * <ul>
 *     <li><b>Socket source</b>: keeps the demo simple and interactive. You can manually send in-order,
 *     out-of-order and late records and immediately observe the behavior.</li>
 *     <li><b>Event-time extraction</b>: event time comes from each payload (`timestamp` field), not from
 *     machine clock. This keeps temporal semantics tied to business time.</li>
 *     <li><b>Watermark strategy</b>: controls event-time progress and therefore when windows can close.
 *     Watermarks are the core mechanism to tolerate disorder while deciding when results are emitted.</li>
 *     <li><b>Keyed tumbling windows</b>: records are grouped per city and evaluated in fixed 5-second event-time
 *     intervals, making counts deterministic per key/window.</li>
 *     <li><b>Window process output</b>: emits boundaries and count so it is easy to understand exactly which
 *     records contributed to each result.</li>
 * </ul>
 *
 * <p>About out-of-orderness and late data:</p>
 * <ul>
 *     <li>Out-of-order records are those that arrive later than newer timestamps already seen.</li>
 *     <li>Late data is data that arrives after the watermark already passed the relevant window boundary.</li>
 *     <li>This class is intentionally minimal; production code typically uses a bounded-out-of-orderness
 *     watermark strategy and an explicit late-data policy (allowed lateness and/or side output).</li>
 * </ul>
 */
public class Example0OutOfOrdernessAndLateDataExample {

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
                                .map(Example0OutOfOrdernessAndLateDataExample::parseReading, Types.POJO(SensorReading.class))
                );

        stream.print();

        env.execute("window example 0");

    }

    static SensorReading parseReading(String line) {
        String[] data = line.split(",");
        return new SensorReading(data[0], Float.parseFloat(data[1]), Long.parseLong(data[2]));
    }

    /**
     * Defines how event-time progresses in this demo.
     *
     * <p>`forMonotonousTimestamps()` is used here to keep the example compact and easy to read.
     * The timestamp assigner extracts event time from `SensorReading.timestamp`.</p>
     *
     * <p>In real out-of-orderness scenarios, replace with `forBoundedOutOfOrderness(...)` and tune the
     * delay bound according to observed lateness.</p>
     */
    static WatermarkStrategy<SensorReading> createWatermarkStrategy() {
        return WatermarkStrategy.<SensorReading>forMonotonousTimestamps()
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
