package io.github.jlmc.flink.patientadt.infrastructure.mongodb;

import io.github.jlmc.flink.patientadt.app.model.AdtPatientLastLocation;
import org.apache.flink.connector.mongodb.sink.MongoSink;

public final class AdtPatientLastLocationMongoSinkFactory {

    private AdtPatientLastLocationMongoSinkFactory() {
    }

    public static MongoSink<AdtPatientLastLocation> build(String mongoUri, String mongoDatabase, String mongoCollection) {
        return MongoSink.<AdtPatientLastLocation>builder()
                .setUri(mongoUri)
                .setDatabase(mongoDatabase)
                .setCollection(mongoCollection)
                .setSerializationSchema(new AdtPatientLastLocationMongoSerializationSchema())
                .build();
    }
}
