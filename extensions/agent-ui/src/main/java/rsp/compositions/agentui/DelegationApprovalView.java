package rsp.compositions.agentui;

import rsp.component.ComponentView;
import rsp.component.IntentDispatcher;

import static rsp.dsl.Html.*;

/**
 * View component for the delegation approval dialog.
 * <p>
 * Renders scope, control mode, reason, and Approve/Deny buttons.
 */
public class DelegationApprovalView implements ComponentView<DelegationApprovalView.ApprovalViewState, DelegationApprovalView.Decision> {

    public record ApprovalViewState(String scope, String controlMode, String reason) {}

    public record Decision(boolean approved) {}

    @Override
    public rsp.component.View<ApprovalViewState> resolve(IntentDispatcher<Decision> intents) {
        return state -> div(attr("class", "approval-dialog"),
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
                                on("click", ctx -> intents.dispatch(new Decision(true)))),
                        button(attr("class", "btn btn-deny"),
                                text("Deny"),
                                on("click", ctx -> intents.dispatch(new Decision(false))))
                )
        );
    }

}
