package io.github.jlmc.flink.sinks.hdfs;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;

import java.time.Duration;

/**
 * Example of a Flink Job that writes data to HDFS using FileSink.
 */
public class HdfsFileSystemSinkConnectorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Checkpointing is crucial for FileSink to finalize writes to HDFS.
        env.enableCheckpointing(5_000, CheckpointingMode.EXACTLY_ONCE);

        DataGeneratorSource<String> source = new DataGeneratorSource<>(
                value -> "HDFS-Data-Event-" + value,
                Long.MAX_VALUE,
                RateLimiterStrategy.perSecond(100),
                org.apache.flink.api.common.typeinfo.Types.STRING
        );

        // HDFS output path. Ensure 'namenode' is reachable from the Flink TaskManager.
        String hdfsPath = "hdfs://namenode:9000/flink/output/hdfs-sink";

        FileSink<String> hdfsSink = FileSink
                .forRowFormat(new Path(hdfsPath), new SimpleStringEncoder<String>("UTF-8"))
                .withBucketAssigner(new DateTimeBucketAssigner<>())
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(Duration.ofSeconds(10))
                                .withInactivityInterval(Duration.ofSeconds(5))
                                .withMaxPartSize(1024 * 1024 * 128) // 128MB
                                .build())
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "hdfs-generator")
                .sinkTo(hdfsSink);

        env.execute("Flink HDFS Sink Connector Example");
    }
}
