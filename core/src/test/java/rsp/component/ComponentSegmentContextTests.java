package rsp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that {@link ComponentSegment#componentContext()} returns the segment's
 * current context and that {@link ComponentSegment#setComponentContext} updates it.
 * <p>
 * The setter is package-private; these tests live in {@code rsp.component} to access it.
 * Lazy-context behavior at the lookup level is covered by
 * {@link ContextLookupTests.LazyContextTests}.
 */
class ComponentSegmentContextTests {

    private static final TreePositionPath START_DOM_PATH = TreePositionPath.of("1");
    private static final ContextKey.StringKey<String> KEY =
            new ContextKey.StringKey<>("segment.ctx.key", String.class);

    private QualifiedSessionId sessionId;
    private ComponentCompositeKey componentId;
    private ComponentContext initialContext;
    private List<Command> capturedCommands;
    private CommandsEnqueue commandsEnqueue;

    @BeforeEach
    void setUp() {
        sessionId = new QualifiedSessionId("device", "session");
        componentId = new ComponentCompositeKey(sessionId, "testType", TreePositionPath.of("1"));
        initialContext = new ComponentContext().with(KEY, "v1");
        capturedCommands = new ArrayList<>();
        commandsEnqueue = capturedCommands::add;
    }

    private ComponentSegment<String> createSegment(final ComponentContext ctx) {
        final TreeBuilder tb = new TreeBuilder(sessionId, START_DOM_PATH, ctx, commandsEnqueue);
        final ComponentStateSupplier<String> stateSupplier = (key, c) -> "initial";
        final BiFunction<ComponentContext, String, ComponentContext> contextResolver = (c, s) -> c;
        final ComponentView<String> view = stateUpdate -> s -> rc -> {
            rc.openNode(XmlNs.html, "div", false);
            rc.closeNode("div", false);
        };
        return new ComponentSegment<>(
                componentId, stateSupplier, contextResolver, view,
                new NoOpCallbacks(), tb, ctx, commandsEnqueue);
    }

    @Test
    void componentContext_returns_initial_context_after_construction() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        assertSame(initialContext, segment.componentContext());
    }

    @Test
    void setComponentContext_updates_the_field() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        final ComponentContext updated = new ComponentContext().with(KEY, "v2");

        segment.setComponentContext(updated);

        assertSame(updated, segment.componentContext());
        assertEquals("v2", segment.componentContext().get(KEY));
    }

    @Test
    void setComponentContext_rejects_null() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        assertThrows(NullPointerException.class, () -> segment.setComponentContext(null));
    }

    @Test
    void setComponentContext_observable_through_lazy_lookup() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        final Lookup lazy = new ContextLookup(
                segment::componentContext,
                commandsEnqueue,
                new NoOpSubscriber());

        assertEquals("v1", lazy.get(KEY));

        segment.setComponentContext(new ComponentContext().with(KEY, "v2"));
        assertEquals("v2", lazy.get(KEY),
                "a lazy lookup whose supplier is segment::componentContext must follow updates");
    }

    @Test
    void setComponentContext_notifies_context_scope_watchers() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        final Lookup scoped = new ContextLookup(
                segment.contextScope(),
                commandsEnqueue,
                new NoOpSubscriber());
        final AtomicReference<String> observed = new AtomicReference<>();

        scoped.watch(KEY, observed::set);
        segment.setComponentContext(new ComponentContext().with(KEY, "v2"));

        assertEquals("v2", observed.get());
    }

    @Test
    void mirrorContextTo_updates_external_scope_initially_and_on_context_replacement() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        final ContextScope.Controller mirror =
                ContextScope.controller(new ComponentContext().with(KEY, "mirror-old"));
        final AtomicReference<String> observed = new AtomicReference<>();

        mirror.scope().watch(KEY, (_, newValue) -> observed.set(newValue));
        segment.mirrorContextTo(mirror);

        assertEquals("v1", mirror.scope().current().get(KEY));
        assertEquals("v1", observed.get());

        segment.setComponentContext(new ComponentContext().with(KEY, "v2"));

        assertEquals("v2", mirror.scope().current().get(KEY));
        assertEquals("v2", observed.get());
    }

    @Test
    void unmount_does_not_clear_mirrored_external_scope() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        final ContextScope.Controller mirror =
                ContextScope.controller(new ComponentContext().with(KEY, "mirror-old"));
        final AtomicInteger calls = new AtomicInteger();

        mirror.scope().watch(KEY, (_, _) -> calls.incrementAndGet());
        segment.mirrorContextTo(mirror);
        segment.unmount();
        mirror.replace(new ComponentContext().with(KEY, "after-unmount"));

        assertEquals("after-unmount", mirror.scope().current().get(KEY));
        assertEquals(2, calls.get());
    }

    @Test
    void with_on_lazy_lookup_freezes_independent_of_subsequent_setComponentContext() {
        final ComponentSegment<String> segment = createSegment(initialContext);
        final Lookup lazy = new ContextLookup(
                segment::componentContext,
                commandsEnqueue,
                new NoOpSubscriber());

        final ContextKey.StringKey<String> KEY2 =
                new ContextKey.StringKey<>("segment.ctx.key2", String.class);
        final Lookup derived = lazy.with(KEY2, "added");

        segment.setComponentContext(new ComponentContext().with(KEY, "v2"));

        assertEquals("v2", lazy.get(KEY));            // live
        assertEquals("v1", derived.get(KEY));         // frozen at .with() time
        assertEquals("added", derived.get(KEY2));
    }

    private static final class NoOpCallbacks implements ComponentCallbacks<String> {
        @Override public boolean onBeforeUpdated(String s, CommandsEnqueue c) { return true; }
        @Override public void onAfterRendered(String s, Subscriber sub, CommandsEnqueue c, StateUpdate<String> u) {}
        @Override public void onMounted(ComponentCompositeKey id, String s, StateUpdate<String> u) {}
        @Override public void onUpdated(ComponentCompositeKey id, String o, String n, StateUpdate<String> u) {}
        @Override public void onUnmounted(ComponentCompositeKey id, String s) {}
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override public void addWindowEventHandler(String type, java.util.function.Consumer<rsp.page.EventContext> h,
                                                    boolean preventDefault, rsp.dom.DomEventEntry.Modifier mod) {}
        @Override public Lookup.Registration addComponentEventHandler(String type,
                                                                      java.util.function.Consumer<ComponentEventEntry.EventContext> h,
                                                                      boolean preventDefault) {
            return () -> {};
        }
    }
}
