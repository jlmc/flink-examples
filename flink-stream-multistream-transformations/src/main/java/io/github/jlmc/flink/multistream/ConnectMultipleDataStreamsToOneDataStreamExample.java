package io.github.jlmc.flink.multistream;

import org.apache.flink.streaming.api.datastream.ConnectedStreams;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoMapFunction;

/**
 * Multistream Transformations - Connect Multiple DataStreams To One DataStream.
 * This allows two separate data sources to "talk" to the same operator.
 */
public class ConnectMultipleDataStreamsToOneDataStreamExample {

    public static void main(String[] args) throws Exception {
        // 1. Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. Create two separate data sources
        DataStream<Integer> controlStream = env.fromElements(1, 0, 1, 0);
        DataStream<String> dataStream = env.fromElements("apple", "banana", "cherry", "date");

        // 3. Connect the two streams
        // This results in a ConnectedStreams object
        ConnectedStreams<Integer, String> connectedStreams = controlStream.connect(dataStream);

        // 4. Apply a CoMapFunction to the connected streams
        // This allows processing both streams in the same operator
        DataStream<String> resultStream = connectedStreams.map(new CoMapFunction<Integer, String, String>() {
            @Override
            public String map1(Integer control) {
                return "Control: " + (control == 1 ? "ENABLE" : "DISABLE");
            }

            @Override
            public String map2(String value) {
                return "Data: " + value;
            }
        });

        // 5. Print the result
        resultStream.print();

        // 6. Execute the job
        env.execute("Connect DataStreams Example");
    }
}
