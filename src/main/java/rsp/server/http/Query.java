package rsp.server.http;


import java.util.*;

public record Query(List<Parameter> parameters) {

    public static final Query EMPTY = new Query(List.of());

    public Query(final List<Parameter> parameters) {
        this.parameters = Collections.unmodifiableList(Objects.requireNonNull(parameters));
    }

    public static Query of(final String queryString) {
        Objects.requireNonNull(queryString);
        final String trimmedStr = queryString.trim().replaceFirst("^\\?", "");
        final List<Parameter> params = Arrays.stream(trimmedStr.split("&")).filter(s -> !s.isEmpty()).map(paramString -> {
            final String[] tokens = paramString.split("=");
            return tokens.length > 1 ? new Parameter(tokens[0], tokens[1]) : new Parameter(tokens[0], "");
        }).toList();
        return new Query(params);
    }

    public Optional<String> parameterValue(final String name) {
        for (Parameter param : parameters) {
            if (param.name.equals(name)) {
                return Optional.of(param.value);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        if (parameters.isEmpty()) {
            return "";
        }
        final var paramStrings = parameters.stream().map(parameter -> parameter.name + '=' + parameter.value).toArray(String[]::new);
        return "?" + String.join("&", paramStrings);
    }

    public record Parameter(String name, String value) {
        public Parameter {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }
}
