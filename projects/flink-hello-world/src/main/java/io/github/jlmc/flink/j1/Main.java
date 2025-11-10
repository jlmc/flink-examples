package io.github.jlmc.flink.j1;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class Main {

    public static void main(String[] args) throws Exception {

// 1️⃣ Create the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //env.setParallelism(2);


        // 2️⃣ Create a simple data stream
        DataStream<String> textStream = env.fromElements(
                "Hello", "from", "Apache", "Flink", "Java 21"
        );

        // 3️⃣ Transform the stream (optional, here we just uppercase everything)
        DataStream<String> upperStream = textStream.map((MapFunction<String, String>) String::toUpperCase);

        // 4️⃣ Print the result to the console
        upperStream.print();

        // 5️⃣ Execute the Flink job
        env.execute("Hello World Flink Job");
    }
}
