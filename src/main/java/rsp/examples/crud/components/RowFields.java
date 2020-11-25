package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Arrays;

import static rsp.dsl.Html.*;

public class RowFields implements Component<Void> {

    private final String key;
    Component<?> fieldComponent;
    private final Component[] fieldComponents;

    public RowFields(String key, Component... fieldComponents) {
        this.key = key;
        this.fieldComponents = fieldComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Void> useState) {

        return of(Arrays.stream(fieldComponents).map(component -> td(
                      component.render(useState()))
                ));
    }


}
