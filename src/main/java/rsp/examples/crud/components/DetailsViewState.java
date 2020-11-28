package rsp.examples.crud.components;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class DetailsViewState<T> {
    public final boolean isActive;
    public final Optional<T> currentValue;
    public final Optional<String> currentKey;
    public final Map<String, String> validationErrors;
    public DetailsViewState(boolean isActive, Optional<T> value, Optional<String> key, Map<String, String> validationErrors) {
        this.isActive = isActive;
        this.currentValue = value;
        this.currentKey = key;
        this.validationErrors = validationErrors;
    }
    public DetailsViewState(boolean isActive, Optional<T> value, Optional<String> key) {
        this(isActive, value, key, Collections.EMPTY_MAP);
    }

    public DetailsViewState() {
        this(false, Optional.empty(), Optional.empty(), Collections.EMPTY_MAP);
    }

    public DetailsViewState<T> withActive() {
        return new DetailsViewState<>(true, currentValue, currentKey, validationErrors);
    }

    public DetailsViewState<T> withValue(T value) {
        return new DetailsViewState<T>(false, Optional.of(value), this.currentKey, this.validationErrors);
    }

    public DetailsViewState<T> withValidationErrors(Map<String, String> validationErrors) {
        return new DetailsViewState<T>(this.isActive, this.currentValue, this.currentKey, validationErrors);
    }
}