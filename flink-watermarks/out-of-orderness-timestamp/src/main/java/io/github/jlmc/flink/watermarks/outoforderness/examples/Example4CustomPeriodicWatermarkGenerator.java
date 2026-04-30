package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;

/**
 * Topic 131: how to customize watermark generation with periodic emission.
 */
public class Example4CustomPeriodicWatermarkGenerator {

    public static class PeriodicBoundedGenerator implements WatermarkGenerator<WatermarkExamplesModels.SensorEvent> {
        private final long maxOutOfOrdernessMs;
        private long maxTimestampSeen = Long.MIN_VALUE + 1;

        public PeriodicBoundedGenerator(long maxOutOfOrdernessMs) {
            this.maxOutOfOrdernessMs = maxOutOfOrdernessMs;
        }

        @Override
        public void onEvent(WatermarkExamplesModels.SensorEvent event, long eventTimestamp, WatermarkOutput output) {
            maxTimestampSeen = Math.max(maxTimestampSeen, eventTimestamp);
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
            output.emitWatermark(new Watermark(maxTimestampSeen - maxOutOfOrdernessMs - 1));
        }
    }
}
