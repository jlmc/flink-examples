package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Topic 138: interval join between two keyed streams.
 */
public class Example11TwoKeyedStreamsIntervalJoin {

    public static DataStream<String> definePipeline(
            DataStream<WatermarkExamplesModels.LeftEvent> left,
            DataStream<WatermarkExamplesModels.RightEvent> right) {

        KeyedStream<WatermarkExamplesModels.LeftEvent, String> leftKeyed = left
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.LeftEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime))
                .keyBy(event -> event.key);

        KeyedStream<WatermarkExamplesModels.RightEvent, String> rightKeyed = right
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy
                                .<WatermarkExamplesModels.RightEvent>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((event, previous) -> event.eventTime))
                .keyBy(event -> event.key);

        return leftKeyed
                .intervalJoin(rightKeyed)
                .between(Duration.ofSeconds(-2), Duration.ofSeconds(2))
                .process(new ProcessJoinFunction<WatermarkExamplesModels.LeftEvent, WatermarkExamplesModels.RightEvent, String>() {
                    @Override
                    public void processElement(WatermarkExamplesModels.LeftEvent leftEvent,
                                               WatermarkExamplesModels.RightEvent rightEvent,
                                               ProcessJoinFunction<WatermarkExamplesModels.LeftEvent, WatermarkExamplesModels.RightEvent, String>.Context context,
                                               Collector<String> out) {
                        out.collect("intervalJoin key=" + leftEvent.key + ", left=" + leftEvent.payload + ", right=" + rightEvent.payload);
                    }
                });
    }
}
