package rsp.examples.crud.components;

import rsp.component.View;
import rsp.component.definitions.InitialStateComponent;
import rsp.compositions.ListViewContract;
import rsp.dsl.Html;
import rsp.examples.crud.entities.Post;

public class SimpleListView extends InitialStateComponent<Object> {

    public SimpleListView(ListViewContract<Post> contract) {
        super(new Object(), (View<Object>) state -> Html.div(
            Html.h1(Html.text("Posts List")),
            Html.table(
                Html.thead(
                    Html.tr(
                        Html.th(Html.text("ID")),
                        Html.th(Html.text("Title")),
                        Html.th(Html.text("Content"))
                    )
                ),
                Html.tbody(
                    Html.of(contract.items().stream().map(post -> 
                        Html.tr(
                            Html.td(Html.text(post.id())),
                            Html.td(Html.text(post.title())),
                            Html.td(Html.text(post.content()))
                        )
                    ))
                )
            )
        ));
    }
}
