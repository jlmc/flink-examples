package io.github.jlmc.flink.j4;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.configuration.TaskManagerOptions;
import org.apache.flink.connector.mongodb.source.MongoSource;
import org.apache.flink.connector.mongodb.source.enumerator.splitter.PartitionStrategy;
import org.apache.flink.connector.mongodb.source.reader.deserializer.MongoDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.bson.BsonDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <a href="https://nightlies.apache.org/flink/flink-docs-master/docs/connectors/datastream/mongodb/">Mongodb connector</a>
 */
public class MongodbConnectorDataSourceJob {

    private static final Logger logger = LoggerFactory.getLogger(MongodbConnectorDataSourceJob.class);

    public static void main(String[] args) throws Exception {
        // 1️⃣ Create the execution environment
        Configuration conf = new Configuration();
        conf.set(RestOptions.PORT, 8082);
        conf.set(TaskManagerOptions.NUM_TASK_SLOTS, 4);

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);


        MongoSource<String> mongoSource = MongoSource.<String>builder()
                .setUri("mongodb://localhost:27017")
                .setDatabase("db")
                .setCollection("flink_source_works")
                .setProjectedFields("_id", "id", "title", "author", "year")
                .setFetchSize(5)
                .setLimit(1024)
                .setNoCursorTimeout(true)
                .setPartitionStrategy(PartitionStrategy.SAMPLE)
                .setDeserializationSchema(new MongoDeserializationSchema<>() {

                    @Override
                    public TypeInformation<String> getProducedType() {
                        return Types.STRING;
                    }

                    @Override
                    public String deserialize(BsonDocument bsonDocument) {
                        return bsonDocument.toJson();
                    }
                }).build();

        env.fromSource(mongoSource, WatermarkStrategy.noWatermarks(), "mongodb-source")
                .uid("mongodb-source")
                .name("MongoDB Source")
                .print()
                .uid("print-sink")
                .name("Print Sink");

        env.execute("Flink MongoDB Data Source Connector Example");
    }
}
