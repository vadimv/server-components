package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class Form implements Component<Form.State> {

    private final Consumer<Function<String, Optional<String>>> submittedData;
    private final TextInput[] fieldsComponents;

    @SafeVarargs
    public Form(Consumer<Function<String, Optional<String>>> submittedData, TextInput... fieldsComponents) {
        this.submittedData = submittedData;
        this.fieldsComponents = fieldsComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Form.State> useState) {
        return
            div(form(on("submit", c -> {
                        // Validate all fieldComponents, if any is invalid update state with validation messages, if all valid accept the values
                        final Map<String, String> topValidationErrors =
                                Arrays.stream(fieldsComponents).map(component ->
                                    new Tuple2<>(component.fieldName,
                                                 c.eventObject().apply(component.fieldName).stream().flatMap(value ->
                                                                    Arrays.stream(component.validations()).flatMap(validation ->
                                                                              validation.apply(value).stream())).collect(Collectors.toList())))
                                        .filter(t -> t._2.size() > 0)
                                        .collect(Collectors.toMap(t -> t._1, t -> t._2.get(0)));

                            if (topValidationErrors.size() > 0) {
                                useState.accept(new Form.State(topValidationErrors));
                            } else {
                                submittedData.accept(c.eventObject());
                            }
                        }),
                        of(Arrays.stream(fieldsComponents).map(component ->
                                div(component.render(useState(() -> Optional.ofNullable(useState.get().validationErrors.get(component.fieldName))))))),
                        button(attr("type", "submit"), text("Ok")),
                        button(attr("type", "button"),
                                on("click", ctx -> useState.accept(new State(Collections.EMPTY_MAP))),
                                text("Cancel"))));
    }


    public static class State {
        public final Map<String, String> validationErrors;

        public State(Map<String, String> validationErrors) {
            this.validationErrors = validationErrors;
        }


    /*    public State() {
            validationErrors = Collections.EMPTY_MAP;
        }*/

    }
}
