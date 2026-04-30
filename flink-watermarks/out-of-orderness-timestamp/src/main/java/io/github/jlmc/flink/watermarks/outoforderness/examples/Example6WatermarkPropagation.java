package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

import java.time.Duration;

/**
 * Topic 133: watermark propagation across operators.
 *
 * <p>The key rule demonstrated by this pipeline is: downstream operator progress follows
 * the minimum watermark coming from upstream inputs/partitions.</p>
 */
public class Example6WatermarkPropagation {

    public static SingleOutputStreamOperator<String> definePipeline(DataStream<WatermarkExamplesModels.SensorEvent> input) {
        return input
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.SensorEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime)
                )
                .keyBy(event -> event.key)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .process(new ExampleWindowPrinter());
    }
}
