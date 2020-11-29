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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class Resource<T> implements Component<Resource.State<T>> {

    public final int DEFAULT_PAGE_SIZE = 10;

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
                    Create<T> createComponent) {
        this.name = name;
        this.title = title;
        this.entityService = entityService;
        this.listComponent = listComponent;
        this.editComponent = editComponent;
        this.createComponent = createComponent;
    }

    public CompletableFuture<Resource.State<T>> initialState() {
        return entityService.getList(0, DEFAULT_PAGE_SIZE)
                .thenApply(entities -> new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                                            new HashSet<>()))
                .thenApply(gridState -> new Resource.State<>(Set.of(Resource.ViewType.LIST),
                                                             gridState,
                                                             new DetailsViewState<>()));
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
                                                     entityService.getList(0, DEFAULT_PAGE_SIZE).thenAccept(entities -> {
                                                            us.accept(us.get().withList(new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                                                                               new HashSet<>())));
                                                     });
                                                 });
                                }))),
                when(us.get().view.contains(ViewType.LIST),
                        () -> listComponent.render(useState(() -> us.get().list,
                                                   gridState -> us.accept(us.get().withList(gridState))))),

                when(us.get().view.contains(ViewType.CREATE),
                        () -> createComponent.render(detailsViewState(us))),

                when(us.get().view.contains(ViewType.EDIT) && us.get().edit.isActive,
                        () -> editComponent.render(detailsViewState(us))));
    }

    private UseState<DetailsViewState<T>> detailsViewState(UseState<Resource.State<T>> us) {
        return useState(() -> us.get().edit.withActive(),
                         editState -> {
            if (!editState.validationErrors.isEmpty()) {
                us.accept(us.get().withEdit(editState));
            } else if (editState.currentValue.isPresent() && editState.currentKey.isPresent()) {
                // edit
                entityService.update(new KeyedEntity<>(editState.currentKey.get(), editState.currentValue.get()))
                        .thenCompose(u -> entityService.getList(0, DEFAULT_PAGE_SIZE))
                        .thenAccept(entities ->
                                us.accept(us.get().withList(new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                        new HashSet<>())))).join();

            } else if (editState.currentValue.isPresent()) {
                // create
                entityService.create(editState.currentValue.get())
                        .thenCompose(u -> entityService.getList(0, DEFAULT_PAGE_SIZE))
                        .thenAccept(entities ->
                                us.accept(us.get().withList(new DataGrid.Table<>(entities.toArray(new KeyedEntity[0]),
                                        new HashSet<>())))).join();
            } else {
                us.accept(us.get().withList());
            }
        });
    }

    public enum ViewType {
        LIST, EDIT, CREATE, ERROR
    }

    public static class State<T> {
        public final Set<ViewType> view;
        public final DataGrid.Table list;
        public final DetailsViewState<T> edit;

        public State(Set<ViewType> view,
                     DataGrid.Table list,
                     DetailsViewState<T> edit) {
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

        public State withEdit(DetailsViewState<T> edit) {
            return new State(view, list, edit);
        }

        public State withEditData(KeyedEntity<String, T> data) {
            return new State(Set.of(ViewType.LIST, ViewType.EDIT), list, new DetailsViewState<T>(true, Optional.of(data.data), Optional.of(data.key)));
        }

        public State withCreate() {
            return new State(Set.of(ViewType.LIST, ViewType.CREATE), list, new DetailsViewState(true, Optional.empty(), Optional.empty()));
        }

        public State updateList(DataGrid.Table<?, ?> l) {
            return new State(view, l, edit);
        }

    }

}
