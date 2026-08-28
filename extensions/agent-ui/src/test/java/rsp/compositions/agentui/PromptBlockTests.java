package rsp.compositions.agentui;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AllowAllSpawner;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.block.ContextKeys;
import rsp.page.QualifiedSessionId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptBlockTests {

    @Test
    void initial_state_reads_prompt_history_and_active_category_from_context() {
        PromptService prompts = new PromptService();
        prompts.sendPrompt("session", "list posts");
        prompts.sendReply("session", "Here are the posts.");
        PromptBlock block = block(prompts);

        PromptView.PromptViewState state = block.initStateSupplier().getState(null,
                new ComponentContext()
                        .with(QualifiedSessionId.class, new QualifiedSessionId("device", "session"))
                        .with(ContextKeys.PRIMARY_CATEGORY_KEY, "Posts"));

        assertEquals("Posts", state.activeCategory());
        assertEquals(List.of("list posts", "Here are the posts."),
                state.messages().stream().map(PromptBlock.Message::text).toList());
    }

    @Test
    void initial_state_is_a_snapshot_of_the_prompt_history() {
        PromptService prompts = new PromptService();
        prompts.sendReply("session", "before");
        PromptBlock block = block(prompts);
        ComponentContext context = new ComponentContext()
                .with(QualifiedSessionId.class, new QualifiedSessionId("device", "session"));

        PromptView.PromptViewState state = block.initStateSupplier().getState(null, context);
        prompts.sendReply("session", "after");

        assertEquals(1, state.messages().size());
    }

    private PromptBlock block(PromptService prompts) {
        return new PromptBlock(prompts, new AgentService(), new ActionDispatcher(),
                new Authorization(_ -> new AccessDecision.Allow(), Attributes.empty()), new AllowAllSpawner(),
                new StructureNode(null, null, List.of(), List.of()));
    }
}
