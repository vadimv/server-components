package rsp.examples.crud.components;

import rsp.Component;
import rsp.examples.crud.entities.Keyed;
import rsp.examples.crud.state.Cell;
import rsp.examples.crud.state.Row;


public interface FieldComponent<S> extends Component<S>, Keyed<String> {

    static Cell cellForComponent(Row row, FieldComponent fieldComponent) {
        if ("__key__".equals(fieldComponent.key())) return new Cell(row.key.toString(), row.key.toString());

        for (Cell cell : row.cells) {
            if (cell.fieldName.equals(fieldComponent.key())) {
                return cell;
            }
        }

        return new Cell("null", "Field not found");

    }

}
