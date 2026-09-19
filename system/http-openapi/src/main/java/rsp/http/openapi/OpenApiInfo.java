package rsp.http.openapi;

import java.util.Objects;
import java.util.Optional;

/** Required identity and optional description of an OpenAPI document. */
public final class OpenApiInfo {
    private final String title;
    private final String version;
    private final Optional<String> description;

    public OpenApiInfo(String title, String version) {
        this(requireText(title, "title"), requireText(version, "version"), Optional.empty());
    }

    private OpenApiInfo(String title, String version, Optional<String> description) {
        this.title = title;
        this.version = version;
        this.description = description;
    }

    public OpenApiInfo description(String value) {
        return new OpenApiInfo(title, version,
                Optional.of(requireText(value, "description")));
    }

    public String title() {
        return title;
    }

    public String version() {
        return version;
    }

    public Optional<String> description() {
        return description;
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
