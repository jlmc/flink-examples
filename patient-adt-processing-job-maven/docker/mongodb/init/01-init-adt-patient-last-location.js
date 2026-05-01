const dbName = process.env.MONGO_INITDB_DATABASE || "patient_adt";
const database = db.getSiblingDB(dbName);
const collectionName = "adt_patient_last_location";

database.createCollection(collectionName, {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["_id", "accountId", "patientId", "createdAt"],
            properties: {
                _id: { bsonType: "string" },
                accountId: { bsonType: "string" },
                patientId: { bsonType: "string" },
                createdAt: { bsonType: "date" },
                updatedAt: { bsonType: "date" }
            }
        }
    },
    validationLevel: "moderate"
});

const collection = database.getCollection(collectionName);

// PK lógica: accountId + "_" + patientId (armazenada em _id)
collection.createIndex(
    { _id: 1 },
    {
        name: "pk_account_patient",
        unique: true
    }
);

// Índices auxiliares para melhorar pesquisas por accountId e patientId
collection.createIndex(
    { accountId: 1 },
    {
        name: "idx_account_id"
    }
);

collection.createIndex(
    { patientId: 1 },
    {
        name: "idx_patient_id"
    }
);

// Índices auxiliares para ordenação e pesquisa temporal
collection.createIndex(
    { createdAt: 1 },
    {
        name: "idx_created_at"
    }
);

collection.createIndex(
    { updatedAt: 1 },
    {
        name: "idx_updated_at"
    }
);

// TTL baseado no campo expirationTimestamp do AdtPatientLastLocation
collection.createIndex(
    { expirationTimestamp: 1 },
    {
        name: "expiration_timestamp_ttl",
        expireAfterSeconds: 0,
        partialFilterExpression: {
            expirationTimestamp: { $type: "date" }
        }
    }
);
