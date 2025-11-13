package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To run this example, we must open a listening TCP socket (server)
 * on the address localhost and port 9999.
 * * To achieve this, we can use the netcat (nc) command:
 * ```bash
 * nc -lk 9999
 * ```
 * Where:
 * - 'l' (listen): Puts netcat in listen mode (server).
 * - 'k' (keep-open): Keeps the connection open for multiple clients or after a disconnect (useful for testing).
 * * After running the command, the terminal will be ready to receive (and send) data.
 * You can then start your client/server program which will connect to this port.
 */
public class WorksCounterStreamSocketProcessing {

    public static final Logger LOGGER = LoggerFactory.getLogger(WorksCounterStreamSocketProcessing.class);

    public static void main(String[] args) throws Exception {
        // 1️⃣ Create the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // ⚠️ CRITICAL: Set the execution mode to STREAMING for bounded input processing
        // we can set the runtime mode also using a job parameter
        // bin/flink run -Dexecution.runtime-mode=BATCH
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1); // Setting parallelism to 1 simplifies local output reading

        // 2️⃣ Define the file source using the modern Flink API (Socket)

        DataStreamSource<String> sourceStream = env.socketTextStream("localhost", 9999);

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
}
