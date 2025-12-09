package rsp.server.http;


import java.util.*;

/**
 * Represents a URL query.
 * @see RelativeUrl
 * @param parameters a list of query parameters
 */
public record Query(List<Parameter> parameters) {

    /**
     * An empty query parameters.
     */
    public static final Query EMPTY = new Query(List.of());

    /**
     * Creates a new instance of a Query given a list of query parameters.
     * @param parameters a list of parameters
     */
    public Query(final List<Parameter> parameters) {
        this.parameters = Collections.unmodifiableList(Objects.requireNonNull(parameters));
    }

    /**
     * Creates a new instance of this class by parsing a query string in the format attribute-value pairs separated by the delimiter.
     * A query string optionally can start with '?'.
     * @param queryString a query string
     * @return a new instance
     */
    public static Query of(final String queryString) {
        Objects.requireNonNull(queryString);
        final String trimmedStr = queryString.trim().replaceFirst("^\\?", "");
        final List<Parameter> params = Arrays.stream(trimmedStr.split("&")).filter(s -> !s.isEmpty()).map(paramString -> {
            final String[] tokens = paramString.split("=");
            return tokens.length > 1 ? new Parameter(tokens[0], tokens[1]) : new Parameter(tokens[0], "");
        }).toList();
        return new Query(params);
    }

    /**
     * Provides a value of a query parameter
     * @param name a parameter's name
     * @return
     */
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

    /**
     * A URL's query parameter attribute-value pair.
     * @param name
     * @param value
     */
    public record Parameter(String name, String value) {
        public Parameter {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }
}
