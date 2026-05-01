package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AllowAllSpawner;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.contract.ContextKeys;
import rsp.page.QualifiedSessionId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptContractTests {

    @Test
    void enrichContext_uses_primary_category_key_provided_by_sibling_context() {
        final Lookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class, new QualifiedSessionId("device", "session"));
        final PromptContract promptContract = new PromptContract(
                lookup,
                new PromptService(),
                new AgentService(),
                new ActionDispatcher(),
                new Authorization(_ -> new AccessDecision.Allow(), Attributes.empty()),
                new AllowAllSpawner(),
                null);

        final ComponentContext enriched = promptContract.enrichContext(
                new ComponentContext().with(ContextKeys.PRIMARY_CATEGORY_KEY, "Posts"));

        assertEquals("Posts", enriched.get(PromptContextKeys.ACTIVE_CATEGORY));
    }
}
