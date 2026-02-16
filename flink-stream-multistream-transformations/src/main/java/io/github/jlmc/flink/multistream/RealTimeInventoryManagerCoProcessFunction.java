package io.github.jlmc.flink.multistream;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.time.Duration;

public class RealTimeInventoryManagerCoProcessFunction {
    public static void main(String[] args) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Stream 1: Sales arriving
        DataStream<Sale> sales = env.fromElements(
                new Sale("laptop", 2),
                new Sale("laptop", 9) // This will drop stock to -1
        );

        // Stream 2: Inventory restock arriving (delayed in real life)
        // In this test, we don't send anything immediately to trigger the timer
        DataStream<StockArrival> restocks = env.fromElements(
                new StockArrival("smartphone", 50)
        );


        SingleOutputStreamOperator<InventoryAlert> process = defineWorkflow(sales, restocks);

        process.print();
    }

    public static SingleOutputStreamOperator<InventoryAlert> defineWorkflow(DataStream<Sale> sales, DataStream<StockArrival> restocks) {
        return sales.connect(restocks)
                .keyBy(Sale::productId, StockArrival::productId)
                .process(new InventoryMonitor());
    }

    static class InventoryMonitor extends KeyedCoProcessFunction<String, Sale, StockArrival, InventoryAlert> {

        private static final long REPLENISHMENT_TIMEOUT = Duration.ofSeconds(1).toMillis();

        private transient ValueState<Integer> stockLevelState;
        private transient ValueState<Long> criticalTimerState;

        @Override
        public void open(Configuration parameters) {
            this.stockLevelState = getRuntimeContext().getState(new ValueStateDescriptor<>("stock-level", Integer.class));
            this.criticalTimerState = getRuntimeContext().getState(new ValueStateDescriptor<>("timer-ts", Long.class));
        }

        @Override
        public void processElement1(Sale sale, KeyedCoProcessFunction<String, Sale, StockArrival, InventoryAlert>.Context ctx, Collector<InventoryAlert> out) throws Exception {
            Integer currentStock = stockLevelState.value();
            if (currentStock == null) currentStock = 10; // initial support stock = 10

            int updatedStock = currentStock - sale.quantity();
            stockLevelState.update(updatedStock);

            System.out.println("Product: " + ctx.getCurrentKey() + " | Stock: " + updatedStock);

            // If the stock runs out, we schedule a critical alert for 10 seconds in the future.
            if (updatedStock <= 0 && criticalTimerState.value() == null) {
                // Use event time for determinism in tests, but the original logic used processing time.
                // To keep it as is but make it testable, we could allow the timer to be triggered by a "tick"
                // but let's try to fix the test environment first.
                
                long current = ctx.timerService().currentProcessingTime();
                long alertTime = current + REPLENISHMENT_TIMEOUT;
                ctx.timerService().registerProcessingTimeTimer(alertTime);

                criticalTimerState.update(alertTime);

                out.collect(new InventoryAlert(sale.productId(), "Low stock detected. Waiting for replenishment..."));
            }
        }

        @Override
        public void processElement2(StockArrival arrival, KeyedCoProcessFunction<String, Sale, StockArrival, InventoryAlert>.Context ctx, Collector<InventoryAlert> out) throws Exception {
            Integer currentStock = stockLevelState.value();
            if (currentStock == null) currentStock = 0;

            stockLevelState.update(currentStock + arrival.quantity());
            System.out.println(">>> Stock arrived for " + ctx.getCurrentKey() + ": +" + arrival.quantity());

            // If the stock has been reset, we cancel the critical alert timer.
            Long activeTimer = criticalTimerState.value();
            if (activeTimer != null) {
                ctx.timerService().deleteProcessingTimeTimer(activeTimer);
                criticalTimerState.clear();
                System.out.println("Critical alert cancelled for " + ctx.getCurrentKey());
            }
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<InventoryAlert> out) throws Exception {
            // If the timer goes off, it means the stock level remained at zero.
            out.collect(new InventoryAlert(ctx.getCurrentKey(), "CRITICAL: No replenishment received in " + (REPLENISHMENT_TIMEOUT / 1000) + " seconds!"));
            criticalTimerState.clear();
            System.out.println(">>> Critical alert fired for " + ctx.getCurrentKey());
        }
    }

    // 1. DATA MODELS
    public record Sale(String productId, int quantity) implements Serializable {}
    public record StockArrival(String productId, int quantity) implements Serializable {}
    public record InventoryAlert(String productId, String message) implements Serializable {}
}
