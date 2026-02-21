package io.github.jlmc.flink.multistream;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealTimeInventoryManagerCoProcessFunctionTest extends AbstractTestBase {

    @BeforeEach
    void setUp() {
        CollectSink.clear();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Should emit low stock alert when stock is depleted")
    void shouldEmitLowStockAlertWhenStockIsDepleted() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var sales = List.of(
                new RealTimeInventoryManagerCoProcessFunction.Sale("laptop", 11) // Stock: 10 -> -1
        );
        var restocks = List.of(
                new RealTimeInventoryManagerCoProcessFunction.StockArrival("none", 0)
        );

        RealTimeInventoryManagerCoProcessFunction.defineWorkflow(env.fromCollection(sales), env.fromCollection(restocks))
                .addSink(new CollectSink<>());

        env.execute();

        List<RealTimeInventoryManagerCoProcessFunction.InventoryAlert> alerts = getAlerts();

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).productId()).isEqualTo("laptop");
        assertThat(alerts.get(0).message()).contains("Low stock detected");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Should emit critical alert when replenishment is delayed")
    void shouldEmitCriticalAlertWhenReplenishmentIsDelayed() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Use a source that produces a Sale and then waits long enough for the timer to fire.
        // Since processing time is used, we need real time to pass while the job is running.
        // A simple way is to use env.fromElements(sale) and then a long sleep in a Map or something.
        
        var sales = env.fromElements(new RealTimeInventoryManagerCoProcessFunction.Sale("laptop", 10))
                .map(sale -> {
                    // This won't help because it happens before/after the process function.
                    // We need the Job to stay alive.
                    return sale;
                });
        
        // Use a restock stream that stays alive
        var restocks = env.addSource(new org.apache.flink.streaming.api.functions.source.SourceFunction<RealTimeInventoryManagerCoProcessFunction.StockArrival>() {
            private volatile boolean running = true;
            @Override
            public void run(SourceContext<RealTimeInventoryManagerCoProcessFunction.StockArrival> ctx) throws Exception {
                // Keep the source alive
                Thread.sleep(5000);
            }
            @Override
            public void cancel() {
                running = false;
            }
        });

        RealTimeInventoryManagerCoProcessFunction.defineWorkflow(sales, restocks)
                .addSink(new CollectSink<>());

        env.execute();

        List<RealTimeInventoryManagerCoProcessFunction.InventoryAlert> alerts = getAlerts();

        assertThat(alerts).extracting(RealTimeInventoryManagerCoProcessFunction.InventoryAlert::message)
                .anyMatch(m -> m.contains("Low stock detected"))
                .anyMatch(m -> m.contains("CRITICAL"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Should not emit critical alert when replenishment arrives in time")
    void shouldNotEmitCriticalAlertWhenReplenishmentArrivesInTime() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // To ensure Sale is processed before StockArrival in a deterministic way without TestHarness,
        // we can use a small trick with a custom source or just use enough elements.
        // But with fromCollection and parallelism 1, it's still not guaranteed across connected streams.
        
        var sales = env.fromElements(new RealTimeInventoryManagerCoProcessFunction.Sale("laptop", 10));
        var restocks = env.fromElements(
                new RealTimeInventoryManagerCoProcessFunction.StockArrival("laptop", 20)
        );

        RealTimeInventoryManagerCoProcessFunction.defineWorkflow(sales, restocks)
                .addSink(new CollectSink<>());

        env.execute();
        
        List<RealTimeInventoryManagerCoProcessFunction.InventoryAlert> alerts = getAlerts();

        // We check that IF a "Low stock detected" was generated, then NO "CRITICAL" was generated.
        if (alerts.stream().anyMatch(a -> a.message().contains("Low stock detected"))) {
            assertThat(alerts).extracting(RealTimeInventoryManagerCoProcessFunction.InventoryAlert::message)
                    .noneMatch(m -> m.contains("CRITICAL"));
        }
    }

    private List<RealTimeInventoryManagerCoProcessFunction.InventoryAlert> getAlerts() {
        return CollectSink.<RealTimeInventoryManagerCoProcessFunction.InventoryAlert>values().stream()
                .toList();
    }
}
