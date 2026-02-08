package rsp.app.posts.components;

import rsp.component.ContextKey;

import java.util.List;

public final class PromptContextKeys {
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<PromptContract.Message>> PROMPT_MESSAGES =
            new ContextKey.StringKey<>("prompt.messages", (Class<List<PromptContract.Message>>) (Class<?>) List.class);

    private PromptContextKeys() {}
}
