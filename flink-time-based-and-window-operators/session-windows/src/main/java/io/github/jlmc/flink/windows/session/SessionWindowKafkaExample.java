package io.github.jlmc.flink.windows.session;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
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
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<SensorReading> source = KafkaSource.<SensorReading>builder()
                .setBootstrapServers("kafka:19092")
                .setTopics("sensors-data")
                .setGroupId("session-windows-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(SensorReading.class))
                .build();

        WatermarkStrategy<SensorReading> watermarkStrategy = WatermarkStrategy
                .<SensorReading>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((event, timestamp) -> event.timestamp.toEpochMilli());

        DataStream<SensorReading> sensorStream = env.fromSource(source, watermarkStrategy, "Kafka Sensor Source");

        // Session windows with 5-second gap
        sensorStream
                .keyBy(r -> r.id)
                .window(EventTimeSessionWindows.withGap(Duration.ofSeconds(5)))
                .reduce((r1, r2) -> new SensorReading(r1.id, r1.timestamp, Math.max(r1.temperature, r2.temperature)))
                .print();

        env.execute("Flink Session Window Kafka Example");
    }
}
