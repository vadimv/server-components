package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.useState;

public class Create<T> implements Component<Create.State> {

    public final Form<T> form;
    public final DefaultValue[] defaultValues;

    public Create(Form<T> form, DefaultValue<?>... defaultValues) {
        this.form = form;
        this.defaultValues = defaultValues;
    }

    @Override
    public DocumentPartDefinition render(UseState<State> us) {
        return form.render(useState(() -> null));
    }

    public static class State<S> {
        public final Form.State<S> formState;

        public State(Form.State<S> formState) {
            this.formState = formState;
        }
    }
}
