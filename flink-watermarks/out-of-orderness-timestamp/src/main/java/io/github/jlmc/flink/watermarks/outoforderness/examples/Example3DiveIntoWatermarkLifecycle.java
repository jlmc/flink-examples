package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;

/**
 * Topic 130: source-code-level lifecycle view of watermarks.
 *
 * <p>This class isolates the internal logic equivalent to what Flink executes in runtime:
 * `onEvent(...)` updates state, and `onPeriodicEmit(...)` publishes progress.</p>
 */
public class Example3DiveIntoWatermarkLifecycle {

    public static class TracingPeriodicGenerator implements WatermarkGenerator<WatermarkExamplesModels.SensorEvent> {
        private long maxTimestampSeen = Long.MIN_VALUE + 1;
        private final long delayMs;

        public TracingPeriodicGenerator(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public void onEvent(WatermarkExamplesModels.SensorEvent event, long eventTimestamp, WatermarkOutput output) {
            maxTimestampSeen = Math.max(maxTimestampSeen, eventTimestamp);
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
            output.emitWatermark(new Watermark(maxTimestampSeen - delayMs - 1));
        }
    }
}
