package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

public class JavaCollectionSourcesConnectorExampleJob {

    static SingleOutputStreamOperator<String> buildWithFromCollection(StreamExecutionEnvironment env, Configuration configuration) {
        DataStreamSource<String> source = env.fromCollection(List.of("a", "b", "c", "d", "e", "f", "g", "h"));

        return source.rebalance()
                .map(JavaCollectionSourcesConnectorExampleJob::getFormatted, Types.STRING)
                .setParallelism(configuration.parallelism);
    }

    static SingleOutputStreamOperator<String> buildWithFromElements(StreamExecutionEnvironment env, Configuration configuration) {
        return env.fromElements("a", "b", "c", "d", "e", "f", "g", "h")
                .map(JavaCollectionSourcesConnectorExampleJob::getFormatted, Types.STRING)
                .setParallelism(configuration.parallelism);
    }

    static SingleOutputStreamOperator<String> buildWithFromSequence(StreamExecutionEnvironment env, Configuration configuration) {
        return env
        // Generates numbers from 1 to 10
                .fromSequence(1, 10)
                // Simulates out-of-order
                // numbers 1..5 get timestamps 1000..5000 timestamps
                // numbers 6..10 get timestamps 1000..5000 again
                // So events go backwards in time, which simulates real out-of-order data.
                //  ✔ Useful for demonstrating how watermarks work
                //  ✔ Without this, watermarking has no effect in a fromSequence() example
                .map(number -> {
                    long eventTime = 0L;

                    if (number <= 5) {
                        eventTime = number * 1000;
                    } else  {
                        eventTime = (number - 5) * 1000;;
                    }

                    return eventTime;
                })

                // Assign event-time timestamps and configure watermarks
                // `.assignTimestampsAndWatermarks(...)` This tells Flink to use event-time processing, not processing-time.
                // Event-time is required for:
                //   ✔  event-time windows
                //   ✔  interval joins
                //   ✔  correct ordering
                //   ✔  late event handling
                //   ✔  CEP, tumbling windows, sliding windows, etc.
                // Inside: `WatermarkStrategy.<Long>forBoundedOutOfOrderness(Duration.ofSeconds(1))` Creates a strategy that allows events to be up to 1 second late.
                //   - Meaning: “I expect events to arrive out of order, but no more than 1 second.”
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Long>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                                .withTimestampAssigner((value, ts ) -> value)
                )

                // Event-time tumbling window of 3 seconds
                // Creates event-time tumbling windows, each covering exactly 3
                //    Windows:
                //      ✔ 0-3 seconds
                //      ✔ 3-6 seconds
                //      ✔ 6-9 seconds
                //      ✔ etc
                //    The window boundaries are based on event timestamps, not system time.
                //    Event-time windows will not close unless watermarks advance.
                //    That’s why watermarks are essential here.
                .windowAll(TumblingEventTimeWindows.of(Duration.ofSeconds(3L)))

                // Reduces the window to the last element (just to close the window)
                // A simple way to “close the window.", This operation:
                //   - receives all events in the window
                //   - keeps the last element (b)
                // You could replace it with sum, count, average, etc.
                // This is only to show that the window produces output.
                .reduce((a, b) -> b)

                // Converts the numeric result into a printable string.
                // This is the final SingleOutputStreamOperator<String> you return.
                .map(result -> "Window closed at event-time " + result)
                ;
    }

    private static String getFormatted(String value) {
        return "[%s] => %s".formatted(value, value.toUpperCase());
    }

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        buildWithFromCollection(env, new Configuration("duke", 5)).print();

        env.execute("java collection source");
    }

    record Configuration(String type, int parallelism) implements Serializable {}
}
