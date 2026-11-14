package com.parag.framework.data;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parag.framework.exceptions.TestDataException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reads JSON from the classpath and maps it to POJOs/records. ${VAR} placeholders are resolved first.
 * ObjectMapper is thread-safe once configured, so one shared instance is used.
 */
public final class JsonDataReader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonDataReader() { }

    /** readList("testdata/customers.json", CustomerTemplate.class) */
    public static <T> List<T> readList(String resource, Class<T> type) {
        try {
            String json = SecretResolver.resolve(readResource(resource));
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IOException e) {
            throw new TestDataException("Cannot parse test data: " + resource, e);
        }
    }

    /** read("testdata/payee.json", Payee.class) */
    public static <T> T read(String resource, Class<T> type) {
        try {
            String json = SecretResolver.resolve(readResource(resource));
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new TestDataException("Cannot parse test data: " + resource, e);
        }
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = JsonDataReader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new TestDataException("Test data not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
