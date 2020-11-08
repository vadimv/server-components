package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.services.EntityService;
import rsp.examples.crud.state.Row;
import rsp.examples.crud.state.Table;
import rsp.state.UseState;
import rsp.util.StreamUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Resource<T> implements Component<Admin.State> {

    public final String name;
    public final EntityService<String, T> entityService;
    private final Component<Table<String>> listComponent;
    private final Component<Optional<Row<String>>> editComponent;

    public Resource(String name,
                    EntityService<String, T> entityService,
                    Component<Table<String>> listComponent,
                    Component<Optional<Row<String>>> editComponent) {
        this.name = name;
        this.entityService = entityService;
        this.listComponent = listComponent;
        this.editComponent = editComponent;
    }

    @Override
    public DocumentPartDefinition render(UseState<Admin.State> us) {

        return div(window().on("popstate", ctx -> {
            ctx.eventObject().apply("hash").ifPresent(h ->
                entityService.getOne(h.substring(1)).thenAccept(keo ->
                        us.accept(us.get().updateEdit(keo.map(ke -> ke.toRow())))));
                }),
                button(attr("type", "button"),
                        when(us.get().list.selectedRows.size() == 0, () -> attr("disabled")),
                        text("Delete"),
                        on("click", ctx -> {
                                final Set<Row<String>> rows = us.get().list.selectedRows;
                                StreamUtils.sequence(rows.stream().map(r -> entityService.delete(r.key))
                                           .collect(Collectors.toList()))
                                           .thenAccept(l -> {
                                                 entityService.getList(0, 25).thenAccept(entities -> {
                                                        us.accept(us.get().updateGridState(new Table<>(entities.stream().map(b -> b.toRow()).toArray(Row[]::new),
                                                                 new HashSet<>())));
                                                 });
                                             });


                            })),
                when(us.get().view.contains(Admin.Views.LIST),
                        () -> listComponent.render(useState(() -> us.get().list,
                                                   gridState -> us.accept(us.get().updateGridState(gridState))))),
                when(us.get().edit.isPresent(),
                        () -> editComponent.render(useState(() -> us.get().edit)))
        );
    }

}
