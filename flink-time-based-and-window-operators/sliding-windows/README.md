# Sliding Windows Example

This example demonstrates how to use **Sliding Windows** in Flink to calculate the average temperature per sensor over the last 10 seconds, updated every 5 seconds.

## Scenario
We monitor sensor data arriving from Kafka as JSON. We want a "sliding" view of the last 10 seconds of data for each sensor.

## How to Run

1.  **Start Infrastructure**:
    ```bash
    docker-compose up -d
    ```

2.  **Build the Project**:
    You can build the project directly if you have Maven and JDK 17+ installed:
    ```bash
    mvn clean package
    ```
    Or use the provided script to build it using a Docker container with JDK 11 (ensures compatibility with the Flink image):
    ```bash
    chmod +x build-jdk17.sh
    ./build-jdk17.sh
    ```

3.  **Submit the Job**:
    ```bash
    ./upload-job.sh
    ```

4.  **Send Sample Events**:
    ```bash
    ./submit-events.sh
    ```

5.  **Check Output**:
    Monitor the TaskManager logs or Flink Web UI.
