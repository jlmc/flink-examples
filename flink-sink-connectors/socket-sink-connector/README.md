# Socket Sink Connector

This module provides an example of how to use the Socket Sink in Apache Flink.
The Socket Sink consumes a DataStream and writes its elements to a specified socket.

## Example

```java
DataStream<String> stream = ...;
stream.writeToSocket("localhost", 9999, new SimpleStringSchema());
```

## Running the Example

To run the example, you first need to start a socket listener on the specified port.
You can use `nc` (netcat) for this purpose:

```bash
nc -lk 9999
```

Then, you can run the Flink job.

## Documentation

For more details, refer to the [official Flink Socket Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/overview/#write-to-socket).
