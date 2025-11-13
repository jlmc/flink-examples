package io.github.jlmc.flink.j3;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Optional;

public class WorksCounterStreamProcessing {

    public static final Logger LOGGER = LoggerFactory.getLogger(WorksCounterStreamProcessing.class);

    public static void main(String[] args) throws Exception {
        String inputPath = getSourceFilePath(args).orElse(null);
        if (inputPath == null) {
            LOGGER.error("Missing required parameter 'sourceFilePath'");
            // For local IDE testing, uncomment and set a fixed path below, but remember to comment it out for production JARs.
            // final String inputPath = "file:///path/to/your/local/input.txt";
            // if (true) { args = new String[] { inputPath }; } else { return; }
            return;
        }

        // 1️⃣ Create the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // ⚠️ CRITICAL: Set the execution mode to STREAMING for bounded input processing
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1); // Setting parallelism to 1 simplifies local output reading

        // 2️⃣ Define the file source using the modern Flink API (FileSource)
        final FileSource<String> source = FileSource
                .forRecordStreamFormat(new TextLineInputFormat(), Path.fromLocalFile(new File(inputPath)))
                .build();

        // 3️⃣ Create the DataStream using fromSource
        // 3️⃣ WatermarkStrategy.noWatermarks()
        DataStreamSource<String> sourceStream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(), // Watermarks are not strictly needed in BATCH mode
                "File input source"
        );

        // 4️⃣ Apply transformations: KeyBy the word and sum the counts
        DataStream<Tuple2<String, Long>> flatMapOperator = sourceStream
                .flatMap(new Tokenizer())
                .name("Tokenizer");

        DataStream<Tuple2<String, Long>>  wordCounts =
                flatMapOperator
                        .keyBy(value -> value.f0) // keyBy the word (field 0)
                        .sum(1) // sum the count (field 1)
                        .name("Word Counts");


        // 5️⃣ Sink: output the result
        wordCounts.print().name("Result Sink");

        // 6️⃣ Execute the job
        env.execute("Flink Works Counter Batch Processing");

    }

    private static Optional<String> getSourceFilePath(String[] args) {
        if (args.length >= 1) {
            // For local IDE testing, uncomment and set a fixed path below, but remember to comment it out for production JARs.
            // final String inputPath = "file:///path/to/your/local/input.txt";
            // if (true) { args = new String[] { inputPath }; } else { return; }
            final String inputPath = args[0];
            LOGGER.info("📄 Using provided input path: {}", inputPath);
            return Optional.of(inputPath);
        }

        // 2️⃣ Otherwise, build a path relative to where the program is launched
        // Example: projectRoot/inputs/input.txt
        // 2️⃣ Otherwise, build a default path relative to the project directory
        String projectDir = System.getProperty("user.dir");
        String relativePath = "inputs/input.txt";
        String resolvedPath = projectDir + "/" + relativePath;

        LOGGER.info("⚙️ Using default input path: " + resolvedPath);
        return Optional.of(resolvedPath);

    }
}
