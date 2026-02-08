# Flink Stream Data Transformations Examples

This project provides examples of various data transformations available in the Flink DataStream API, as described in the [Stream Data Transformations documentation](../../docs/data-streams-api/DataStream-Transformations.md).

## Examples Included

### 1. Basic Transformations
- **File**: `BasicTransformationsExample.java`
- **Description**: Demonstrates stateless operations like `map`, `filter`, and `flatMap`.
- **How to run**: Run the `main` method in `BasicTransformationsExample`.

### 2. KeyedStream Transformations
- **File**: `KeyedStreamTransformationsExample.java`
- **Description**: Demonstrates stateful operations that require a `keyBy` call, such as `reduce` and `sum`.
- **How to run**: Run the `main` method in `KeyedStreamTransformationsExample`.

### 3. Multistream Transformations
- **File**: `MultistreamTransformationsExample.java`
- **Description**: Demonstrates how to combine multiple streams using `union` and `connect`.
- **How to run**: Run the `main` method in `MultistreamTransformationsExample`.

### 4. Distribution Transformations
- **File**: `DistributionTransformationsExample.java`
- **Description**: Demonstrates physical data reorganization using `rebalance`.
- **How to run**: Run the `main` method in `DistributionTransformationsExample`.

### 5. KeyedProcessFunction
- **File**: `KeyedProcessFunctionExample.java`
- **Description**: A low-level operation that provides access to state and timers. This example implements an "Inactivity Alert".
- **How to run**: Run the `main` method in `KeyedProcessFunctionExample`.

## Build

To build the project:

```bash
mvn clean package -pl projects/flink-stream-data-transformations
```
