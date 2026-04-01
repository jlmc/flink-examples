package io.github.jlmc.flink.sinks;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.time.Instant;

/**
 * # SocketSinkConnector
 *
 * ### Input socket in terminal
 * ```sh
 * nc -lk 9998
 * ```
 * ### output socket in terminal
 * ```
 * nc -lk 9999
 * ```
 */
public class SocketSinkConnector {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());



        env.socketTextStream("localhost", 9998)
                .map(value -> String.format("\n{\n    \"value\": \"%s\",\n    \"timestamp\": \"%s\",\n}\n", value, Instant.now()), Types.STRING)
                .writeToSocket("localhost", 9999, new SimpleStringSchema());

        env.execute("SocketSinkConnector Job");
    }
}
