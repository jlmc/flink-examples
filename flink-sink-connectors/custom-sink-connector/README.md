# Flink Custom HTTP Sink Connector Example

This example demonstrates how to implement a **real-world custom Sink** in Apache Flink using the modern `Sink` API (SinkV2). 
It implements an `HttpSink` that sends JSON data to a REST API.

## Project Structure

- `CustomSinkConnectorExample.java`: Defines the Flink Job and the `HttpSink` implementation.
- `HttpSink`: Implementation of the `Sink<Patient>` interface that creates a writer.
- `HttpSinkWriter`: Implementation of the `SinkWriter<Patient>` interface that sends each element as a POST request to a REST endpoint.
- `Patient`: A simple POJO representing the data being processed.
- `docker-compose.yaml`: Provisions a Flink cluster and a **MockServer** to act as the HTTP destination.

## How to Run

### 1. Build the Project

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Start the Environment

```bash
docker-compose up -d
```

### 3. Setup the Mock Server

The MockServer is automatically configured via `mockserver-initializer.json` to respond with `200 OK` to POST requests at `/api/patients`.

- **MockServer UI (Dashboard)**: Accessible at http://localhost:1080/mockserver/dashboard
- **MockServer Logs**: 
  ```bash
  docker-compose logs -f mockserver
  ```

### 4. Deploy the Job

```bash
chmod +x upload-job.sh
./upload-job.sh
```

**Note:** The Job generates messages indefinitely at a rate of 2 per second.

## Verify Results

### Check TaskManager Logs
You can see the Sink sending messages by inspecting the TaskManager logs:

```bash
docker-compose logs -f taskmanager
```

You should see logs like:
`INFO  ... CustomSinkConnectorExample$HttpSinkWriter  - Sending patient to HTTP Sink: Patient{id=0, name='Patient 0'}`

### Check MockServer (UI and Logs)
You can verify the incoming POST requests by:
1. Opening the **MockServer UI** at http://localhost:1080/mockserver/dashboard
2. Checking the **MockServer logs**:
   ```bash
   docker-compose logs -f mockserver
   ```

You should see entries indicating that POST requests were received at `/api/patients`.

## Implementation Notes

- **SinkV2 API**: The recommended way to create output connectors in Flink since version 1.14+.
- **HTTP Client**: Uses Java's built-in `HttpClient` (introduced in Java 11).
- **JSON Serialization**: Uses **Flink JSON** (`JsonSerializationSchema`) to convert POJOs to JSON strings.
- **Error Handling**: The `HttpSinkWriter` checks the HTTP status code and throws an `IOException` if the request fails (e.g., 4xx or 5xx errors), which triggers Flink's fault tolerance mechanisms (retries based on checkpoints).
- **Checkpointing**: Enabled to ensure that if the job fails, it can resume from the last successful state.
