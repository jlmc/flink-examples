package io.github.jlmc.flink.windows.tumbling;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import java.time.Duration;

/**
 * Real-world example using Tumbling Windows to calculate the average temperature per sensor every 10 seconds.
 * Consumes JSON events from Kafka.
 */
public class TumblingWindowKafkaExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Kafka Source setup
        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers("kafka:19092")
                .setTopics("sensors-data")
                .setGroupId("tumbling-windows-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(SensorReading.class))
                .build();

        // Watermark Strategy: extracts the event timestamp and allows a 2-second delay
        WatermarkStrategy<SensorReading> watermarkStrategy = WatermarkStrategy
                .<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli());

        DataStream<SensorReading> sensorStream = env.fromSource(source, watermarkStrategy, "Kafka Sensor Source");

        // Apply 10-second Tumbling Window per sensor ID
        sensorStream
                .keyBy((SensorReading r) -> r.id)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(10)))
                .reduce((r1, r2) -> new SensorReading(r1.id, r1.timestamp, (r1.temperature + r2.temperature) / 2))
                .print();

        env.execute("Flink Tumbling Window Kafka Example");
    }
}
