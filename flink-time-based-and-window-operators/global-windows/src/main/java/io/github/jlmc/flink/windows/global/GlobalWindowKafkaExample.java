package io.github.jlmc.flink.windows.global;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;

/**
 * Real-world example using Global Windows.
 * Uses a CountTrigger to fire the window every 5 elements per key.
 */
public class GlobalWindowKafkaExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        org.apache.flink.api.java.utils.ParameterTool parameters = org.apache.flink.api.java.utils.ParameterTool.fromArgs(args);
        String bootstrapServers = parameters.get("bootstrap.servers", "localhost:9092");
        String inputTopic = parameters.get("input.topic", "sensors-data");

        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(inputTopic)
                .setGroupId("global-windows-group")
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

        DataStream<SensorReading> sensorStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Sensor Source");

        // Global Window triggered every 5 elements
        sensorStream
                .keyBy(r -> r.id)
                .window(GlobalWindows.create())
                .trigger(CountTrigger.of(5))
                .reduce((r1, r2) -> new SensorReading(r1.id, r1.timestamp, (r1.temperature + r2.temperature) / 2))
                .print();

        env.execute("Flink Global Window Kafka Example");
    }
}
