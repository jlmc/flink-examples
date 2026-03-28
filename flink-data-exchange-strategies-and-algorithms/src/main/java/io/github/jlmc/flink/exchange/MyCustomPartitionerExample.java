package io.github.jlmc.flink.exchange;

import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class MyCustomPartitionerExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setParallelism(4);

        env.socketTextStream("localhost", 9999)
                .map(String::toUpperCase)
                .partitionCustom(new MyCustomPartitioner<>(), new KeySelector<String, String>() {
                    @Override
                    public String getKey(String s) {
                        System.out.println("KeySelector: " + s);
                        return s;
                    }
                })
                .print();

        env.execute("Custom Partitioner Example");
    }
}
