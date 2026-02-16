package io.github.jlmc.flink.j4;

import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.jlmc.flink.j4.JavaCollectionSourcesConnectorExampleJob.buildWithFromCollection;

class JavaCollectionSourcesConnectorExampleJobTest {

    @Test
    void withFromCollection() throws Exception {
        StreamExecutionEnvironment environment = StreamExecutionEnvironment.getExecutionEnvironment();

        SingleOutputStreamOperator<String> stream = buildWithFromCollection(
                environment,
                new JavaCollectionSourcesConnectorExampleJob.Configuration("duke", 1)
        );

        List<String> fromCollectionExecution = stream.executeAndCollect("From collection Execution", 100);

        System.out.println(fromCollectionExecution);
    }
}