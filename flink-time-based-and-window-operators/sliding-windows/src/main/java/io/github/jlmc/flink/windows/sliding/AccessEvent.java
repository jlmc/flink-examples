package io.github.jlmc.flink.windows.sliding;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class AccessEvent {
    public String userId;
    public boolean success;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant timestamp;

    public AccessEvent() {}

    public AccessEvent(String userId, boolean success, Instant timestamp) {
        this.userId = userId;
        this.success = success;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AccessEvent{" +
                "userId='" + userId + '\'' +
                ", success=" + success +
                ", timestamp=" + timestamp +
                '}';
    }
}
