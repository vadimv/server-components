package rsp.compositions.agent;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import static rsp.dsl.Html.*;

/**
 * View component for the delegation approval dialog.
 * <p>
 * Renders scope, control mode, reason, and Approve/Deny buttons.
 */
public class DelegationApprovalView extends Component<DelegationApprovalView.ApprovalViewState> {

    public record ApprovalViewState(String scope, String controlMode, String reason) {}

    private Lookup lookup;

    @Override
    public ComponentStateSupplier<ApprovalViewState> initStateSupplier() {
        return (_, context) -> {
            String scope = context.get(DelegationApprovalContract.APPROVAL_SCOPE);
            String controlMode = context.get(DelegationApprovalContract.APPROVAL_CONTROL_MODE);
            String reason = context.get(DelegationApprovalContract.APPROVAL_REASON);
            return new ApprovalViewState(
                    scope != null ? scope : "APP",
                    controlMode != null ? controlMode : "ASSIST",
                    reason != null ? reason : "");
        };
    }

    @Override
    public ComponentSegment<ApprovalViewState> createComponentSegment(
            final QualifiedSessionId sessionId,
            final TreePositionPath componentPath,
            final TreeBuilderFactory treeBuilderFactory,
            final ComponentContext componentContext,
            final CommandsEnqueue commandsEnqueue) {
        Subscriber subscriber = componentContext.get(Subscriber.class);
        if (subscriber == null) {
            subscriber = NoOpSubscriber.INSTANCE;
        }
        this.lookup = new ContextLookup(componentContext, commandsEnqueue, subscriber);
        return super.createComponentSegment(sessionId, componentPath,
                treeBuilderFactory, componentContext, commandsEnqueue);
    }

    @Override
    public ComponentView<ApprovalViewState> componentView() {
        return _ -> state -> div(attr("class", "approval-dialog"),
                div(attr("class", "approval-header"),
                        text("Agent Delegation Request")),
                div(attr("class", "approval-body"),
                        div(attr("class", "approval-field"),
                                span(attr("class", "approval-label"), text("Scope: ")),
                                span(text(state.scope()))),
                        div(attr("class", "approval-field"),
                                span(attr("class", "approval-label"), text("Control Mode: ")),
                                span(text(state.controlMode()))),
                        state.reason() != null && !state.reason().isEmpty()
                                ? div(attr("class", "approval-field"),
                                        span(attr("class", "approval-label"), text("Purpose: ")),
                                        span(text(state.reason())))
                                : div()
                ),
                div(attr("class", "approval-actions"),
                        button(attr("class", "btn btn-approve"),
                                text("Approve"),
                                on("click", ctx ->
                                        lookup.publish(DelegationApprovalContract.USER_DECISION, true))),
                        button(attr("class", "btn btn-deny"),
                                text("Deny"),
                                on("click", ctx ->
                                        lookup.publish(DelegationApprovalContract.USER_DECISION, false)))
                )
        );
    }

    private static final class NoOpSubscriber implements Subscriber {
        static final NoOpSubscriber INSTANCE = new NoOpSubscriber();

        @Override
        public void addWindowEventHandler(String eventType,
                                          java.util.function.Consumer<rsp.page.EventContext> eventHandler,
                                          boolean preventDefault,
                                          rsp.dom.DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }
}
