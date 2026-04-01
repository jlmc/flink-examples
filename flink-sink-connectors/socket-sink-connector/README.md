# Socket Sink Connector Example

This module demonstrates how to use the Flink Socket Sink Connector to send data to a TCP socket.

## Prerequisites

- **Java 17** or higher
- **Maven 3.x**
- **Netcat (`nc`)** (available on most Unix-like systems, including macOS and Linux)

## How to Run the Example

To run this example locally, follow these steps to set up the input and output sockets.

### 1. Start the Output Socket (Sink)
Open a terminal and start a Netcat server to listen for incoming data from Flink:

```bash
nc -lk 9999
```
This will be where the results processed by Flink are displayed.

### 2. Start the Input Socket (Source)
Open another terminal and start another Netcat server to provide data to Flink:

```bash
nc -lk 9998
```
Any text typed in this terminal will be sent to the Flink job.

### 3. Run the Flink Job
You can run the example directly from your IDE by executing the `main` method in `SocketSinkConnector.java`, or by using Maven from the project root:

```bash
mvn clean install -pl flink-sink-connectors/socket-sink-connector -am
java -jar flink-sink-connectors/socket-sink-connector/target/socket-sink-connector-1.0-SNAPSHOT-shaded.jar
```

### 4. Verify the Results
- Type some text into the **Input Socket** terminal (port 9998).
- Observe the transformed output in the **Output Socket** terminal (port 9999).
- You should see messages prefixed with: `SocketSinkConnector: <your input>`.

### 5. Access the Web UI
Since this job uses `createLocalEnvironmentWithWebUI`, you can monitor its execution by visiting:
[http://localhost:8081](http://localhost:8081)

## Code Overview

The Flink job performs the following steps:
1.  Creates a local environment with Web UI support.
2.  Reads data from a socket at `localhost:9998`.
3.  Maps each input string by prefixing it.
4.  Writes the result to a socket at `localhost:9999`.

```java
public class SocketSinkConnector {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(new Configuration());

        env.socketTextStream("localhost", 9998)
                .map(value -> "SocketSinkConnector: " + value, Types.STRING)
                .writeToSocket("localhost", 9999, new SimpleStringSchema());

        env.execute("SocketSinkConnector Job");
    }
}
```

## Official Documentation
For more details on Flink's connectors, refer to the [official Apache Flink documentation](https://nightlies.apache.org/flink/flink-docs-stable/docs/connectors/datastream/overview/).
