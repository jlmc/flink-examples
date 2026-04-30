package io.github.jlmc.flink.watermarks.outoforderness.examples;

import org.apache.flink.api.common.eventtime.Watermark;
import org.apache.flink.api.common.eventtime.WatermarkGenerator;
import org.apache.flink.api.common.eventtime.WatermarkOutput;

/**
 * Topic 132: custom punctuated watermark generator.
 *
 * <p>Watermark is emitted on specific events (here, every event with value == 999.0).</p>
 */
public class Example5CustomPunctuatedWatermarkGenerator {

    public static class PunctuatedGenerator implements WatermarkGenerator<WatermarkExamplesModels.SensorEvent> {

        @Override
        public void onEvent(WatermarkExamplesModels.SensorEvent event, long eventTimestamp, WatermarkOutput output) {
            if (event.value == 999.0d) {
                output.emitWatermark(new Watermark(eventTimestamp - 1));
            }
        }

        @Override
        public void onPeriodicEmit(WatermarkOutput output) {
            // intentionally empty: punctuated strategy emits only on marker events
        }
    }
}
