package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Cell;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.*;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class EditForm<K, T> implements Component<Optional<Row<K, T>>> {

    private final TextInput[] fieldsComponents;

    public EditForm(TextInput... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Optional<Row<K,T>>> useState) {
        return div(span("Edit component:" + useState.get().get().key),
                form(on("submit", c -> {
                           var values= Arrays.stream(fieldsComponents).map(f -> new Tuple2<>(f, c.eventObject().apply(f.fieldName)))
                                                           .filter(t -> t._2.isPresent())
                                                           .map(t -> new Tuple2<>(t._1.fieldName, t._2.get()))
                                                           .collect(Collectors.toMap(t -> t._1, t -> t._2));
                           final Class clazz = useState.get().get().clazz;

                           useState.accept(formDataToState(clazz, useState.get().get(), values));
                            // 1. read form fields to a Row
                            // 2. validate using fieldComponents, if any is invalid update state
                            // 3. if all are valid accept
                            System.out.println("submited:" + values);
                        }),
                        of(Arrays.stream(fieldsComponents).map(component ->
                                        div(renderFieldComponent(useState.get().get(), component)))),
                     button(attr("type", "submit"), text("Ok")),
                     button(attr("type", "button"),
                             on("click", ctx -> useState.accept(Optional.empty())),
                             text("Cancel"))))  ;
    }

    private Optional<Row<K,T>> formDataToState(Class entityClass,
                                               Row<K, T> previous,
                                               Map<String, String> values) {
        final List<Cell> cells = new ArrayList<>();
        for (Cell cell: previous.cells) {
            final String newValue = values.get(cell.fieldName);
            cells.add(new Cell(cell.fieldName, newValue != null ? parse(cell.fieldName, newValue) : cell.data));
        }
        return Optional.of(new Row<>(previous.key, entityClass, cells.toArray(new Cell[0])));
    }

    private DocumentPartDefinition renderFieldComponent(Row row, FieldComponent component) {
        return component instanceof EditButton ? component.render(useState(() -> new Cell("rowKey", row.key)))
                : component.render(useState(() -> FieldComponent.cellForComponent(row.cells, component)));
    }

    private Object parse(String fieldName, String str) {
        return fieldComponent(fieldName).conversion.apply(str);
    }

    private TextInput fieldComponent(String fieldName) {
        for (TextInput fc : fieldsComponents) {
            if (fc.fieldName.equals(fieldName)) {
                return fc;
            }
        }
        throw new IllegalStateException("Component not found for field: " + fieldName);
    }
}
