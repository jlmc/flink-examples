package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaCollectionSourcesConnectorExampleJobMiniClusterTest {

    private static MiniCluster miniCluster;
    private StreamExecutionEnvironment env;

    @BeforeAll
    static void startMiniCluster() throws Exception {
        Configuration flinkConfig = new Configuration();

        MiniClusterConfiguration cfg = new MiniClusterConfiguration.Builder()
                .setNumTaskManagers(1)
                .setNumSlotsPerTaskManager(4)
                .setConfiguration(flinkConfig)
                .build();

        miniCluster = new MiniCluster(cfg);
        miniCluster.start();
    }

    @AfterAll
    static void stopMiniCluster() throws Exception {
        if (miniCluster != null) {
            miniCluster.close();
        }
    }

    @BeforeEach
    void setup() {
        env = StreamExecutionEnvironment.createLocalEnvironment(4);
        env.setRuntimeMode(RuntimeExecutionMode.BATCH); // Optional, use BATCH for deterministic tests
    }

    @Test
    void testFromCollectionWithMiniCluster() throws Exception {
        List<String> collected = new CopyOnWriteArrayList<>();

        // Build your stream
        SingleOutputStreamOperator<String> stream = JavaCollectionSourcesConnectorExampleJob.buildWithFromCollection(
                env,
                new JavaCollectionSourcesConnectorExampleJob.Configuration("duke", 1)
        );

        // Add a collecting sink
        stream.addSink(new SinkFunction<>() {
            @Override
            public void invoke(String value) {
                System.out.println(value);
                collected.add(value);
            }
        });

        // Execute the job using the MiniCluster
        env.execute("MiniCluster Test");


        Thread.sleep(10_000);

        // Assertions
        assertEquals(List.of(
                "[a] => A",
                "[b] => B",
                "[c] => C",
                "[d] => D",
                "[e] => E",
                "[f] => F",
                "[g] => G",
                "[h] => H"
        ), collected);
    }
}

