package rsp.server.http;


import java.util.*;

public final class Query {
    public final List<Parameter> parameters;

    public Query(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    public static Query of(final String queryString) {
        if (queryString == null) {
            return new Query(List.of());
        }
        final String trimmedStr = queryString.trim();
        final List<Parameter> params = Arrays.stream(trimmedStr.split("&")).filter(s -> !s.isEmpty()).map(paramString -> {
            final String[] tokens = paramString.split("=");
            return tokens.length > 1 ? new Parameter(tokens[0], tokens[1]) :new Parameter(tokens[0], "");
        }).toList();
        return new Query(params);
    }

    public Optional<String> parameterValue(String name) {
        for ( Parameter param : parameters) {
            if (param.name.equals(name)) {
                return Optional.of(param.value);
            }
        }
        return Optional.empty();
    }

    public record Parameter(String name, String value) {
        public Parameter {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }
}
