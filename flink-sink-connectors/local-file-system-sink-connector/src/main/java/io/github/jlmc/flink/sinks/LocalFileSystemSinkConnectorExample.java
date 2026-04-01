package io.github.jlmc.flink.sinks;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class LocalFileSystemSinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());


        env.enableCheckpointing(5_000, CheckpointingMode.EXACTLY_ONCE);

        // The source of the data
        DataGeneratorSource<String> stringDataGeneratorSource = new DataGeneratorSource<>(
                new GeneratorFunction<Long, String>() {
                    @Override
                    public String map(Long value) {
                        return "The generated text line and No.: " + value;
                    }
                },
                1_000L,
                RateLimiterStrategy.perSecond(1L),
                Types.STRING
        );

        //String outputFilePath = "/Users/jlmc/IdeaProjects/apache-flink/flink-sink-connectors/local-file-system-sink-connector/outputs";
        String outputFilePath = "/tmp/flink-output";

        //Path.fromLocalFile(new java.io.File("/tmp/flink-output").toPath()).toUri().toString();
        FileSink<String> fileSink = FileSink
                .<String>forRowFormat(
                        new org.apache.flink.core.fs.Path(outputFilePath),
                        new SimpleStringEncoder<>(StandardCharsets.UTF_8.name())

                ).withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withMaxPartSize(MemorySize.parse("250", MemorySize.MemoryUnit.BYTES))
                                //.withInactivityInterval(Duration.ofSeconds(10))
                                .withRolloverInterval(Duration.ofSeconds(30))
                                .build()
                )
                .withBucketAssigner(new DateTimeBucketAssigner<>("yyyy-MM-dd-HH"))
                .withOutputFileConfig(new OutputFileConfig(
                                "text-file",
                                ".txt"
                        )
                )
                .build();

        env.fromSource(
                        stringDataGeneratorSource,
                        WatermarkStrategy.noWatermarks(),
                        "string-data-generator-source"
                )
                .map(value -> value, Types.STRING)
                .sinkTo(fileSink);


        env.execute("Local File System Sink Connector - Text");

    }
}
