package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ContextKey;

import java.util.List;

public final class PromptContextKeys {
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<PromptService.Message>> PROMPT_MESSAGES =
            new ContextKey.StringKey<>("prompt.messages", (Class<List<PromptService.Message>>) (Class<?>) List.class);

    private PromptContextKeys() {}
}
