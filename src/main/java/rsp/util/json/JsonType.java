package rsp.util.json;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface JsonType {

    class BooleanType implements JsonType {
        private final boolean value;

        public BooleanType(boolean value) {
            this.value = value;
        }

        public boolean value() {
            return value;
        }
    }

    class NumberType implements JsonType {
        private final Number value;

        public NumberType(Number value) {
            this.value = value;
        }

        public Number value() {
            return value;
        }
    }

    class StringType implements JsonType {
        private final String value;

        public StringType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    class ObjectType implements JsonType {
        private final Map<String, ? extends JsonType> values;

        public ObjectType(Map<String, ? extends JsonType> values) {
            this.values = values;
        }

        public Optional<JsonType> value(String name) {
            return Optional.ofNullable(values.get(name));
        }

        public Set<String> keys() {
            return values.keySet();
        }
    }

    class ArrayType implements JsonType {
        private final JsonType[] items;

        public ArrayType(JsonType[] items) {
            this.items = items;
        }

        public JsonType[] items() {
            return items;
        }
    }

    class NullType implements JsonType {
    }
}
