# Flink Local Environment for Development

One common problem in development is not having access to the JobManager Web UI when running jobs locally.  
In this example, we’ll see how to enable the Flink Web UI in a local development environment.

1. Add dependency to your project
    - In your pom.xml, add the flink-runtime-web dependency:
    ```xml
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-runtime-web</artifactId>
            <version>${flink.version}</version>
            <scope>provided</scope>
        </dependency>
    ```
   - ⚠️ Make sure the ${flink.version} matches the version used in your project.

2. Create a StreamExecutionEnvironment with Web UI
    - Instead of using:
    ```java
    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    ```
    - Use the alternative method to create a local environment with Web UI:
    ```java
    // Create a custom configuration
    Configuration conf = new Configuration();

    // Create the local environment with Web UI
    StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);
   ```

3. Accessing the Web UI
   1. Run the application in IntelliJ or via Maven (mvn compile exec:java).
   2. By default, the Web UI will be available at: [http://localhost:8081](http://localhost:8081)
   3. The Web UI allows you to monitor:
        - Active jobs
        - Execution parameters
        - Checkpoints
        - TaskManagers and metrics

4. Recommended configuration for development
    - You can create a dedicated method to generate the Configuration for your local environment, centralizing all important parameters:
    ```java
    import org.apache.flink.configuration.Configuration;
    
    public class FlinkConfigFactory {

        public static Configuration createLocalConfiguration() {
            Configuration conf = new Configuration();

            // Web UI
            conf.setInteger("rest.port", 8081);
            conf.setBoolean("rest.ssl.enabled", false);

            // Parallelism and slots
            conf.setInteger("parallelism.default", 4);
            conf.setInteger("taskmanager.numberOfTaskSlots", 4);

            // Memory
            conf.setString("taskmanager.memory.process.size", "1024m");
            conf.setString("jobmanager.memory.process.size", "1024m");

            // Checkpoints and savepoints
            conf.setString("state.checkpoints.dir", "file:///tmp/flink-checkpoints");
            conf.setString("state.savepoints.dir", "file:///tmp/flink-savepoints");
            conf.setBoolean("state.checkpoints.cleanup", true);

            // Restart strategy
            conf.setInteger("restart-strategy.fixed-delay.attempts", 3);
            conf.setLong("restart-strategy.fixed-delay.delay", 1000);

            // Logging
            conf.setString("log4j.configurationFile", "log4j2-text.xml"); // or log4j2-json.xml

            // Metrics
           conf.setBoolean("metrics.enabled", true);

           return conf;
       }
   }
   ```

5. Usage tips

    - Local parallelism: adjust parallelism.default based on your machine.
    - JSON logging: set log4j.configurationFile to log4j2-json.xml for structured logs.
    - Local checkpoints: configure directories under /tmp or a preferred path.
    - Web UI port: can be changed via rest.port.

---

## Expected result

  - Running this setup, you will have:
  - JobManager Web UI running locally
  - Logs and metrics visible in the console and Web UI
  - Easy development and debugging of Flink jobs