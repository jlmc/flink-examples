package io.github.jlmc.flink.exchange;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class DataExchangeStrategiesExample {
    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.fromElements("Hello", "Flink", "Exchange", "Strategies")
           .print();

        env.execute("Data Exchange Strategies and Algorithms Example");
    }
}
