package io.github.jlmc.flink.patientadt.infrastructure.mongodb;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import io.github.jlmc.flink.patientadt.model.AdtPatientLastLocation;
import org.apache.flink.connector.mongodb.sink.writer.context.MongoSinkContext;
import org.apache.flink.connector.mongodb.sink.writer.serializer.MongoSerializationSchema;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdtPatientLastLocationMongoSerializationSchema implements MongoSerializationSchema<AdtPatientLastLocation> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdtPatientLastLocationMongoSerializationSchema.class);

    @Override
    public WriteModel<BsonDocument> serialize(AdtPatientLastLocation element, MongoSinkContext context) {
        LOGGER.info("Serializing patient last location to MongoDB. {}", element);

        final String patientKey = element.patientKey();
        final BsonDocument filter = new BsonDocument("_id", new org.bson.BsonString(patientKey));

        /*
        if (!element.isActive()) {
            LOGGER.info("Deleting patient last location from MongoDB due to deactivation event. {}", element);
            return new com.mongodb.client.model.DeleteOneModel<>(filter);
        }
         */

        final var document = PatientLastLocationDocument.from(element)
                .toDocument()
                .toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());

        return new ReplaceOneModel<>(
                filter,
                document,
                new ReplaceOptions().upsert(true)
        );
    }
}
