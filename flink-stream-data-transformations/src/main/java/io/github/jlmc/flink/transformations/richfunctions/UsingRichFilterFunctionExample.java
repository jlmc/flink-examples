package io.github.jlmc.flink.transformations.richfunctions;

import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.HashSet;
import java.util.Set;

public class UsingRichFilterFunctionExample {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(2);

        DataStream<String> input = env.fromData(
                // Marvel Heroes
                "IronMan", "CaptainAmerica", "Thor", "Hulk", "BlackWidow", "SpiderMan", "DoctorStrange", "BlackPanther", "ScarletWitch", "Wolverine",
                // DC Heroes
                "Superman", "Batman", "WonderWoman", "Flash", "Aquaman", "Cyborg", "GreenLantern", "Shazam", "Supergirl", "Nightwing"
        );


        DataStream<String> marvelOnly = input.filter(new MarvelRichFilter());

        // 4. Print result
        marvelOnly.print();

        env.execute("Flink RichFilterFunction Lifecycle Example");
    }

    static class MarvelRichFilter extends RichFilterFunction<String> {

        private transient Set<String> marvelHeroes;

        // Called once when the operator instance is created
        @Override
        public void open(Configuration parameters) throws Exception {

            int subtask = getRuntimeContext().getIndexOfThisSubtask();
            int parallelism = getRuntimeContext().getNumberOfParallelSubtasks();

            System.out.println("OPEN called → Subtask: " + subtask +
                    " / Parallelism: " + parallelism);

            // Initialize Marvel hero list (could be DB/Redis in real use)
            marvelHeroes = new HashSet<>();

            marvelHeroes.add("IronMan");
            marvelHeroes.add("CaptainAmerica");
            marvelHeroes.add("Thor");
            marvelHeroes.add("Hulk");
            marvelHeroes.add("BlackWidow");
            marvelHeroes.add("SpiderMan");
            marvelHeroes.add("DoctorStrange");
            marvelHeroes.add("BlackPanther");
            marvelHeroes.add("ScarletWitch");
            marvelHeroes.add("Wolverine");
        }

        // Runs for EVERY record
        @Override
        public boolean filter(String hero) throws Exception {
            int subtask = getRuntimeContext().getIndexOfThisSubtask();
            System.out.println("Processing: " + hero + " on subtask " + subtask);

            return marvelHeroes.contains(hero);
        }

        // Called when the job stops/cancels
        @Override
        public void close() throws Exception {
            System.out.println("CLOSE called for subtask: "
                    + getRuntimeContext().getIndexOfThisSubtask());

            if (marvelHeroes != null) {
                marvelHeroes.clear();
            }
        }
    }
}
