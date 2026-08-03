package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.component.TreeBuilder;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;
import rsp.compositions.ui.DefaultEditView;
import rsp.compositions.ui.DefaultListView;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.dsl.Html.div;

class DirectContractHostTests {

    private final CommandsEnqueue commands = _ -> {};
    private final Subscriber subscriber = new NoOpSubscriber();

    @Test
    void group_creates_a_fresh_direct_contract_component() {
        Group group = new Group().bind(TestContract.class, TestContract::new);

        assertTrue(group.hasBinding(TestContract.class));
        assertInstanceOf(TestContract.class, group.resolveComponent(TestContract.class));
        assertInstanceOf(TestContract.class, group.resolveComponent(TestContract.class));
    }

    @Test
    void direct_host_supplies_descriptor_context_without_a_legacy_runtime() {
        TestContract contract = new TestContract();
        ContractDescriptor descriptor = ContractDescriptor.forContract(TestContract.class, Map.of("id", "42"));
        Group group = new Group().bind(TestContract.class, TestContract::new);
        Scene scene = Scene.of(descriptor, Map.of(), new Composition(new Router(), new DefaultLayout(), group));
        TreeBuilder treeBuilder = new TreeBuilder(
                new QualifiedSessionId("device", "session"),
                TreePositionPath.of("1"),
                new ComponentContext()
                        .with(CommandsEnqueue.class, commands)
                        .with(Subscriber.class, subscriber)
                        .with(ContextKeys.SCENE, scene),
                commands);

        new DirectContractHost(descriptor, contract).render(treeBuilder);

        assertEquals("42", contract.showDataId);
        assertEquals(TestContract.class, contract.contractClass);
        assertEquals(Boolean.TRUE, contract.active);
        treeBuilder.shutdown();
    }

    @Test
    void default_crud_views_are_render_and_dispatch_adapters() {
        assertFalse(Component.class.isAssignableFrom(DefaultListView.class));
        assertFalse(Component.class.isAssignableFrom(DefaultEditView.class));
    }

    static final class TestContract extends ContractNodeComponent<String, Object> {
        String showDataId;
        Class<? extends Contract> contractClass;
        Boolean active;

        @Override
        public ComponentStateSupplier<String> initStateSupplier() {
            return (_, _) -> "ready";
        }

        @Override
        public ComponentView<String, Object> componentView() {
            return _ -> _ -> div();
        }

        @Override
        public String title() {
            return "Test";
        }

        @Override
        protected void onContractMounted(String state, rsp.component.StateUpdater<String> stateUpdate) {
            showDataId = lookup().get(ContextKeys.SHOW_DATA).get("id").toString();
            contractClass = lookup().get(ContextKeys.CONTRACT_CLASS);
            active = lookup().get(ContextKeys.IS_ACTIVE_CONTRACT);
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType,
                                          java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault,
                                          rsp.dom.DomEventEntry.Modifier modifier) {
        }

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                             java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                             boolean preventDefault) {
            return () -> {};
        }
    }
}
