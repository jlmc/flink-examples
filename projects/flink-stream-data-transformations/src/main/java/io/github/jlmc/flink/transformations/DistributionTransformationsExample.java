package io.github.jlmc.flink.transformations;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Examples of Distribution Transformations: Rebalance.
 */
public class DistributionTransformationsExample {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 1. Rebalance: Evenly distributes data in a Round-Robin fashion
        DataStream<Long> numbers = env.fromSequence(1, 20);

        DataStream<Long> balanced = numbers
                .rebalance()
                .map(n -> {
                    System.out.println("Thread ID: " + Thread.currentThread().getId() + " processing: " + n);
                    return n * 2;
                });

        balanced.print("Rebalance");

        env.execute("Distribution Transformations Example");
    }
}
