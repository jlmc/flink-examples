package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

import java.time.Duration;

/**
 * Topic 137: two-stream window join.
 */
public class Example10TwoStreamWindowJoin {

    public static DataStream<String> definePipeline(
            DataStream<WatermarkExamplesModels.LeftEvent> left,
            DataStream<WatermarkExamplesModels.RightEvent> right) {

        DataStream<WatermarkExamplesModels.LeftEvent> leftWm = left
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.LeftEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime));

        DataStream<WatermarkExamplesModels.RightEvent> rightWm = right
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.RightEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime));

        return leftWm
                .join(rightWm)
                .where(event -> event.key)
                .equalTo(event -> event.key)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .apply((l, r) -> "windowJoin key=" + l.key + ", left=" + l.payload + ", right=" + r.payload);
    }
}
