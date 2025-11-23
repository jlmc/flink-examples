package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

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
 * ---
 * ## About the logging
 * ```bash
 * export LOG_JSON=true
 * if [ "$LOG_JSON" = "true" ]; then
 *     FLINK_LOG_CONF="-Dlog4j.configurationFile=log4j2-json.xml"
 * else
 *     FLINK_LOG_CONF="-Dlog4j.configurationFile=log4j2-text.xml"
 * fi
 *
 * flink run $FLINK_LOG_CONF -c my.job.Class my-flink-job.jar
 * ```
 */
public class FlinkLocalEnvironmentForDevelopJob {

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);

        env.socketTextStream("localhost", 9999)
                .flatMap((String line, Collector<String> out) -> {

                    String[] splits = line.split("\\s+"); // split by space
                    Arrays.stream(splits).forEach(out::collect);

                }, Types.STRING)
                .map(word -> Tuple2.of(word, 1L), Types.TUPLE(Types.STRING, Types.LONG))
                .keyBy(t -> t.f0)
                .sum(1)
                .print();

        env.execute("Flink Local Environment For Develop Job");
    }
}
