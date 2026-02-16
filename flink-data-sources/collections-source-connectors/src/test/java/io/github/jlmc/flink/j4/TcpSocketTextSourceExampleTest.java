package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TcpSocketTextSourceExampleTest {

    private ServerSocket serverSocket;
    private int port = 0;
    private ExecutorService executor;


    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // 0 = assign random available port
        port = serverSocket.getLocalPort();
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @Test
    void testSocketTextStream() throws Exception {
        executor.submit(() -> {
            try (Socket client = serverSocket.accept();
                 PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {

                writer.println("hello");
                writer.println("world");

            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        });

        //Create Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING); // Batch mode for bounded sources env.setParallelism(1);
        env.setParallelism(1);
        SingleOutputStreamOperator<String> stream = TcpSocketTextSourceExample.buildStream(env, "localhost", port);

        // Execute the pipeline
        List<String> collected = stream.executeAndCollect("Collecting the strings from socket", 5);

        assertEquals(List.of("HELLO", "WORLD"), collected);
    }
}
