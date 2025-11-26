package io.github.jlmc.flink.j4;

import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.io.Serializable;
import java.util.List;

public class JavaCollectionSourcesConnectorExampleJob {

    static SingleOutputStreamOperator<String> buildWithFromCollection(StreamExecutionEnvironment env, Configuration configuration) {
        DataStreamSource<String> source = env.fromCollection(List.of("a", "b", "c", "d", "e", "f", "g", "h"));

        return source.rebalance()
                .map(value -> "[%s] => %s".formatted(value, value.toUpperCase()))
                .setParallelism(configuration.parallelism);
    }


    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        buildWithFromCollection(env, new Configuration("duke", 5)).print();

        env.execute("java collection source");
    }

    record Configuration(String type, int parallelism) implements Serializable {}
}
