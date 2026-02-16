/**
 * Script to create and populate the 'flink_source_works' collection.
 * * Target Schema (Projected Fields):
 * - _id (ObjectId, default)
 * - id (Number, sequential)
 * - title (String)
 * - author (String)
 * - year (Number)
 */

// --- Configuration ---
const COLLECTION_NAME = "flink_source_works";
const RECORD_COUNT = 2056;
const DB_NAME = "yourDatabaseName"; // <-- !!! CHANGE THIS !!!

// Ensure we are operating in the correct database context
const db = db.getSiblingDB(DB_NAME);
print(`\n--- Starting script for database: ${DB_NAME} ---`);

// 1. Drop the collection if it exists (for a clean run)
print(`Checking if collection '${COLLECTION_NAME}' exists...`);
try {
    const dropResult = db.getCollection(COLLECTION_NAME).drop();
    if (dropResult) {
        print(`Successfully dropped existing collection: ${COLLECTION_NAME}`);
    } else {
        print(`Collection '${COLLECTION_NAME}' did not exist, proceeding...`);
    }
} catch (e) {
    print(`Error dropping collection: ${e.message}`);
}

// 2. Initialize the bulk insert operation
const bulk = db.getCollection(COLLECTION_NAME).initializeUnorderedBulkOp();
let documentsAdded = 0;

print(`Generating and preparing ${RECORD_COUNT} documents for insertion...`);

// 3. Loop to generate the specified number of records
for (let i = 1; i <= RECORD_COUNT; i++) {
    const document = {
        // Use sequential integer 'id' for easier Flink data tracking
        id: i,
        // Simple, predictable string data
        title: `Work Title ${i}`,
        author: `Author ${Math.ceil(i / 10)}`, // Create roughly 200 unique authors
        // Distribute years across a 20-year span
        year: 1980 + (i % 20),
        // Additional field (not projected by Flink) to show how projection works
        details: `Details for record ${i}`
    };

    bulk.insert(document);
    documentsAdded++;
}

// 4. Execute the bulk insert
print(`Executing bulk insert of ${documentsAdded} documents...`);

try {
    const result = bulk.execute();

    if (result.ok === 1) {
        print(`\n✅ Success! ${result.nInserted} documents inserted into '${COLLECTION_NAME}'.`);

        // 5. Verification
        const count = db.getCollection(COLLECTION_NAME).countDocuments({});
        print(`Verification: Collection now contains ${count} records.`);

        if (count !== RECORD_COUNT) {
            print(`⚠️ WARNING: Count mismatch. Expected ${RECORD_COUNT}, found ${count}.`);
        }

        // Show one document as an example
        const sample = db.getCollection(COLLECTION_NAME).findOne({});
        print("\nSample Document:");
        printjson(sample);

    } else {
        print("\n❌ Bulk insert failed.");
        printjson(result);
    }
} catch (e) {
    print(`\n❌ Script execution error: ${e.message}`);
}

print("\n--- Script Finished ---");