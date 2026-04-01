package io.github.jlmc.flink.sinks.kafka;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

/**
 * Example of Flink KafkaSink writing Key-Value messages to a Kafka topic.
 */
public class KafkaSinkKeyValueExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Enable Flink checkpointing every 10 seconds (10,000 ms)
        // This is crucial for At-Least-Once or Exactly-Once delivery guarantees.
        env.enableCheckpointing(10_000, CheckpointingMode.EXACTLY_ONCE);

        DataGeneratorSource<Tuple2<String, String>> source = new DataGeneratorSource<>(
                value -> Tuple2.of("key-" + (value % 10), "value-" + value),
                100L,
                RateLimiterStrategy.perSecond(1L),
                org.apache.flink.api.common.typeinfo.Types.TUPLE(
                        org.apache.flink.api.common.typeinfo.Types.STRING,
                        org.apache.flink.api.common.typeinfo.Types.STRING)
        );

        KafkaSink<Tuple2<String, String>> sink = KafkaSink.<Tuple2<String, String>>builder()
                .setBootstrapServers("kafka:19092")
                .setRecordSerializer((tuple, context, timestamp) -> new ProducerRecord<>(
                        "key-value-topic",
                        tuple.f0.getBytes(StandardCharsets.UTF_8),
                        tuple.f1.getBytes(StandardCharsets.UTF_8)
                ))
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-key-value-generator")
                .sinkTo(sink);

        env.execute("Flink Kafka Sink Key-Value Example");
    }
}
