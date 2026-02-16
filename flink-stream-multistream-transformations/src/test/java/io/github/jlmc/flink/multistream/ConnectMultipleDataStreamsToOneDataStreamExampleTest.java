package io.github.jlmc.flink.multistream;

import org.apache.flink.streaming.api.datastream.ConnectedStreams;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoMapFunction;
import org.apache.flink.test.util.AbstractTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectMultipleDataStreamsToOneDataStreamExampleTest extends AbstractTestBase {

    @Test
    public void testConnectStreams() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        CollectSink.values.clear();

        DataStream<Integer> controlStream = env.fromElements(1, 0);
        DataStream<String> dataStream = env.fromElements("apple", "banana");

        ConnectedStreams<Integer, String> connectedStreams = controlStream.connect(dataStream);

        DataStream<String> resultStream = connectedStreams.map(new CoMapFunction<Integer, String, String>() {
            @Override
            public String map1(Integer control) {
                return "Control: " + control;
            }

            @Override
            public String map2(String value) {
                return "Data: " + value;
            }
        });

        resultStream.addSink(new CollectSink<String>());

        env.execute();

        assertThat(CollectSink.values).containsExactlyInAnyOrder(
                "Control: 1",
                "Control: 0",
                "Data: apple",
                "Data: banana"
        );
    }
}
