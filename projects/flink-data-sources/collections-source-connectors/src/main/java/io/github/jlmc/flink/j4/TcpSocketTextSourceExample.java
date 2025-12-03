package io.github.jlmc.flink.j4;

import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 *  To run this example, we must open a listening TCP socket (server)
 *  on the address localhost and port 9999.
 *  - To achieve this, we can use the netcat (nc) command:
 *   ```bash
 *   nc -lk 9999
 *   ```
 */
public class TcpSocketTextSourceExample {

    public static SingleOutputStreamOperator<String> buildStream(StreamExecutionEnvironment env, String host, int port) {
        return env
                .socketTextStream(host, port, "\n")
                .map(String::toUpperCase);
    }
}
