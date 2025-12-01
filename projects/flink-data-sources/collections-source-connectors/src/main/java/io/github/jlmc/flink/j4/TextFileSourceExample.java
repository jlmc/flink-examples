package io.github.jlmc.flink.j4;

import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Duration;

/// In order to use the File Source connector, you need to include the following Maven dependency:
/// ```
/// <dependency>
///     <groupId>org.apache.flink</groupId>
///     <artifactId>flink-connector-files</artifactId>
///     <version>${flink.version}</version>
/// </dependency>
///```
///
public class TextFileSourceExample {

    public static SingleOutputStreamOperator<String> buildStream(StreamExecutionEnvironment env, String filePath) {

        FileSource<String> fileSource =
                FileSource.forRecordStreamFormat(
                        new TextLineInputFormat(),
                        new org.apache.flink.core.fs.Path(filePath)
                ).build();

        env.setParallelism(1);
        return env.fromSource(fileSource, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "file-source");
    }

    public static SingleOutputStreamOperator<String> buildBoundedStream(StreamExecutionEnvironment env, String filePath) {
        FileSource<String> fileSource =
                FileSource.forRecordStreamFormat(
                        new TextLineInputFormat(),
                        new org.apache.flink.core.fs.Path(filePath)
                )
                        .monitorContinuously(Duration.ofSeconds(10)) // Enable continuous monitoring for new files
                        .build();

        env.setParallelism(1);
        return env.fromSource(fileSource, org.apache.flink.api.common.eventtime.WatermarkStrategy.noWatermarks(), "file-source");
    }
}
