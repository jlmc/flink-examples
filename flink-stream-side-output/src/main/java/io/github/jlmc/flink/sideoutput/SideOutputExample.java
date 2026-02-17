package io.github.jlmc.flink.sideoutput;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * 68. DataStream SideOutput - Split DataStream to Multiple DataStreams
 * <p>
 * This example demonstrates how to use Side Outputs to split a single DataStream into multiple streams.
 * Side outputs are a powerful way to emit multiple streams of different types from a single operator.
 */
public class SideOutputExample {

    // Define OutputTags for the side outputs.
    // It's recommended to define them as static final constants to ensure they are the same across the job.
    private static final OutputTag<String> EVEN_NUMBERS_TAG = new OutputTag<>("even-numbers", Types.STRING);
    private static final OutputTag<String> ODD_NUMBERS_TAG = new OutputTag<>("odd-numbers", Types.STRING);

    public static void main(String[] args) throws Exception {
        // 1. Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 2. Create an input DataStream
        DataStream<Integer> input = env.fromElements(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        // 3. Process the stream and emit to side outputs
        SingleOutputStreamOperator<Integer> mainStream = input.process(new ProcessFunction<Integer, Integer>() {
            @Override
            public void processElement(Integer value, Context ctx, Collector<Integer> out) {
                // Emit to the main output
                out.collect(value);

                // Emit to side outputs based on the value
                if (value % 2 == 0) {
                    ctx.output(EVEN_NUMBERS_TAG, "Even: " + value);
                } else {
                    ctx.output(ODD_NUMBERS_TAG, "Odd: " + value);
                }
            }
        });

        // 4. Get the side output streams using the OutputTags
        DataStream<String> evenNumbers = mainStream.getSideOutput(EVEN_NUMBERS_TAG);
        DataStream<String> oddNumbers = mainStream.getSideOutput(ODD_NUMBERS_TAG);

        // 5. Print the results
        mainStream.print("Main Stream");
        evenNumbers.print("Even Side Output");
        oddNumbers.print("Odd Side Output");

        // 6. Execute the job
        env.execute("Flink Side Output Example");
    }
}
