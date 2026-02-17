package io.github.jlmc.flink.multistream;

import java.util.List;

public record ForestMonitorData(
        String type,
        String smoke,
        double temperature
) {
    public static final String TYPE_SMOKE = "SMOKE";
    public static final String TYPE_TEMPERATURE = "TEMPERATURE";
    private static List<String> SMOKE_LEVELS = List.of("LOW", "MEDIUM", "HIGH");


    boolean isFireAlert() {
        return (TYPE_TEMPERATURE.equals(type) && temperature > 50.0) ||
                (TYPE_SMOKE.equals(type) && SMOKE_LEVELS.indexOf(smoke) >= SMOKE_LEVELS.indexOf("MEDIUM"));
    }
}
