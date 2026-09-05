package rsp.compositions.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed outcome of a form save or single-entity delete operation.
 *
 * <p>Unlike a boolean result, this preserves validation, not-found, conflict,
 * and operational failures so the form can remain open with useful feedback.</p>
 */
public record FormMutationResult(Status status,
                                 Map<String, List<String>> fieldErrors,
                                 String message,
                                 String entityId) {
    public static final String FORM_ERROR = "_form";

    public enum Status {
        SUCCESS,
        INVALID,
        NOT_FOUND,
        CONFLICT,
        FAILURE
    }

    public FormMutationResult {
        Objects.requireNonNull(status, "status");
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (fieldErrors != null) {
            fieldErrors.forEach((field, errors) -> copied.put(
                    Objects.requireNonNull(field, "field error key"),
                    errors == null ? List.of() : List.copyOf(errors)));
        }
        fieldErrors = Collections.unmodifiableMap(copied);
        message = message == null ? "" : message;
        entityId = entityId == null ? "" : entityId;
        if (status == Status.SUCCESS && !fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("A successful mutation cannot contain field errors");
        }
    }

    public static FormMutationResult saved() {
        return saved("", "");
    }

    public static FormMutationResult saved(String entityId, String message) {
        return new FormMutationResult(Status.SUCCESS, Map.of(), message, entityId);
    }

    public static FormMutationResult invalid(Map<String, List<String>> errors) {
        return invalid(errors, "Please correct the highlighted fields.");
    }

    public static FormMutationResult invalid(Map<String, List<String>> errors, String message) {
        return new FormMutationResult(Status.INVALID, errors, message, "");
    }

    public static FormMutationResult notFound(String message) {
        return new FormMutationResult(Status.NOT_FOUND, Map.of(), message, "");
    }

    public static FormMutationResult conflict(String message) {
        return new FormMutationResult(Status.CONFLICT, Map.of(), message, "");
    }

    public static FormMutationResult failure(String message) {
        return new FormMutationResult(Status.FAILURE, Map.of(), message, "");
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }
}
