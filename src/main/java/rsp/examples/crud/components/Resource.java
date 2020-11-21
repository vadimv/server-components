package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.examples.crud.entities.services.EntityService;
import rsp.examples.crud.state.Table;
import rsp.state.UseState;
import rsp.util.StreamUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class Resource<T> implements Component<Resource.State> {

    public final Class<T> clazz;
    public final String name;
    public final EntityService<String, T> entityService;

    private final Component<Table<String, T>> listComponent;
    private final Component<Form.State<T>> editComponent;
    private final Component<Form.State<T>> createComponent;

    public Resource(Class<T> clazz,
                    String name,
                    EntityService<String, T> entityService,
                    Component<Table<String, T>> listComponent,
                    Component<Form.State<T>> editComponent,
                    Component<Form.State<T>> createComponent) {
        this.clazz = clazz;
        this.name = name;
        this.entityService = entityService;
        this.listComponent = listComponent;
        this.editComponent = editComponent;
        this.createComponent = createComponent;
    }

    @Override
    public DocumentPartDefinition render(UseState<Resource.State> us) {
        return div(window().on("popstate", ctx -> {
            ctx.eventObject().apply("hash").ifPresent(h ->
                entityService.getOne(h.substring(1)).thenAccept(keo ->
                        us.accept(us.get().withEdit(clazz, keo.map(ke -> ke)))).join());
                }),
                div(button(attr("type", "button"),
                           text("Create"),
                           on("click", ctx -> {
                               us.accept(us.get().withCreate(clazz));
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
                                                            us.accept(us.get().updateGridState(new Table<>(entities.toArray(new KeyedEntity[0]),
                                                                                               new HashSet<>())));
                                                     });
                                                 });


                                }))),
                when(us.get().view.contains(ViewType.LIST),
                        () -> listComponent.render(useState(() -> us.get().list,
                                                   gridState -> us.accept(us.get().updateGridState(gridState))))),

                when(us.get().view.contains(ViewType.CREATE),
                        () -> createComponent.render(useState(() -> new Form.State<>(clazz)))),

                when(us.get().view.contains(ViewType.EDIT) && us.get().edit.row.isPresent(),
                        () -> editComponent.render(useState(() -> us.get().edit,
                                                            s -> s.row.ifPresentOrElse(r -> {
                            entityService.update(r)
                                         .thenCompose(u -> entityService.getList(0, 0))
                                         .thenAccept(entities ->
                                                 us.accept(us.get().updateList(new Table<>(entities.toArray(new KeyedEntity[0]),
                                                                                           new HashSet<>()))
                                                                   .withEdit(clazz, Optional.empty())));
                                                                },
                                                                    () -> us.accept(us.get().withEdit(clazz, Optional.empty())))))));
    }

    public enum ViewType {
        LIST, EDIT, CREATE, ERROR
    }

    public static class State {
        public final Set<ViewType> view;
        public final Table list;
        public final Form.State edit;

        public State(Set<ViewType> view,
                     Table list,
                     Form.State edit) {
            this.view = view;
            this.list = list;
            this.edit = edit;
        }

        public State updateGridState(Table<?, ?> gs) {
            return new State(view, gs, edit);
        }

        public State withEdit(Class<?> clazz, Optional<KeyedEntity<?, ?>> e) {
            return new State(Set.of(ViewType.LIST, ViewType.EDIT), list, new Form.State(clazz, e));
        }

        public State withCreate(Class<?> clazz) {
            return new State(Set.of(ViewType.LIST, ViewType.CREATE), list, new Form.State(clazz));
        }

        public State updateList(Table<?, ?> l) {
            return new State(view, l, edit);
        }
    }

}
