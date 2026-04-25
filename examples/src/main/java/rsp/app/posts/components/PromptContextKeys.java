package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ContextKey;

public final class PromptContextKeys {
    public static final ContextKey.StringKey<PromptService> PROMPT_SERVICE =
            new ContextKey.StringKey<>("prompt.service", PromptService.class);

    public static final ContextKey.StringKey<String> SCOPE_KEY =
            new ContextKey.StringKey<>("prompt.scopeKey", String.class);

    public static final ContextKey.StringKey<String> ACTIVE_CATEGORY =
            new ContextKey.StringKey<>("prompt.activeCategory", String.class);

    private PromptContextKeys() {}
}
