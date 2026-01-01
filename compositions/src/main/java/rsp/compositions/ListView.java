package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.definitions.Component;

import java.util.List;

public abstract class ListView extends Component<List<String>> {
    @Override
    public ComponentStateSupplier<List<String>> initStateSupplier() {
        return (_, context) -> List.of("line1", "line2");
    }

    static final class ListViewState { // the common typed language for state
        public String schema;
        public String data;
    }

}
