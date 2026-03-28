package io.github.jlmc.flink.exchange;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.accumulators.IntCounter;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class CounterAccumulatorExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStreamSource<String> dataStreamSource = env.fromData(
                "apache", "flink", "is", "a", "streaming", "processing", "framework"
        );


        dataStreamSource.map(new RichMapFunction<String, String>() {
            private final IntCounter wordCount = new IntCounter();

            @Override
            public void open(OpenContext openContext) throws Exception {
                super.open(openContext);
                getRuntimeContext().addAccumulator("how_many_words", wordCount);
            }

            @Override
            public String map(String value) throws Exception {
                wordCount.add(1);
                return value.toUpperCase();
            }
        })
        .print();

        JobExecutionResult jobExecutionResult = env.execute();

        Integer wordCount = jobExecutionResult.getAccumulatorResult("how_many_words");
        System.out.println("The job executed finished and totally processed " + wordCount + " words");
    }
}
