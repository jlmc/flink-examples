# Global Windows Example

This example demonstrates how to use **Global Windows** with a custom trigger.

## Scenario
A Global Window is used to store all elements for a key. We trigger it based on a **CountTrigger** of 5 events.

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
    The result will only be printed once a sensor reaches 5 events.
