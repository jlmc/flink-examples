# Global Windows Example

### Context
- A global windows assigner assigns all elements with the same key to the same window. This means that all events for a given key are grouped together in a single window.
- This windowing scheme is only usefully if you also specify a custom trigger. Otherwise, no computation will be performed, as the global window does not have a natural end at which we could process the aggregated elements.
- The features of Global Windows are:
  - no window boundaries (size), i.e., all elements with the same key belong to the same window.
  - non-overlapping, i.e., an element can only belong to one window.
  - no start and end timestamp, i.e., the window is open until it is triggered
  - no computation is performed until the window is triggered, i.e., the trigger determines when the window is evaluated and results are emitted.



## About 
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
    The result will only be printed once a sensor reaches 5 events.
