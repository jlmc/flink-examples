package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

/**
 * Topic 136: route very late events to side output to avoid silent data loss.
 */
public class Example9SideOutputLateEvents {

    public static final OutputTag<WatermarkExamplesModels.SensorEvent> LATE_EVENTS_TAG =
            new OutputTag<>("late-events") {
            };

    public static SingleOutputStreamOperator<String> definePipeline(DataStream<WatermarkExamplesModels.SensorEvent> input) {
        return input
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.SensorEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime)
                )
                .keyBy(event -> event.key)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .sideOutputLateData(LATE_EVENTS_TAG)
                .process(new ExampleWindowPrinter());
    }
}
