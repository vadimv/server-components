package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.app.posts.services.PromptService;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.ContextLookup;
import rsp.component.ContextScope;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AllowAllSpawner;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.contract.ContextKeys;
import rsp.component.Subscriber;
import rsp.page.QualifiedSessionId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptContractTests {

    @Test
    void enrichContext_uses_primary_category_key_provided_by_sibling_context() {
        final CommandsEnqueue commands = _ -> {};
        final Subscriber subscriber = new NoOpSubscriber();
        final ComponentContext lookupContext = new ComponentContext()
                .with(QualifiedSessionId.class, new QualifiedSessionId("device", "session"));
        final ContextScope.Controller controller = ContextScope.controller(lookupContext);
        final ContextLookup lookup = new ContextLookup(controller.scope(), commands, subscriber);
        final PromptContract promptContract = new PromptContract(
                lookup,
                new PromptService(),
                new AgentService(),
                new ActionDispatcher(),
                new Authorization(_ -> new AccessDecision.Allow(), Attributes.empty()),
                new AllowAllSpawner(),
                null);

        controller.replace(lookupContext.with(ContextKeys.PRIMARY_CATEGORY_KEY, "Comments"));
        assertEquals("Comments", activeCategory(promptContract));

        final ComponentContext enriched = promptContract.enrichContext(
                new ComponentContext().with(ContextKeys.PRIMARY_CATEGORY_KEY, "Posts"));

        assertEquals("Posts", enriched.get(PromptContextKeys.ACTIVE_CATEGORY));
    }

    private static String activeCategory(PromptContract promptContract) {
        try {
            final java.lang.reflect.Field field = PromptContract.class.getDeclaredField("activeCategory");
            field.setAccessible(true);
            return (String) field.get(promptContract);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType, java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault, rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType,
                                             java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}

        @Override
        public void removeComponentEventHandler(String eventType) {}
    }
}
