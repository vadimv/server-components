package rsp.compositions.ui;

import rsp.component.ComponentView;
import rsp.compositions.ListView;

import java.util.List;

import static rsp.dsl.Html.*;

public class DefaultListView extends ListView {

    @Override
    public ComponentView<List<String>> componentView() {
        return _ -> lines -> {
            final List<Item> items = lines.stream().map(line -> line.split(",")).map(tokens -> new Item(tokens[0], tokens[1], tokens[2])).toList();
            return div(
                h1(text("Items List")),
                table(
                        thead(
                                tr(
                                        th(text("column1")),
                                        th(text("column2")),
                                        th(text("column3"))
                                )
                        ),
                        tbody(
                                of(items.stream().map(item ->
                                        tr(
                                                td(text(item.column1)),
                                                td(text(item.column2)),
                                                td(text(item.column3))
                                        )
                                ))
                        )
                )
            );
        };
    }

    record Item(String column1, String column2, String column3) {}
}
