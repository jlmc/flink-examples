package io.github.jlmc.flink.j4;

import io.github.jlmc.flink.j4.models.Person;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.client.program.ClusterClient;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.InjectClusterClient;
import org.apache.flink.test.junit5.InjectMiniCluster;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MiniClusterExtension.class)
public class FileTextSourceExampleMiniClusterTest {

    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }

    }

    @Test
    void testSocketTextStream(
            @InjectMiniCluster MiniCluster miniCluster,
            @InjectClusterClient ClusterClient<?> clusterClient
    ) throws Exception {
        URL resource = getClass().getClassLoader().getResource("input.txt");
        assertNotNull(resource);
        String path = resource.getPath();

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);
        SingleOutputStreamOperator<String> stream = TextFileSourceExample.buildStream(
                env,
                path
        );


        List<String> collected = stream.executeAndCollect("MiniCluster Socket Test", 5);

        assertEquals(List.of(
                        "hello flink",
                        "hello java",
                        "hello Duke",
                        "Great hello",
                        "nothing else mater"),
                collected);
    }

    @Test
    void testFileTextBoundedStream(
            @InjectMiniCluster MiniCluster miniCluster,
            @InjectClusterClient ClusterClient<?> clusterClient,
            @TempDir Path tempDir          // JUnit injects a temporary folder
    ) throws Exception {
        final AtomicInteger counter = new AtomicInteger();

        Runnable addFileTask = () -> {
            try {
                int idx = counter.getAndIncrement();
                final String file = "part-" + System.currentTimeMillis() + ".txt";
                switch (idx % 2) {
                    case 0 -> Files.writeString(tempDir.resolve(file), """
                            hello flink
                            hello java
                            """);
                    case 1 -> Files.writeString(tempDir.resolve(file), """
                            hello Duke
                            Great hello
                            """);
                    default -> executor.shutdown();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // Run every 300ms, first delay 300ms
        executor.scheduleAtFixedRate(addFileTask, 1, 2, TimeUnit.SECONDS);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);
        SingleOutputStreamOperator<String> stream = TextFileSourceExample.buildBoundedStream(
                env,
                tempDir.toString()
        );

        var collected = stream.executeAndCollect("MiniCluster Socket Test", 10);


        System.out.println(collected);

        assertEquals(
                Set.of(
                        "hello java",
                        "hello Duke",
                        "Great hello",
                        "hello flink"
                ),
                new HashSet<>(collected)
        );
    }

    @Test
    void testCSVFileTextBoundedStream(
            @InjectMiniCluster MiniCluster miniCluster,
            @InjectClusterClient ClusterClient<?> clusterClient,
            @TempDir Path tempDir          // JUnit injects a temporary folder
    ) throws Exception {
        AtomicInteger fileCounter = new AtomicInteger();
        AtomicInteger recordCounter = new AtomicInteger();

        Runnable addFileTask = () -> {
            try {
                int fileIdx = fileCounter.getAndIncrement();
                int linesPerFile = 5; // can be any number
                String fileName = String.format("part-%03d.csv", fileIdx);

                StringBuilder content = fileContent(linesPerFile, recordCounter);

                Files.writeString(tempDir.resolve(fileName), content.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        executor.scheduleAtFixedRate(addFileTask, 1, 2, TimeUnit.SECONDS);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.STREAMING);
        env.setParallelism(1);
        SingleOutputStreamOperator<Person> stream = TextFileSourceExample.buildBoundedCSVStream(
                env,
                tempDir.toString()
        );

        var collected = stream.executeAndCollect("MiniCluster Socket Test", 10);


        System.out.println(collected);
        Assertions.assertEquals(10, collected.size());
    }

    private static StringBuilder fileContent(int linesPerFile, AtomicInteger recordCounter) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < linesPerFile; i++) {
            int id = recordCounter.getAndIncrement();
            String name = switch (i % 4) {
                case 0 -> "Duke";
                case 1 -> "Spiderman";
                case 2 -> "Superman";
                default -> "Batman";
            };
            int age = 20 + (id % 30); // just an example
            content.append("%d,%d,%s%n".formatted(id, age, name));
        }
        return content;
    }
}
