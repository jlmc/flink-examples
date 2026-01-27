package io.github.jlmc.j11;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class CustomSourceConnectorExample {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());

        //DataStream<Long> customSimpleSourceFunction = simpleRichSourceFunction(env);


        DataStream<Long> longDataStreamSource = simpleRichParallelSourceFunction(env);

        longDataStreamSource.print();

        env.execute("Custom Source Connector Example");
    }

    public static DataStream<Long> simpleRichParallelSourceFunction(StreamExecutionEnvironment env) {
       return env.addSource(new SimpleRichParallelSourceFunction(10, 100), "Custom Simple Parallel Source Function")
                .setParallelism(4)
                .returns(Long.class);
    }

    public static DataStream<Long> simpleRichSourceFunction(StreamExecutionEnvironment env) {
        return env.addSource(new SimpleRichSourceFunction(), "Custom Simple Source Function")
                .returns(Long.class);
    }
}
