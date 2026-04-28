package io.github.jlmc.flink.windows.sliding;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.Instant;

public class AccessAlert implements Serializable {
    public String userId;
    public long failedAttempts;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant windowStart;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    public Instant windowEnd;

    public AccessAlert() {}

    public AccessAlert(String userId, long failedAttempts, Instant windowStart, Instant windowEnd) {
        this.userId = userId;
        this.failedAttempts = failedAttempts;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    @Override
    public String toString() {
        return String.format("ALERTA: Possível Bot! Utilizador [%s] teve %d tentativas falhadas na janela [%s - %s]",
                userId, failedAttempts, windowStart, windowEnd);
    }
}
