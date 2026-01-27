package io.github.jlmc.j11;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class CustomSourceConnectorExample {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());

        env.addSource(new SimpleRichSourceFunction(), "Custom Simple Source Function")
                .print();

        env.execute("Custom Source Connector Example");
    }
}
