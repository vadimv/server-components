package rsp.examples.crud.components;

import rsp.Component;
import rsp.examples.crud.entities.Keyed;
import rsp.examples.crud.state.Row;


public interface FieldComponent<S> extends Component<S>, Keyed<String> {

    static Object dataForComponent(Row row, FieldComponent fieldComponent) {
        if ("__key__".equals(fieldComponent.key())) return row.rowKey.toString();

        for (int i = 0; i < row.dataKeys.length; i++) {
            if (row.dataKeys[i].equals(fieldComponent.key())) {
                return row.data[i];
            }
        }
        throw new IllegalStateException("Data for " + fieldComponent + " not found in " + row);
    }

}
