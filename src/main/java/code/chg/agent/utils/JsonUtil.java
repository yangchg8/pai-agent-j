package code.chg.agent.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title JsonUtil
 * @description Jackson utilities for JSON serialization and deserialization
 */
public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Serialize object to JSON string
     *
     * @param object the object to serialize
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serialize object to JSON string without throwing exception
     *
     * @param object the object to serialize
     * @return JSON string representation, or null if serialization fails
     */
    public static String toJsonSafely(Object object) {
        return toJson(object);
    }

    /**
     * Deserialize JSON string to object of specified type
     *
     * @param json  the JSON string
     * @param clazz the target class type
     * @return deserialized object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserialize JSON string to object of specified type without throwing exception
     *
     * @param json  the JSON string
     * @param clazz the target class type
     * @return deserialized object, or null if deserialization fails
     */
    public static <T> T fromJsonSafely(String json, Class<T> clazz) {
        return fromJson(json, clazz);
    }

    /**
     * Get the underlying ObjectMapper instance for advanced operations
     *
     * @return ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}

