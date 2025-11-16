package io.github.jlmc.flink.j5;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

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
 * ## Host and port can now be provided via
 *
 *  1. CLI args:   --host myhost --port 8888
 *  2. Env vars:   FLINK_SOCKET_HOST, FLINK_SOCKET_PORT
 *  3. Defaults:   localhost, 9999
 *
 *  Alternatively we can use also picocli `info.picocli:picocli:4.7.6`
 */
public class FlinkDataStreamApiAndJava8LambdaExpression {


    record SocketConfiguration(String host, int port) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(FlinkDataStreamApiAndJava8LambdaExpression.class);

    /**
     * Example showing how to supply configuration via:
     *
     * 1. CLI arguments:  --host myhost --port 8888
     * 2. Environment vars:  FLINK_SOCKET_HOST, FLINK_SOCKET_PORT
     * 3. Defaults:  localhost:9999
     *
     * This version integrates Flink's ParameterTool for robust configuration handling.
     */
    public static void main(String[] args) throws Exception {

        // 1️⃣ Extract all configuration cleanly
        SocketConfiguration socketConfiguration = resolveSocketConfigUsingParamTool(args);
        LOGGER.info("Using socket source: {}:{}", socketConfiguration.host(), socketConfiguration.port());

        // 1️⃣ Create the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // ⚠️ CRITICAL: Set the execution mode to STREAMING for bounded input processing
        // we can set the runtime mode also using a job parameter
        // bin/flink run -Dexecution.runtime-mode=BATCH
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1); // Setting parallelism to 1 simplifies local output reading

        // ----------------------------------------------------------------------
        // ❗ What this line does:
        // env.getConfig().setGlobalJobParameters(ParameterTool.fromArgs(args));
        //
        // ✔ Reads command-line arguments into a ParameterTool instance
        // ✔ Stores those parameters in the global job configuration (ExecutionConfig)
        // ✔ Makes these parameters automatically available to ALL "Rich" operators
        //   such as RichMapFunction, RichFlatMapFunction, RichSourceFunction, etc.
        // ✔ Shows the parameters in the Flink Web UI under:
        //     Job → Configuration → Global Job Parameters
        //
        // This is useful only when you need operators to read parameters inside open()
        // public class MyRichMap extends RichMapFunction<String, String> {
        //    @Override
        //    public void open(Configuration parameters) {
        //        ParameterTool p = (ParameterTool) getRuntimeContext()
        //                .getExecutionConfig()
        //                .getGlobalJobParameters();
        //
        //        String host = p.get("host");
        //        int port = p.getInt("port");
        //
        //        System.out.println("Host: " + host);
        //        System.out.println("Port: " + port);
        //    }
        //}
        // Otherwise, the line is optional.
        // ----------------------------------------------------------------------
        env.getConfig().setGlobalJobParameters(ParameterTool.fromArgs(args));

        // 2️⃣ Define the file source using the modern Flink API (Socket)
        env.socketTextStream(socketConfiguration.host(), socketConfiguration.port())
                .flatMap((String line, Collector<Tuple2<String, Long>> collector) -> {
                    String normalizedLine = line.trim().toLowerCase()
                            .replaceAll("[^a-z0-9\\s]", "");

                    Arrays.stream(normalizedLine.split("\\s+"))
                            .filter(word -> !word.isEmpty())
                            .forEach(word -> collector.collect(new Tuple2<>(word, 1L)));
                })
                //  We need this because Java lambdas erase generic type information.
                //  Flink cannot automatically infer that the output type of the flatMap is Tuple2<String, Long>.
                //  Without this, Flink falls back to Object type, breaking serialization and state handling.
                //.returns(new TypeHint<Tuple2<String, Long>>() {})
                // or alternatively
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .keyBy(value -> value.f0)
                .sum(1)
                .print();

        // 6️⃣ Execute the job
        env.execute("Flink Works Counter Batch Processing");
    }

    private static SocketConfiguration resolveSocketConfig(String[] args) {
        // Defaults
        String host = "localhost";
        int port = 9999;

        // Environment variables (medium priority)
        String envHost = System.getenv("FLINK_SOCKET_HOST");
        String envPort = System.getenv("FLINK_SOCKET_PORT");

        if (envHost != null) host = envHost;
        if (envPort != null) port = Integer.parseInt(envPort);

        // CLI args (highest priority)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> {
                    if (i + 1 < args.length) host = args[++i];
                }
                case "--port" -> {
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                }
            }
        }

        return new SocketConfiguration(host, port);
    }

    // -----------------------------------------------------------------------------
    //  Helper method: Resolves host + port using ParameterTool + env vars + defaults
    // -----------------------------------------------------------------------------
    private static SocketConfiguration resolveSocketConfigUsingParamTool(String[] args) {

        // Step 1: Load CLI params
        ParameterTool cliParams = ParameterTool.fromArgs(args);

        // Step 2: Load env vars (convert to ParameterTool)
        ParameterTool envParams = ParameterTool.fromMap(System.getenv());

        // Step 3: Merge CLI > ENV > defaults
        // CLI has highest priority
        String host =
                cliParams.get("host",
                        envParams.get("FLINK_SOCKET_HOST", "localhost"));

        int port =
                cliParams.getInt("port",
                        Integer.parseInt(envParams.get("FLINK_SOCKET_PORT", "9999")));

        return new SocketConfiguration(host, port);
    }
}
