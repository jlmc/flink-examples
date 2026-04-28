# Count Windows Example

This example demonstrates how to use **Count Windows** in Flink.

## Scenario
Instead of time, we window based on the number of events received. We calculate the average temperature every 3 events per sensor.

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

3.  **Send Sample Events**:
    ```bash
    ./submit-events.sh
    ```

4.  **Check Output**:
    The result will be printed for every 3 events per sensor.
