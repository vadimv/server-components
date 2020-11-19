package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Form<K, T> implements Component<Optional<Row<K, T>>> {

    private final InputComponent<?, ?>[] fieldsComponents;

    public Form(InputComponent<?, ?>... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Optional<Row<K,T>>> useState) {
        return div(span("Edit component:" + useState.get().get().rowKey),
                form(on("submit", c -> {
                           var formValues= Arrays.stream(fieldsComponents).map(f -> new Tuple2<>(f, c.eventObject().apply(f.key())))
                                                           .filter(t -> t._2.isPresent())
                                                           .map(t -> new Tuple2<>(t._1.key(), t._2.get()))
                                                           .collect(Collectors.toMap(t -> t._1, t -> t._2));
                           final Class<?> clazz = useState.get().get().clazz;

                           useState.accept(formDataToState(clazz, useState.get().get(), formValues));
                            // 1. read form fields to a Row
                            // 2. validate using fieldComponents, if any is invalid update state
                            // 3. if all are valid accept
                            System.out.println("submitted:" + formValues);
                        }),
                        of(Arrays.stream(fieldsComponents).map(component ->
                                        div(renderFieldComponent(useState.get().get(), component)))),
                     button(attr("type", "submit"), text("Ok")),
                     button(attr("type", "button"),
                             on("click", ctx -> useState.accept(Optional.empty())),
                             text("Cancel"))))  ;
    }

    private Optional<Row<K,T>> formDataToState(Class entityClass,
                                               Row<K, T> oldRow,
                                               Map<String, String> values) {
        final Object[] newData = new Object[oldRow.data.length];
        for (int i = 0; i < oldRow.data.length;i++) {
            final String newValue = values.get(oldRow.dataKeys[i]);
            newData[i] = newValue != null ? parse(oldRow.dataKeys[i], newValue) : oldRow.data[i];
        }
        return Optional.of(new Row<>(oldRow.rowKey, entityClass, oldRow.dataKeys, newData));
    }

    private DocumentPartDefinition renderFieldComponent(Row<K, T> row, FieldComponent component) {
        return component.render(useState(() -> FieldComponent.dataForComponent(row, component)));
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
}
