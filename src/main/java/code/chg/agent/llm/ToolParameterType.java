package code.chg.agent.llm;

import lombok.Data;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.Collection;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolParameterType
 * @description Tool parameter type definition aligned with the OpenAI schema format.
 */
@Data
public class ToolParameterType {
    private static final ToolParameterType STRING_TYPE = new ToolParameterType(ToolParameterBaseType.STRING);
    private static final ToolParameterType BOOLEAN_TYPE = new ToolParameterType(ToolParameterBaseType.BOOLEAN);
    private static final ToolParameterType INTEGER_TYPE = new ToolParameterType(ToolParameterBaseType.INTEGER);
    private static final ToolParameterType NUMBER_TYPE = new ToolParameterType(ToolParameterBaseType.NUMBER);
    private ToolParameterBaseType type;
    private Map<String, ToolParameterType> properties;
    private ToolParameterType items;

    public ToolParameterType() {
    }

    public ToolParameterType(ToolParameterBaseType type) {
        this.type = type;
    }

    public static Map<String, Object> toJsonSchema(ToolParameterType parameterType, String description) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", parameterType.getType().name());
        if (description != null) {
            schema.put("description", description);
        }
        Map<String, ToolParameterType> properties = parameterType.getProperties();
        if (properties != null && !properties.isEmpty()) {
            Map<String, Object> propertiesSchema = new HashMap<>();
            for (Map.Entry<String, ToolParameterType> entry : properties.entrySet()) {
                propertiesSchema.put(entry.getKey(), toJsonSchema(entry.getValue(), null));
            }
            schema.put("properties", propertiesSchema);
        }
        ToolParameterType items = parameterType.getItems();
        if (items != null) {
            schema.put("items", toJsonSchema(items, null));
        }
        return schema;
    }


    public static ToolParameterType from(Class<?> clazz) {
        return from(clazz, null);
    }

    /**
     * Converts a Java type into a {@link ToolParameterType}.
     *
     * @param clazz       the raw Java class
     * @param genericType generic type metadata used to resolve collection element types
     * @return the corresponding tool parameter type
     */
    public static ToolParameterType from(Class<?> clazz, Type genericType) {
        // Handle primitive and boxed scalar types.
        if (clazz == String.class) {
            return STRING_TYPE;
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return BOOLEAN_TYPE;
        } else if (clazz == Integer.class || clazz == int.class || clazz == Long.class || clazz == long.class) {
            return INTEGER_TYPE;
        } else if (clazz == Double.class || clazz == float.class || clazz == double.class) {
            return NUMBER_TYPE;
        }

        // Handle arrays and collection types.
        if (clazz.isArray()) {
            ToolParameterType arrayType = new ToolParameterType(ToolParameterBaseType.ARRAY);
            arrayType.setItems(from(clazz.getComponentType()));
            return arrayType;
        }

        if (Collection.class.isAssignableFrom(clazz)) {
            ToolParameterType arrayType = new ToolParameterType(ToolParameterBaseType.ARRAY);
            // Attempt to infer the collection element type from generic metadata.
            if (genericType instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) genericType;
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    arrayType.setItems(from((Class<?>) typeArgs[0]));
                }
            } else {
                // Fall back to an array of strings when element type information is unavailable.
                arrayType.setItems(STRING_TYPE);
            }
            return arrayType;
        }

        // Handle nested object types.
        if (isComplexType(clazz)) {
            ToolParameterType objectType = new ToolParameterType(ToolParameterBaseType.OBJECT);
            Map<String, ToolParameterType> properties = new HashMap<>();

            // Reflect over object fields to build a nested schema.
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                // Skip static and transient fields.
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                        java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                Type fieldGenericType = field.getGenericType();
                properties.put(field.getName(), from(fieldType, fieldGenericType));
            }

            if (!properties.isEmpty()) {
                objectType.setProperties(properties);
            }
            return objectType;
        }

        throw new IllegalArgumentException("Unsupported parameter type: " + clazz.getSimpleName());
    }

    /**
     * Determines whether the given class should be modeled as a complex object.
     *
     * @param clazz the class to evaluate
     * @return {@code true} when the class represents a nested object type
     */
    private static boolean isComplexType(Class<?> clazz) {
        // Exclude primitive, collection, and platform types.
        if (clazz.isPrimitive() ||
                clazz == String.class ||
                clazz == Boolean.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Double.class ||
                clazz == Float.class ||
                clazz == Short.class ||
                clazz == Byte.class ||
                clazz == Character.class ||
                clazz.isArray() ||
                Collection.class.isAssignableFrom(clazz) ||
                Map.class.isAssignableFrom(clazz) ||
                clazz == Object.class ||
                clazz.getPackage() == null ||
                clazz.getPackage().getName().startsWith("java.") ||
                clazz.getPackage().getName().startsWith("javax.")) {
            return false;
        }
        return true;
    }

}
