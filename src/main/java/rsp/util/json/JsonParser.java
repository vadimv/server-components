package rsp.util.json;

public interface JsonParser {
    JsonDataType parse(String jsonString) throws JsonDataType.JsonException;
}
