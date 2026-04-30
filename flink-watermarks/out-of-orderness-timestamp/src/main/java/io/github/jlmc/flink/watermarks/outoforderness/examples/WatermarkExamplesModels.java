package io.github.jlmc.flink.watermarks.outoforderness.examples;

import java.io.Serializable;

/**
 * Shared lightweight models used by the watermark/join examples.
 */
public final class WatermarkExamplesModels {

    private WatermarkExamplesModels() {
    }

    public static class SensorEvent implements Serializable {
        public String key;
        public double value;
        public long eventTime;

        public SensorEvent() {
        }

        public SensorEvent(String key, double value, long eventTime) {
            this.key = key;
            this.value = value;
            this.eventTime = eventTime;
        }
    }

    public static class LeftEvent implements Serializable {
        public String key;
        public String payload;
        public long eventTime;

        public LeftEvent() {
        }

        public LeftEvent(String key, String payload, long eventTime) {
            this.key = key;
            this.payload = payload;
            this.eventTime = eventTime;
        }
    }

    public static class RightEvent implements Serializable {
        public String key;
        public String payload;
        public long eventTime;

        public RightEvent() {
        }

        public RightEvent(String key, String payload, long eventTime) {
            this.key = key;
            this.payload = payload;
            this.eventTime = eventTime;
        }
    }
}
