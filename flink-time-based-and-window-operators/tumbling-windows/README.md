# Tumbling Windows Example

This example demonstrates how to use **Tumbling Windows** in Flink to process real-time sensor data from Kafka.

## Scenario
We are monitoring temperature sensors. Each sensor sends its reading as a JSON event. We want to calculate the average temperature per sensor every 10 seconds.

## How to Run

1.  **Start Infrastructure**:
    ```bash
    docker-compose up -d
    ```
    This starts Flink (JobManager and TaskManager), Kafka, and a Kafka-UI (at [http://localhost:8085](http://localhost:8085)).

2.  **Build the Project**:
    You can build the project directly if you have Maven and JDK 17+ installed:
    ```bash
    mvn clean package
    ```
    Or use the provided script to build it using a Docker container with JDK 11 (ensures compatibility with the Flink image):
    ```bash
    chmod +x build-jdk11.sh
    ./build-jdk11.sh
    ```

3.  **Submit the Flink Job**:
    ```bash
    ./upload-job.sh
    ```

4.  **Send Sample Events**:
    ```bash
    ./submit-events.sh
    ```

5.  **Check Output**:
    Monitor the TaskManager logs or Flink Web UI (at [http://localhost:8081](http://localhost:8081)) to see the computed averages.

## JSON Event Format
```json
{
  "id": "sensor-1",
  "timestamp": "2026-04-03T20:12:00.000Z",
  "temperature": 22.5
}
```
