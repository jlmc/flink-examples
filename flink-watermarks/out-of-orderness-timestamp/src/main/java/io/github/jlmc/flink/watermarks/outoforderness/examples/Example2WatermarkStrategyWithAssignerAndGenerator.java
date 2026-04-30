package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkGeneratorSupplier;
import org.apache.flink.api.common.eventtime.WatermarkOutput;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;

import java.time.Duration;

/**
 * Topic 129: explicit {@link WatermarkStrategy} with custom {@link SerializableTimestampAssigner}
 * and custom {@link WatermarkGenerator}.
 */
public class Example2WatermarkStrategyWithAssignerAndGenerator {

    public static WatermarkStrategy<WatermarkExamplesModels.SensorEvent> createStrategy(Duration maxOutOfOrderness) {
        return WatermarkStrategy
                .<WatermarkExamplesModels.SensorEvent>forGenerator((WatermarkGeneratorSupplier.Context context) ->
                        new MaxTimestampPeriodicGenerator(maxOutOfOrderness.toMillis()))
                .withTimestampAssigner((event, previousTimestamp) -> event.eventTime);
    }

    /**
     * Periodic generator: tracks the max observed event time and emits watermark in periodic callback.
     */
    public static class MaxTimestampPeriodicGenerator implements WatermarkGenerator<WatermarkExamplesModels.SensorEvent> {
        private final long outOfOrdernessMillis;
        private long maxTimestampSeen = Long.MIN_VALUE + 1;

        public MaxTimestampPeriodicGenerator(long outOfOrdernessMillis) {
            this.outOfOrdernessMillis = outOfOrdernessMillis;
        }

        @Override
        public void onEvent(WatermarkExamplesModels.SensorEvent event, long eventTimestamp, WatermarkOutput output) {
            maxTimestampSeen = Math.max(maxTimestampSeen, eventTimestamp);
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
            output.emitWatermark(new Watermark(maxTimestampSeen - outOfOrdernessMillis - 1));
        }
    }
}
