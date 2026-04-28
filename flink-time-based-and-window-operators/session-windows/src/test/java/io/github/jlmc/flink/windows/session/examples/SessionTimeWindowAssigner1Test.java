package io.github.jlmc.flink.windows.session.examples;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTimeWindowAssigner1Test {

    static final MiniClusterWithClientResource FLINK_CLUSTER =
            new MiniClusterWithClientResource(
                    new MiniClusterResourceConfiguration.Builder()
                            .setNumberSlotsPerTaskManager(2)
                            .setNumberTaskManagers(1)
                            .build());

    @BeforeAll
    static void beforeAll() throws Exception {
        FLINK_CLUSTER.before();
    }

    @AfterAll
    static void afterAll() {
        FLINK_CLUSTER.after();
    }

    @BeforeEach
    void setUp() {
        CollectSink.clear();
    }

    @Test
    void shouldCountEventsPerSessionIdAcrossSessions() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        SourceFunction<String> source = new SourceFunction<>() {
            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                ctx.collect("s1,/home,1");
                Thread.sleep(20);
                ctx.collect("s1,/products,2");
                Thread.sleep(200);
                ctx.collect("s1,/cart,3");
                Thread.sleep(20);
                ctx.collect("s2,/home,4");
                Thread.sleep(200);
            }

            @Override
            public void cancel() {
                // no-op
            }
        };

        SessionTimeWindowAssigner1.definePipeline(env.addSource(source), Duration.ofMillis(100))
                .addSink(new CollectSink<>());

        env.execute("SessionTimeWindowAssigner1Test");

        List<Tuple2<String, Long>> values = CollectSink.values();
        assertThat(values).hasSize(3);
        assertThat(values)
                .anySatisfy(value -> {
                    assertThat(value.f0).isEqualTo("s1");
                    assertThat(value.f1).isEqualTo(2L);
                })
                .anySatisfy(value -> {
                    assertThat(value.f0).isEqualTo("s1");
                    assertThat(value.f1).isEqualTo(1L);
                })
                .anySatisfy(value -> {
                    assertThat(value.f0).isEqualTo("s2");
                    assertThat(value.f1).isEqualTo(1L);
                });
    }
}
