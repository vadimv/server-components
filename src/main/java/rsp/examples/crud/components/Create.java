package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.useState;

public class Create<T> implements Component<Create.State<T>> {

    public final Form<T> form;
    public final DefaultValue[] defaultValues;

    public Create(Form<T> form, DefaultValue<?>... defaultValues) {
        this.form = form;
        this.defaultValues = defaultValues;
    }

    @Override
    public DocumentPartDefinition render(UseState<Create.State<T>> us) {
        return form.render(useState(() -> us.get().formState));
    }

    public static class State<T> {
        public final Class<T> clazz;
        public final Form.State<T> formState;

        public State(Class<T> clazz) {
            this.clazz = clazz;
            this.formState = new Form.State<>(clazz);
        }
    }
}
