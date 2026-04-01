# Flink MongoDB Sink Connector Example

This module provides an example of using the Flink MongoDB Sink connector to write data to a MongoDB database.

## Prerequisites

- Docker and Docker Compose
- JDK 11 (or use the provided build script which uses Docker)
- Maven

## How to Run

### 1. Build the project

You can build the project using the provided script which uses a Docker container with Maven and JDK 11:

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Start the infrastructure

Start the Flink cluster and MongoDB database using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- `jobmanager` at [http://localhost:8081](http://localhost:8081)
- `taskmanager`
- `mongodb` (accessible at `localhost:27017`)

### 3. Deploy the Flink job

Upload and run the shaded JAR:

```bash
chmod +x upload-job.sh
./upload-job.sh
```

### 4. Verify the data

You can check the data in MongoDB using the `mongosh` inside the container:

```bash
docker exec -it mongodb mongosh flink_db --eval "db.patients.find().limit(10)"
```

### 5. Stop the infrastructure

To stop all services:

```bash
docker-compose down
```

## Example Code

The example uses the `MongoSink` with a custom `MongoSerializationSchema` to perform UPSERT operations using `ReplaceOneModel`.

```java
MongoSink<Patient> mongoSink = MongoSink.<Patient>builder()
        .setUri("mongodb://mongodb:27017")
        .setDatabase("flink_db")
        .setCollection("patients")
        .setSerializationSchema(new MongoSerializationSchema<Patient>() {
            @Override
            public WriteModel<BsonDocument> serialize(Patient patient, MongoSinkContext context) {
                BsonDocument document = new BsonDocument();
                document.append("id", new BsonInt32(patient.id));
                document.append("name", new BsonString(patient.name));
                document.append("age", new BsonInt32(patient.age));

                // UPSERT logic: replace document if ID matches
                BsonDocument filter = new BsonDocument("id", new BsonInt32(patient.id));
                return new ReplaceOneModel<>(filter, document, new ReplaceOptions().upsert(true));
            }
        })
        .build();

env.fromSource(source, WatermarkStrategy.noWatermarks(), "mongodb-data-generator")
   .sinkTo(mongoSink);
```

## Documentation

For more information, see the [Flink MongoDB Sink documentation](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/mongodb/).
