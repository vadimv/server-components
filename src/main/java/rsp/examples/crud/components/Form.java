package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Form<T> implements Component<Form.State<T>> {

    private final InputComponent<String, ?>[] fieldsComponents;

    @SafeVarargs
    public Form(InputComponent<String, ?>... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Form.State<T>> useState) {
        return useState.get().row.map(row ->
            div(span("Edit: " + row.rowKey),
                form(on("submit", c -> {
                            // 1. read form fields to a Row
                            final Map<String, String> formValues = Arrays.stream(fieldsComponents)
                                    .map(f -> new Tuple2<>(f, c.eventObject().apply(f.key())))
                                    .filter(t -> t._2.isPresent())
                                    .map(t -> new Tuple2<>(t._1.key(), t._2.get()))
                                    .collect(Collectors.toMap(t -> t._1, t -> t._2));
                            // 2. validate using fieldComponents, if any is invalid update state
                            // 3. if all are valid accept
                            useState.accept(formDataToState(row.clazz, row, formValues));
                            System.out.println("submitted:" + formValues);
                        }),
                        of(Arrays.stream(fieldsComponents).map(component ->
                                div(renderFieldComponent(row, component)))),
                        button(attr("type", "submit"), text("Ok")),
                        button(attr("type", "button"),
                                on("click", ctx -> useState.accept(new State<>(useState.get().clazz, Optional.empty(), Collections.EMPTY_MAP))),
                                text("Cancel")))))

                .orElse(div(span("Create")));
    }

    private Form.State<T> formDataToState(Class entityClass,
                                               Row<String, T> oldRow,
                                               Map<String, String> values) {
        final Object[] newData = new Object[oldRow.data.length];
        for (int i = 0; i < oldRow.data.length;i++) {
            final String newValue = values.get(oldRow.dataKeys[i]);
            newData[i] = newValue != null ? parse(oldRow.dataKeys[i], newValue) : oldRow.data[i];
        }
        return new State<>(entityClass, Optional.of(new Row<>(oldRow.rowKey, entityClass, oldRow.dataKeys, newData)), Collections.EMPTY_MAP);
    }

    private DocumentPartDefinition renderFieldComponent(Row<String, T> row, FieldComponent<String> component) {
        return component.render(useState(() -> FieldComponent.dataForComponent(row, component).toString()));
    }

    private Object parse(String fieldName, String str) {
        return fieldComponent(fieldName).conversionFrom().apply(str);
    }

    private InputComponent fieldComponent(String fieldName) {
        for (InputComponent fc : fieldsComponents) {
            if (fc.key().equals(fieldName)) {
                return fc;
            }
        }
        throw new IllegalStateException("Component not found for field: " + fieldName);
    }

    public static class State<T> {
        public final Class<T> clazz;
        public final Optional<Row<String, T>> row;
        public final Map<String, String> validationErrors;

        public State(Class<T> clazz) {
            this(clazz, Optional.empty(), Collections.EMPTY_MAP);
        }

        public State(Class<T> clazz, Optional<Row<String, T>> row) {
            this(clazz, row, Collections.EMPTY_MAP);
        }

        public State(Class<T> clazz, Optional<Row<String, T>> row, Map<String, String> validationErrors) {
            this.clazz = clazz;
            this.row = row;
            this.validationErrors = validationErrors;
        }
    }
}
