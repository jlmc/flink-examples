package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

import java.time.Duration;

/**
 * Topic 134: idle source handling with {@code withIdleness(...)}.
 */
public class Example7IdleSourceHandling {

    public static SingleOutputStreamOperator<String> definePipeline(DataStream<WatermarkExamplesModels.SensorEvent> input) {
        return input
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.SensorEvent>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner((event, previous) -> event.eventTime)
                                .withIdleness(Duration.ofSeconds(30))
                )
                .keyBy(event -> event.key)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .process(new ExampleWindowPrinter());
    }
}
