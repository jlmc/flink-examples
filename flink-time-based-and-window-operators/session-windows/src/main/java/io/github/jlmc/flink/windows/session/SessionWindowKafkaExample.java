package io.github.jlmc.flink.windows.session;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.formats.json.JsonSerializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows;
import java.time.Duration;

/**
 * Real-world example using Session Windows. 
 * Windows close after a period of inactivity (gap) of 5 seconds for a specific sensor.
 */
public class SessionWindowKafkaExample {

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("InaccessibleObjectException") ||
                    (e.getCause() != null && e.getCause().getMessage() != null && e.getCause().getMessage().contains("InaccessibleObjectException"))) {
                System.err.println("\n[ERROR] Erro de encapsulamento do JDK detectado!");
                System.err.println("[ERROR] Para corrigir, adicione os seguintes argumentos em 'VM Options' na configuração de execução da sua IDE:");
                System.err.println("\n--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/sun.net.util=ALL-UNNAMED\n");
            }
            throw e;
        }
    }

    private static void run(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        org.apache.flink.api.java.utils.ParameterTool parameters = org.apache.flink.api.java.utils.ParameterTool.fromArgs(args);
        String bootstrapServers = parameters.get("bootstrap.servers", "localhost:9092");
        String inputTopic = parameters.get("input.topic", "sensors-data");
        String outputTopic = parameters.get("output.topic", "sensors-session-max");
        long sessionGapSeconds = parameters.getLong("session.gap.seconds", 5L);
        long maxOutOfOrdernessSeconds = parameters.getLong("watermark.max.out.of.orderness.seconds", 2L);

        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("session-windows-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(
                        SensorReading.class,
                        () -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            return objectMapper;
                        }
                ))
                .build();

        WatermarkStrategy<SensorReading> watermarkStrategy = WatermarkStrategy
                .<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(maxOutOfOrdernessSeconds))
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli());

        DataStream<SensorReading> sensorStream = env.fromSource(source, watermarkStrategy, "Kafka Sensor Source");

        JsonSerializationSchema<SensorReading> serializationSchema = new JsonSerializationSchema<>(
                () -> {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    return objectMapper;
                }
        );

        KafkaSink<SensorReading> sink = KafkaSink.<SensorReading>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(outputTopic)
                                .setKeySerializationSchema((SerializationSchema<SensorReading>) element ->
                                        element.id.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                .setValueSerializationSchema(serializationSchema)
                                .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        // How often events are written to Kafka sink:
        // There is no fixed periodic interval.
        // A result is emitted when a session window closes, which happens after
        // `session.gap.seconds` of inactivity for each sensor id, plus watermark progress.
        //
        // To alter/improve behavior:
        // - Increase `session.gap.seconds` to emit less frequently (larger sessions).
        // - Decrease `session.gap.seconds` to emit more frequently (smaller sessions).
        // - Tune `watermark.max.out.of.orderness.seconds` for late/out-of-order events.
        sensorStream
                .keyBy(r -> r.id)
                .window(EventTimeSessionWindows.withGap(Duration.ofSeconds(sessionGapSeconds)))
                .reduce((r1, r2) -> new SensorReading(r1.id, r1.timestamp, Math.max(r1.temperature, r2.temperature)))
                .sinkTo(sink);

        env.execute("Flink Session Window Kafka Example");
    }
}
