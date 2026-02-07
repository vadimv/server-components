package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

import java.util.List;

public class PromptContract extends ViewContract {

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    public PromptContract(Lookup lookup) {
        super(lookup);

        PromptService promptService = lookup.get(PromptService.class);

        subscribe(SEND_PROMPT, (eventName, text) -> {
            promptService.sendPrompt(text);
        });
    }

    @Override
    public Object typeHint() {
        return PromptContract.class;
    }

    @Override
    public String title() {
        return "Prompt";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context.with(PromptContextKeys.PROMPT_MESSAGES, List.of());
    }
}
