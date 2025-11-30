package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.jlmc.flink.j4.JavaCollectionSourcesConnectorExampleJob.buildWithFromCollection;
import static io.github.jlmc.flink.j4.JavaCollectionSourcesConnectorExampleJob.buildWithFromElements;
import static io.github.jlmc.flink.j4.JavaCollectionSourcesConnectorExampleJob.buildWithFromSequence;
import static io.github.jlmc.flink.j4.JavaCollectionSourcesConnectorExampleJob.buildWithFromData;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaCollectionSourcesConnectorExampleJobMiniClusterTest {

    private static MiniCluster miniCluster;
    private StreamExecutionEnvironment env;

    @BeforeAll
    static void startCluster() throws Exception {

        MiniClusterConfiguration cfg =
                new MiniClusterConfiguration.Builder()
                        .setNumTaskManagers(1)
                        .setNumSlotsPerTaskManager(4)
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


     @SuppressWarnings("unused")
    //@BeforeEach
    void setupOld() {
        // we should not use createLocalEnvironment
        // otherwise the job will not be executed in MiniCluster
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setRuntimeMode(RuntimeExecutionMode.BATCH); // Optional, use BATCH for deterministic tests
    }

    @BeforeEach
    void setupInMiniCluster() throws Exception {
        Configuration config = new Configuration();

        config.setString("execution.target", "mini-cluster");
        config.setInteger("taskmanager.numberOfTaskSlots", 4);

        // Using MiniCluster as executor
        // config.setString("rest.bind-address", "localhost");
        // config.setInteger("rest.bind-port", miniCluster.getRestAddress().get().getPort());

        config.set(RestOptions.BIND_ADDRESS, "localhost");
        config.set(RestOptions.BIND_PORT,
                String.valueOf(miniCluster.getRestAddress().get().getPort()));


        //set(ConfigOption, Object)

        env = StreamExecutionEnvironment.getExecutionEnvironment(config);

        env.setRuntimeMode(RuntimeExecutionMode.BATCH);
        env.setParallelism(1);
    }

    //@BeforeEach
    @SuppressWarnings("unused")
    void setUpWithMiniCluster() {
        Configuration config = new Configuration();
        config.setString("execution.target", "mini-cluster");  // chave para funcionar!

        config.setInteger("taskmanager.numberOfTaskSlots", 4);

        env = StreamExecutionEnvironment.getExecutionEnvironment(config);
        env.setRuntimeMode(RuntimeExecutionMode.BATCH);
        env.setParallelism(1);
    }


    @Test
    void testFromCollectionWithMiniCluster() throws Exception {
        // Build your stream
        SingleOutputStreamOperator<String> stream = buildWithFromCollection(
                env,
                new JavaCollectionSourcesConnectorExampleJob.Configuration("duke", 1)
        );

        // Execute the job and collect the results synchronously
        List<String> collected = stream.executeAndCollect("MiniCluster Test", 100);

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

    @Test
    void testFromSourceWithMiniCluster() throws Exception {
        // Build your stream
        SingleOutputStreamOperator<String> stream = buildWithFromData(
                env,
                new JavaCollectionSourcesConnectorExampleJob.Configuration("duke", 1)
        );

        // Execute the job and collect the results synchronously
        List<String> collected = stream.executeAndCollect("MiniCluster Test", 100);

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

    @Test
    void testfromElementsnWithMiniCluster() throws Exception {
        // Build your stream
        SingleOutputStreamOperator<String> stream = buildWithFromElements(
                env,
                new JavaCollectionSourcesConnectorExampleJob.Configuration("duke", 1)
        );

        // Execute the job and collect the results synchronously
        List<String> collected = stream.executeAndCollect("MiniCluster Test", 100);

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


    @Test
    void testFromSequenceWithMiniCluster() throws Exception {
        // Build your stream
        SingleOutputStreamOperator<String> stream = buildWithFromSequence(env);

        // Execute the job and collect the results synchronously
        List<String> collected = stream.executeAndCollect("MiniCluster Test", 100);

        // Assertions
        assertEquals(List.of(
                "Window closed at event-time 2000",
                "Window closed at event-time 5000"
        ), collected);
    }
}

