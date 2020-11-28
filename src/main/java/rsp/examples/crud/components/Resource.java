package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.examples.crud.entities.services.EntityService;
import rsp.state.UseState;
import rsp.util.StreamUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Resource<T> implements Component<Resource.State<T>> {
    public final String name;
    public final String title;
    public final EntityService<String, T> entityService;

    private final Component<DataGrid.Table<String, T>> listComponent;
    private final Edit<T> editComponent;
    private final Create<T> createComponent;

    public Resource(String name,
                    String title,
                    EntityService<String, T> entityService,
                    Component<DataGrid.Table<String, T>> listComponent,
                    Edit<T> editComponent,
                    Create<T>  createComponent) {
        this.name = name;
        this.title = title;
        this.entityService = entityService;
        this.listComponent = listComponent;
        this.editComponent = editComponent;
        this.createComponent = createComponent;
    }

    @Override
    public DocumentPartDefinition render(UseState<Resource.State<T>> us) {
        return div(window().on("popstate", ctx -> {
            ctx.eventObject().apply("hash").ifPresent(h ->
                entityService.getOne(h.substring(1)).thenAccept(keo ->
                        us.accept(us.get().withEditData(keo.get()))).join());
                }),
                div(button(attr("type", "button"),
                           text("Create"),
                           on("click", ctx -> {
                               us.accept(us.get().withCreate());
                           })),
                    button(attr("type", "button"),
                            when(us.get().list.selectedRows.size() == 0, () -> attr("disabled")),
                            text("Delete"),
                            on("click", ctx -> {
                                    final Set<KeyedEntity<String, T>> rows = us.get().list.selectedRows;
                                    StreamUtils.sequence(rows.stream().map(r -> entityService.delete(r.key))
                                               .collect(Collectors.toList()))
                                               .thenAccept(l -> {
                                                     entityService.getList(0, 25).thenAccept(entities -> {
                                                            us.accept(us.get().withList(new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                                                                               new HashSet<>())));
                                                     });
                                                 });


                                }))),
                when(us.get().view.contains(ViewType.LIST),
                        () -> listComponent.render(useState(() -> us.get().list,
                                                   gridState -> us.accept(us.get().withList(gridState))))),

                when(us.get().view.contains(ViewType.CREATE),
                        () -> createComponent.render(createUseState(us))),

                when(us.get().view.contains(ViewType.EDIT) && us.get().edit.isActive,
                        () -> editComponent.render(editUseState(us))));
    }

    private UseState<Create.State<T>> createUseState(UseState<Resource.State<T>> us) {
        return useState(() -> new Create.State<T>(true, Optional.empty()),
                v -> v.current.ifPresentOrElse(value ->
                                entityService.create(value)
                                        .thenCompose(u -> entityService.getList(0, 0))
                                        .thenAccept(entities ->
                                                us.accept(us.get().withList(new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                                        new HashSet<>())))).join(),
                        () -> us.accept(us.get().withList())
                ));
    }

    private UseState<Edit.State<T>> editUseState(UseState<Resource.State<T>> us) {
        return useState(() -> us.get().edit.withActive(),
                         v -> v.current.ifPresentOrElse(value -> {
                                     if (v.validationErrors.isEmpty()) {
                                         entityService.update(value)
                                                      .thenCompose(u -> entityService.getList(0, 0))
                                                      .thenAccept(entities ->
                                                                us.accept(us.get().withList(new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                                                        new HashSet<>())))).join();
                                     } else {
                                             us.accept(us.get().withEdit(v));
                                     }},
                        () -> us.accept(us.get().withList())));
    }

    public enum ViewType {
        LIST, EDIT, CREATE, ERROR
    }

    public static class State<T> {
        public final Set<ViewType> view;
        public final DataGrid.Table list;
        public final Edit.State<T> edit;

        public State(Set<ViewType> view,
                     DataGrid.Table list,
                     Edit.State<T> edit) {
            this.view = view;
            this.list = list;
            this.edit = edit;
        }

        public State withList(DataGrid.Table<?, ?> gs) {
            return new State(Set.of(ViewType.LIST), gs, edit);
        }

        public State withList() {
            return new State(Set.of(ViewType.LIST), list, edit);
        }

        public State withEdit(Edit.State<T> edit) {
            return new State(Set.of(ViewType.LIST, ViewType.EDIT), list, edit);
        }

        public State withEditData(KeyedEntity<String, T> data) {
            return new State(Set.of(ViewType.LIST, ViewType.EDIT), list, new Edit.State<T>(true, Optional.of(data)));
        }

        public State withCreate() {
            return new State(Set.of(ViewType.LIST, ViewType.CREATE), list, new Edit.State(true, Optional.empty()));
        }

        public State updateList(DataGrid.Table<?, ?> l) {
            return new State(view, l, edit);
        }

    }

}
