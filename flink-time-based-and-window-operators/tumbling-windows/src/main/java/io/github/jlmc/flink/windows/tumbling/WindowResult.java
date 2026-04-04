package io.github.jlmc.flink.windows.tumbling;
import java.time.Instant;

public final class WindowResult implements java.io.Serializable {
    public final String sensorId;
    public final double average;
    public final long measurementsCount;
    public final Instant start;
    public final Instant end;

    public WindowResult(String sensorId, double average, long count, Instant start, Instant end) {
        this.sensorId = sensorId;
        this.average = average;
        this.measurementsCount = count;
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return String.format("Sensor: %s | Média: %.2f (%d leituras) | Janela: [%s - %s]",
                sensorId, average, measurementsCount, start, end);
    }
}
