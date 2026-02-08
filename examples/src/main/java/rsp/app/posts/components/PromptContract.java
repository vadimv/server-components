package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayList;
import java.util.List;

public class PromptContract extends ViewContract {
    private final System.Logger logger = System.getLogger(getClass().getName());

    public record Message(String text, boolean fromUser) {}

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    public static final EventKey.SimpleKey<Message> NEW_MESSAGE =
            new EventKey.SimpleKey<>("prompt.newMessage", Message.class);

    private final List<Message> messages = new ArrayList<>();
    private Runnable serviceUnsubscribe;

    public PromptContract(Lookup lookup) {
        super(lookup);

        PromptService promptService = lookup.get(PromptService.class);

        subscribe(SEND_PROMPT, (eventName, text) -> {
            messages.add(new Message(text, true));
            promptService.sendPrompt(text);
        });

        serviceUnsubscribe = promptService.subscribe(message -> {
            Message msg = new Message(message.text(), message.fromUser());
            messages.add(msg);
            lookup.publish(NEW_MESSAGE, msg);
        });
        logger.log(System.Logger.Level.INFO, () -> "PromptContract created");
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
        return context.with(PromptContextKeys.PROMPT_MESSAGES, List.copyOf(messages));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceUnsubscribe != null) {
            serviceUnsubscribe.run();
            serviceUnsubscribe = null;
        }
        logger.log(System.Logger.Level.INFO, () -> "PromptContract destroyed");
    }
}
