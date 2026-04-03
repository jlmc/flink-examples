# Session Windows Example

This example demonstrates how to use **Session Windows** in Flink to calculate the maximum temperature during an activity session for each sensor.

## Scenario
Sessions are determined by a gap in activity (e.g., 5 seconds of inactivity closes the session).

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
    chmod +x build-jdk11.sh
    ./build-jdk11.sh
    ```

3.  **Submit the Job**:
    ```bash
    ./upload-job.sh
    ```

3.  **Send Sample Events**:
    ```bash
    ./submit-events.sh
    ```

4.  **Check Output**:
    Monitor the TaskManager logs or Flink Web UI.
