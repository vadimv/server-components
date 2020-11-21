package rsp.examples.crud.components;

import rsp.Component;
import rsp.examples.crud.entities.Keyed;
import rsp.examples.crud.entities.KeyedEntity;

import java.util.Optional;


public interface FieldComponent<S> extends Component<S>, Keyed<String> {
    String KEY_FIELD_NAME = "__key__";

    static Optional<?> dataForComponent(KeyedEntity<?, ?> row, FieldComponent<?> fieldComponent) {
        if (KEY_FIELD_NAME.equals(fieldComponent.key())) return Optional.of(row.key.toString());
        return row.field(fieldComponent.key());
    }

}
