package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.examples.crud.services.EntityService;
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
    private final Optional<Edit<T>> editComponent;
    private final Optional<Create<T>> createComponent;

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
        this.editComponent = Optional.of(editComponent);
        this.createComponent = Optional.of(createComponent);
    }

    public Resource(String name,
                    String title,
                    EntityService<String, T> entityService,
                    Component<DataGrid.Table<String, T>> listComponent,
                    Edit<T> editComponent) {
        this.name = name;
        this.title = title;
        this.entityService = entityService;
        this.listComponent = listComponent;
        this.editComponent = Optional.of(editComponent);
        this.createComponent = Optional.empty();
    }


    public CompletableFuture<Resource.State<T>> initialListState(String name) {
        return entityService.getList(0, DEFAULT_PAGE_SIZE)
                .thenApply(entities -> new DataGrid.Table<String, T>(entities.toArray(new KeyedEntity[0]), new HashSet<>()))
                .thenApply(gridState -> new Resource.State<>(name, gridState, Optional.empty()));
    }

    public CompletableFuture<Resource.State<T>> initialListStateWithEdit(String resourceName, String key) {
            return entityService.getList(0, DEFAULT_PAGE_SIZE)
                .thenApply(entities -> new DataGrid.Table<String, T>(entities.toArray(new KeyedEntity[0]),
                                                                     new HashSet<>()))
                .thenCombine(entityService.getOne(key).thenApply(keo -> new DetailsViewState(keo.map(ke -> ke.data),
                                                                                             keo.map(ke -> ke.key))),
                        (gridState, edit) ->  new Resource.State<>(resourceName, gridState, Optional.of(edit)));
    }



    @Override
    public DocumentPartDefinition render(UseState<Resource.State<T>> us) {
        return div(div(when(createComponent.isPresent(), button(attr("type", "button"),
                                                                text("Create"),
                                                                on("click", ctx -> {
                                                                     us.accept(us.get().withCreate());
                                                                }))),
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
                    listComponent.render(useState(() -> us.get().list,
                                                             gridState -> gridState.editRowKey.ifPresentOrElse(
                                                                     editKey -> entityService.getOne(editKey).thenAccept(keo ->
                                                                             us.accept(us.get().withEditData(keo.get()))).join(),
                                                                                                         () -> us.accept(us.get().withList(gridState))))),

                when(us.get().details.isPresent() && us.get().details.get().visible && !us.get().details.get().currentKey.isPresent(),
                        () -> of(createComponent.map(cc -> cc.render(detailsViewState(us))).stream())),

                when(us.get().details.isPresent() && us.get().details.get().visible && us.get().details.get().currentKey.isPresent(),
                        () -> of(editComponent.map(ec -> ec.render(detailsViewState(us))).stream())));
    }

    private UseState<DetailsViewState<T>> detailsViewState(UseState<Resource.State<T>> us) {
        return useState(() -> us.get().details.get(),
                         editState -> {
            if (!editState.validationErrors.isEmpty()) {
                // show the validation errors
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
                us.accept(us.get().hideDetails());
            }
        });
    }

    public static class State<T> {
        public final String name;
        public final DataGrid.Table<String, T> list;
        public final Optional<DetailsViewState<T>> details; //TODO to Optional<DetailsViewState<T>> , verify DetailsViewState.isActive

        public State(String name,
                     DataGrid.Table<String, T> list,
                     Optional<DetailsViewState<T>> details) {
            this.name = name;
            this.list = list;
            this.details = details;
        }

        public State withList(DataGrid.Table<?, ?> gs) {
            return new State(name, gs, Optional.empty());
        }

        public State withEdit(DetailsViewState<T> edit) {
            return new State(name, list, Optional.of(edit));
        }

        public State hideDetails() {
            return new State(name, list, Optional.empty());
        }

        public State withEditData(KeyedEntity<String, T> data) {
            return new State(name, list, Optional.of(new DetailsViewState<T>(Optional.of(data.data), Optional.of(data.key))));
        }

        public State withCreate() {
            return new State(name, list, Optional.of(new DetailsViewState(Optional.empty(), Optional.empty())));
        }

    }

}
