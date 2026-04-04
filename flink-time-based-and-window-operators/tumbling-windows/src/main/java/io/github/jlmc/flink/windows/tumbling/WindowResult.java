package io.github.jlmc.flink.windows.tumbling;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public final class WindowResult implements java.io.Serializable {
    public String sensorId;
    public double average;
    public long measurementsCount;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant start;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant end;

    public WindowResult() {}

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
