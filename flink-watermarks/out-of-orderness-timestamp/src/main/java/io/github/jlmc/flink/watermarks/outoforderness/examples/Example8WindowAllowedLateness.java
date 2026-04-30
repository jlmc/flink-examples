package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;

/**
 * Topic 135: WindowedStream allowed lateness.
 */
public class Example8WindowAllowedLateness {

    public static SingleOutputStreamOperator<String> definePipeline(DataStream<WatermarkExamplesModels.SensorEvent> input) {
        return input
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.SensorEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime)
                )
                .keyBy(event -> event.key)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .allowedLateness(Time.seconds(5))
                .process(new ExampleWindowPrinter());
    }
}
