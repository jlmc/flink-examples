package io.github.jlmc.flink.sinks.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.flink.api.common.serialization.Encoder;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A Flink {@link Encoder} that uses Jackson's {@link CsvMapper} to serialize POJOs to CSV.
 * This ensures proper CSV formatting, including escaping, quoting, and column ordering
 * (using {@link com.fasterxml.jackson.annotation.JsonPropertyOrder}).
 *
 * @param <T> The type of the records to encode.
 */
public class JacksonCsvEncoder<T> implements Encoder<T> {

    private final Class<T> type;
    private transient ObjectWriter writer;

    public JacksonCsvEncoder(Class<T> type) {
        this.type = type;
    }

    @Override
    public void encode(T element, OutputStream stream) throws IOException {
        if (writer == null) {
            CsvMapper mapper = new CsvMapper();
            mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            CsvSchema schema = mapper.schemaFor(type).withoutHeader();
            writer = mapper.writer(schema);
        }

        writer.writeValue(stream, element);
    }
}
