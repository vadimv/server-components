package rsp.examples.crud.components;

import rsp.Component;
import rsp.examples.crud.state.Cell;

import java.util.function.Supplier;

public interface FieldComponent extends Component<Cell>, Supplier<String> {

    static Cell cellForComponent(Cell[] cells, FieldComponent fieldComponent) {
        for (Cell cell : cells) {
            if (cell.fieldName.equals(fieldComponent.get())) {
                return cell;
            }
        }
        return new Cell("null", "Field not found");

    }
}
