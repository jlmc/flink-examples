package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.client.program.ClusterClient;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.InjectClusterClient;
import org.apache.flink.test.junit5.InjectMiniCluster;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.jlmc.flink.j4.TcpSocketTextSourceExample.buildStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MiniClusterExtension.class)
public class TcpSocketTextSourceExampleMiniClusterTest {

    private ServerSocket serverSocket;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws IOException {
        serverSocket = new ServerSocket(0); // 0 = assign random available port
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
    void testSocketTextStream(
            @InjectMiniCluster MiniCluster miniCluster,
            @InjectClusterClient ClusterClient<?> clusterClient
    ) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);
        SingleOutputStreamOperator<String> stream = buildStream(
                env,
                serverSocket.getInetAddress().getHostName(),
                serverSocket.getLocalPort()
        );


        executor.submit(() -> {
            try (Socket client = serverSocket.accept();
                 PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {

                // little delay to ensure that the Flink reader is ready to consume message
                //Thread.sleep(100);

                writer.println("hello");
                writer.println("world");
            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
        });

        List<String> collected = stream.executeAndCollect("MiniCluster Socket Test", 5);

        assertEquals(List.of("HELLO", "WORLD"), collected);
    }
}
