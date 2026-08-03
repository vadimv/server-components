package rsp.compositions.agentui;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.EventKey;
import rsp.component.StateUpdater;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.AgentFeedback;
import rsp.compositions.agent.AgentRuntime;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AgentSpawner;
import rsp.compositions.agent.LoopPolicy;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.Scene;
import rsp.page.QualifiedSessionId;

import java.util.Objects;

/**
 * Thin IO/UI shell over {@link AgentRuntime}: wires the chat surface
 * ({@link PromptService}) to the runtime and forwards lifecycle events.
 * <p>
 * All orchestration (LLM invocation, authorization, dispatch, plan execution)
 * lives in the runtime. This contract owns:
 *  <ul>
 *    <li>chat IO: SEND_PROMPT and PromptService update wiring</li>
 *    <li>scope key derivation from {@link QualifiedSessionId}</li>
 *    <li>active-category state updates from the mounted context watch</li>
 *    <li>scene push to the runtime through a mounted context watch</li>
 *    <li>delegation-approval forwarding to the runtime</li>
 *  </ul>
 */
public class PromptContract extends ContractNodeComponent<PromptView.PromptViewState, PromptView.PromptIntent> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    public record Message(long id, String text, boolean fromUser) {}

    public static final EventKey.SimpleKey<String> SEND_PROMPT =
            new EventKey.SimpleKey<>("prompt.send", String.class);

    private Runnable serviceUnsubscribe;
    private String scopeKey;
    private final PromptService promptService;
    private AgentRuntime runtime;
    private volatile String activeCategory = "";

    public PromptContract(PromptService promptService,
                          AgentService agentService, ActionDispatcher dispatcher,
                          Authorization authorization, AgentSpawner spawner,
                          StructureNode structure) {
        this.promptService = Objects.requireNonNull(promptService);
        this.agentService = Objects.requireNonNull(agentService);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.authorization = Objects.requireNonNull(authorization);
        this.spawner = Objects.requireNonNull(spawner);
        this.structure = Objects.requireNonNull(structure);
    }

    private final AgentService agentService;
    private final ActionDispatcher dispatcher;
    private final Authorization authorization;
    private final AgentSpawner spawner;
    private final StructureNode structure;

    @Override
    public ComponentStateSupplier<PromptView.PromptViewState> initStateSupplier() {
        return (_, context) -> {
            QualifiedSessionId sessionId = context.get(QualifiedSessionId.class);
            String key = sessionId != null ? sessionId.sessionId() : "unknown-session";
            String category = normalizeCategory(context.get(rsp.compositions.contract.ContextKeys.PRIMARY_CATEGORY_KEY));
            return new PromptView.PromptViewState(promptService.getMessageHistory(key).stream()
                    .map(message -> new Message(message.id(), message.text(), message.fromUser()))
                    .toList(), category);
        };
    }

    @Override
    public ComponentView<PromptView.PromptViewState, PromptView.PromptIntent> componentView() {
        return new PromptView();
    }

    @Override
    protected void onContractMounted(PromptView.PromptViewState state,
                                     StateUpdater<PromptView.PromptViewState> stateUpdate) {
        QualifiedSessionId sessionId = lookup().get(QualifiedSessionId.class);
        scopeKey = sessionId != null ? sessionId.sessionId() : "unknown-session";
        activeCategory = state.activeCategory();

        AgentFeedback feedback = new AgentFeedback() {
            @Override public void send(String message) {
                promptService.sendReply(scopeKey, message);
            }
            @Override public void updateLast(String message) {
                promptService.updateLastReply(scopeKey, message);
            }
        };

        this.runtime = new AgentRuntime(agentService, dispatcher, spawner,
                authorization, structure, lookup(), feedback,
                DelegationApprovalContract.class, LoopPolicy.DEFAULT, scopeKey);

        subscribe(SEND_PROMPT, (_, text) -> submit(text, stateUpdate));

        subscribe(DelegationApprovalContract.APPROVAL_DECIDED, (eventName, approved) ->
                runtime.onApprovalDecided(approved));

        subscribe(EventKeys.PRIMARY_CONTRACT_MOUNTED, (_, mounted) ->
                runtime.onPrimaryContractMounted(mounted));

        watch(rsp.compositions.contract.ContextKeys.PRIMARY_CATEGORY_KEY, category -> {
            activeCategory = normalizeCategory(category);
            stateUpdate.applyStateTransformation(current -> current.withActiveCategory(activeCategory));
        });
        watch(rsp.compositions.contract.ContextKeys.SCENE, (_, scene) -> runtime.onScene(scene));
        runtime.onScene(lookup().get(rsp.compositions.contract.ContextKeys.SCENE));

        serviceUnsubscribe = promptService.subscribe(scopeKey, message -> {
            Message msg = new Message(message.id(), message.text(), message.fromUser());
            logger.log(System.Logger.Level.DEBUG,
                () -> String.format("PromptContract@%x bridge: PromptService -> lookup@%x [%s id=%d fromUser=%s text='%s' scope=%s]",
                                    System.identityHashCode(this), System.identityHashCode(lookup()),
                                    message.update() ? "UPDATE" : "NEW",
                                    msg.id(), msg.fromUser(), abbreviate(msg.text()), scopeKey));
            if (message.update()) {
                stateUpdate.applyStateTransformation(current -> current.withLastSystemMessageUpdated(msg.text()));
            } else {
                stateUpdate.applyStateTransformation(current -> current.withMessage(msg));
            }
        });
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x created [scope=%s, lookup@%x]",
                                System.identityHashCode(this), scopeKey, System.identityHashCode(lookup())));
    }

    @Override
    public String title() {
        return "Prompt";
    }

    @Override
    protected void onIntent(PromptView.PromptIntent intent,
                            PromptView.PromptViewState state,
                            StateUpdater<PromptView.PromptViewState> stateUpdate) {
        lookup().publish(SEND_PROMPT, intent.text());
    }

    private static String normalizeCategory(String category) {
        return category != null ? category : "";
    }

    @Override
    public void onUnmounted(rsp.component.ComponentCompositeKey componentId, PromptView.PromptViewState state) {
        super.onUnmounted(componentId, state);
        boolean hadBridge = serviceUnsubscribe != null;
        if (serviceUnsubscribe != null) {
            serviceUnsubscribe.run();
            serviceUnsubscribe = null;
        }
        logger.log(System.Logger.Level.DEBUG,
            () -> String.format("PromptContract@%x destroyed [scope=%s, bridgeUnsubscribed=%s]",
                                System.identityHashCode(this), scopeKey, hadBridge));
        runtime = null;
        scopeKey = null;
    }

    private void submit(String text, StateUpdater<PromptView.PromptViewState> stateUpdate) {
        if (text == null || text.isBlank() || runtime == null) {
            return;
        }
        stateUpdate.applyStateTransformation(current -> current.withOptimisticMessage(text));
        promptService.sendPrompt(scopeKey, text);
        runtime.submit(text);
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= 60 ? oneLine : oneLine.substring(0, 57) + "...";
    }
}
