package rsp.examples.crud.components;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Form<T> implements Component<Form.State<T>> {

    private final Consumer<Function<String, Optional<String>>> submittedData;
    private final TextInput[] fieldsComponents;

    @SafeVarargs
    public Form(Consumer<Function<String, Optional<String>>> submittedData, TextInput... fieldsComponents) {
        this.submittedData = submittedData;
        this.fieldsComponents = fieldsComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Form.State<T>> useState) {
        return
            div(form(on("submit", c -> {
                            // 1. read form fields to a Row
                            // 2. validate using fieldComponents, if any is invalid update state
                            // 3. if all are valid accept

                            submittedData.accept(c.eventObject());
                        }),
                        of(Arrays.stream(fieldsComponents).map(component ->
                                div(component.render(useState())))),
                        button(attr("type", "submit"), text("Ok")),
                        button(attr("type", "button"),
                                on("click", ctx -> useState.accept(new State<>(Optional.empty(), Collections.EMPTY_MAP))),
                                text("Cancel"))));
    }


    public static class State<T> {
        public final Optional<T> row;
        public final Map<String, String> validationErrors;

        public State() {
            this(Optional.empty(), Collections.EMPTY_MAP);
        }

        public State(Optional<T> row) {
            this(row, Collections.EMPTY_MAP);
        }

        public State(Optional<T> row, Map<String, String> validationErrors) {
            this.row = row;
            this.validationErrors = validationErrors;
        }
    }
}
