package rsp.app.posts.components;

import rsp.app.posts.services.PromptService;
import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.AgentFeedback;
import rsp.compositions.agent.AgentRuntime;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AgentSpawner;
import rsp.compositions.agent.DelegationApprovalContract;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.Scene;
import rsp.compositions.contract.ViewContract;
import rsp.page.QualifiedSessionId;

import java.util.Objects;

/**
 * Thin IO/UI shell over {@link AgentRuntime}: wires the chat surface
 * ({@link PromptService}) to the runtime and forwards lifecycle events.
 * <p>
 * All orchestration (LLM invocation, authorization, dispatch, plan execution)
 * lives in the runtime. This contract owns:
 *  <ul>
 *    <li>chat IO: SEND_PROMPT / NEW_MESSAGE / UPDATE_MESSAGE wiring</li>
 *    <li>scope key derivation from {@link QualifiedSessionId}</li>
 *    <li>active-category propagation via {@link PromptContextKeys}</li>
 *    <li>scene push to the runtime through {@link #enrichContext}</li>
 *    <li>delegation-approval forwarding to the runtime</li>
 *  </ul>
 */
public class PromptContract extends ViewContract {
    private final System.Logger logger = System.getLogger(getClass().getName());

    public record Message(long id, String text, boolean fromUser) {}

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    public static final EventKey.SimpleKey<Message> NEW_MESSAGE =
            new EventKey.SimpleKey<>("prompt.newMessage", Message.class);

    public static final EventKey.SimpleKey<Message> UPDATE_MESSAGE =
            new EventKey.SimpleKey<>("prompt.updateMessage", Message.class);

    private Runnable serviceUnsubscribe;
    private final String scopeKey;
    private final PromptService promptService;
    private final AgentRuntime runtime;
    private volatile String activeCategory = "";

    public PromptContract(Lookup lookup, PromptService promptService,
                          AgentService agentService, ActionDispatcher dispatcher,
                          Authorization authorization, AgentSpawner spawner,
                          StructureNode structure) {
        super(lookup);
        this.promptService = Objects.requireNonNull(promptService);

        QualifiedSessionId sessionId = lookup.get(QualifiedSessionId.class);
        this.scopeKey = sessionId != null ? sessionId.sessionId() : "unknown-session";
        this.activeCategory = normalizeCategory(lookup.get(ContextKeys.PRIMARY_CATEGORY_KEY));
        watch(ContextKeys.PRIMARY_CATEGORY_KEY, category ->
                this.activeCategory = normalizeCategory(category));

        AgentFeedback feedback = new AgentFeedback() {
            @Override public void send(String message) {
                promptService.sendReply(scopeKey, message);
            }
            @Override public void updateLast(String message) {
                promptService.updateLastReply(scopeKey, message);
            }
        };

        this.runtime = new AgentRuntime(agentService, dispatcher, spawner,
                authorization, structure, lookup, feedback, scopeKey);

        subscribe(SEND_PROMPT, (eventName, text) -> {
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x SEND_PROMPT received text='%s' [scope=%s]",
                                    System.identityHashCode(this), abbreviate(text), scopeKey));
            promptService.sendPrompt(scopeKey, text);
            runtime.submit(text);
        });

        subscribe(DelegationApprovalContract.APPROVAL_DECIDED, (eventName, approved) ->
                runtime.onApprovalDecided(approved));

        serviceUnsubscribe = promptService.subscribe(scopeKey, message -> {
            Message msg = new Message(message.id(), message.text(), message.fromUser());
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x bridge: PromptService -> lookup@%x [%s id=%d fromUser=%s text='%s' scope=%s]",
                                    System.identityHashCode(this), System.identityHashCode(lookup),
                                    message.update() ? "UPDATE" : "NEW",
                                    msg.id(), msg.fromUser(), abbreviate(msg.text()), scopeKey));
            if (message.update()) {
                lookup.publish(UPDATE_MESSAGE, msg);
            } else {
                lookup.publish(NEW_MESSAGE, msg);
            }
        });
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x created [scope=%s, lookup@%x]",
                                System.identityHashCode(this), scopeKey, System.identityHashCode(lookup)));
    }

    @Override
    public String title() {
        return "Prompt";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        Scene scene = context.get(ContextKeys.SCENE);
        runtime.onScene(scene);
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x enrichContext [scope=%s, sceneRouted=%s, activeCategory='%s']",
                                System.identityHashCode(this), scopeKey,
                                scene == null || scene.routedContract() == null
                                    ? "null"
                                    : scene.routedContract().getClass().getSimpleName(),
                                activeCategory));
        this.activeCategory = normalizeCategory(context.get(ContextKeys.PRIMARY_CATEGORY_KEY));
        return context.with(PromptContextKeys.PROMPT_SERVICE, promptService)
                      .with(PromptContextKeys.SCOPE_KEY, scopeKey)
                      .with(PromptContextKeys.ACTIVE_CATEGORY,
                            activeCategory);
    }

    private static String normalizeCategory(String category) {
        return category != null ? category : "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        boolean hadBridge = serviceUnsubscribe != null;
        if (serviceUnsubscribe != null) {
            serviceUnsubscribe.run();
            serviceUnsubscribe = null;
        }
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x destroyed [scope=%s, bridgeUnsubscribed=%s]",
                                System.identityHashCode(this), scopeKey, hadBridge));
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 57) + "...";
    }
}
