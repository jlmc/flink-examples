package io.github.jlmc.flink.sinks.kafka;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Example of Flink KafkaSink writing Value-Only messages to a Kafka topic.
 */
public class KafkaSinkValueOnlyExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataGeneratorSource<String> source = new DataGeneratorSource<>(
                value -> "Message-" + value,
                100L,
                RateLimiterStrategy.perSecond(1L),
                org.apache.flink.api.common.typeinfo.Types.STRING
        );

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers("kafka:19092")
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("value-only-topic")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build()
                )
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "kafka-value-only-generator")
                .sinkTo(sink);

        env.execute("Flink Kafka Sink Value-Only Example");
    }
}
