package rsp.util.json;

/**
 * Parses JSON text into a {@link JsonDataType} tree. This is the injection seam used across the
 * project; obtain an instance from {@link Json#parser()} or {@link Json#parser(JsonLimits)}.
 */
public interface JsonParser {

    /**
     * Parses a complete JSON document.
     *
     * @param jsonString the JSON text
     * @return the parsed value tree
     * @throws JsonDataType.JsonException if the text is malformed or exceeds a configured limit
     */
    JsonDataType parse(String jsonString) throws JsonDataType.JsonException;
}
