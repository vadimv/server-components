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
            div(span("Edit: " + row.key),
                form(on("submit", c -> {
                            // 1. read form fields to a Row
                            final Map<String, String> formValues = Arrays.stream(fieldsComponents)
                                    .map(f -> new Tuple2<>(f, c.eventObject().apply(f.key())))
                                    .filter(t -> t._2.isPresent())
                                    .map(t -> new Tuple2<>(t._1.key(), t._2.get()))
                                    .collect(Collectors.toMap(t -> t._1, t -> t._2));
                            // 2. validate using fieldComponents, if any is invalid update state
                            // 3. if all are valid accept
                            useState.accept(new Form.State<T>(Optional.of(newEntity(row, formValues)), Collections.EMPTY_MAP));
                            System.out.println("submitted:" + formValues);
                        }),
                        of(Arrays.stream(fieldsComponents).map(component ->
                                div(component.render(useState(() -> FieldComponent.dataForComponent(row, component).get().toString()))))),
                        button(attr("type", "submit"), text("Ok")),
                        button(attr("type", "button"),
                                on("click", ctx -> useState.accept(new State<>(Optional.empty(), Collections.EMPTY_MAP))),
                                text("Cancel")))))

                .orElse(div(span("Create")));
    }

    private KeyedEntity<String, T> newEntity(KeyedEntity<String, T> oldRow,
                                             Map<String, String> values) {
        final Objenesis objenesis = new ObjenesisStd();
        final Class<T> clazz = (Class<T>) oldRow.data.getClass();
        final T obj = (T) objenesis.newInstance(clazz);
        try {
            for (String fieldName : oldRow.dataFieldsNames()) {
                final Field declaredField = clazz.getDeclaredField(fieldName);
                declaredField.setAccessible(true);
                final String newFieldValue = values.get(fieldName);
                declaredField.set(obj, newFieldValue != null ? fieldComponentOf(fieldName).conversionFrom().apply(newFieldValue) : oldRow.field(fieldName).get());
                declaredField.setAccessible(false);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return new KeyedEntity<>(oldRow.key, obj);
    }

    private InputComponent fieldComponentOf(String fieldName) {
        for (InputComponent fc : fieldsComponents) {
            if (fc.key().equals(fieldName)) {
                return fc;
            }
        }
        throw new IllegalStateException("Component not found for field: " + fieldName);
    }

    public static class State<T> {
        public final Optional<KeyedEntity<String, T>> row;
        public final Map<String, String> validationErrors;

        public State() {
            this(Optional.empty(), Collections.EMPTY_MAP);
        }

        public State(Optional<KeyedEntity<String, T>> row) {
            this(row, Collections.EMPTY_MAP);
        }

        public State(Optional<KeyedEntity<String, T>> row, Map<String, String> validationErrors) {
            this.row = row;
            this.validationErrors = validationErrors;
        }
    }
}
