package io.github.jlmc.flink.j4;

import org.apache.flink.configuration.*;

public class FlinkConfigFactory {
    public static Configuration createLocalConfiguration() {
        Configuration conf = new Configuration();

        // Web UI
        //conf.set(ConfigOptions.key("rest.port").intType().defaultValue(8081), 8081);
        // REST_PORT_KEY
        conf.set(RestOptions.PORT, 8081);
        conf.set(ConfigOptions.key("rest.ssl.enabled").booleanType().defaultValue(false), false);

        // Parallelism and slots
        conf.set(ConfigOptions.key("parallelism.default").intType().defaultValue(4), 4);
        //conf.set(ConfigOptions.key("taskmanager.numberOfTaskSlots").intType().defaultValue(4), 4);
        conf.set(TaskManagerOptions.NUM_TASK_SLOTS, 4);




        // Memory
        conf.setString("taskmanager.memory.process.size", "1024m");
        conf.setString("jobmanager.memory.process.size", "1024m");

        // Checkpoints and savepoints
        conf.setString("state.checkpoints.dir", "file:///tmp/flink-checkpoints");
        conf.setString("state.savepoints.dir", "file:///tmp/flink-savepoints");
        conf.set(ConfigOptions.key("state.checkpoints.cleanup").booleanType().defaultValue(true), true);


        // Restart strategy
        conf.set(ConfigOptions.key("restart-strategy.fixed-delay.attempts").intType().defaultValue(2), 3);
        conf.set(ConfigOptions.key("restart-strategy.fixed-delay.delay").intType().defaultValue(500), 1000);

        // Logging
        conf.setString("log4j.configurationFile", "log4j2-text.xml"); // or log4j2-json.xml

        // Metrics
        conf.set(ConfigOptions.key("metrics.enabled").booleanType().defaultValue(true), true);

        return conf;
    }
}
