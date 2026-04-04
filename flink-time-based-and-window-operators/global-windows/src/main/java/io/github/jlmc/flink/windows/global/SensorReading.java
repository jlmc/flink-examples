package io.github.jlmc.flink.windows.global;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class SensorReading {
    public String id;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant timestamp;
    public Double temperature;

    public SensorReading() {}
    public SensorReading(String id, Instant timestamp, Double temperature) {
        this.id = id;
        this.timestamp = timestamp;
        this.temperature = temperature;
    }
    @Override
    public String toString() {
        return "SensorReading{" + "id='" + id + '\'' + ", timestamp=" + timestamp + ", temperature=" + temperature + '}';
    }
}
