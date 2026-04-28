# Session Windows Example

## Context
- The session windows assigner groups elements by session of activity.
- Session windows do not have a fixed start and end time, instead a session windows closes when it does not receive elements for a certain period of time, i.e., when a gap of inactivity occurred. 
- A session window assigner can be configured with either a static gap of with a session gap extractor which defines how long the period of inactivity is.
- When this period expires, the current session closes and subsequent elements are assigned to a new window.
- The features of session windows are:
    - They are dynamic and can have different sizes.
    - non-overlapping, i.e., an element can only belong to one session.
    - have start and end timestamp but dynamic and determined by the data itself.


## About This Example
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
    Monitor the TaskManager logs or Flink Web UI.
