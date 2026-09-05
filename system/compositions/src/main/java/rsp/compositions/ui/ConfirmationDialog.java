package rsp.compositions.ui;

import rsp.component.IntentDispatcher;
import rsp.dsl.Definition;
import rsp.ref.ElementRef;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import static rsp.dsl.Html.*;

/** Reusable native modal dialog for confirming a consequential UI action. */
public final class ConfirmationDialog {
    private ConfirmationDialog() {
    }

    public enum Tone {
        DEFAULT,
        WARNING,
        DANGER
    }

    public record Spec(String title, String message, String confirmLabel, Tone tone) {
        public Spec {
            title = requireText(title, "title");
            message = requireText(message, "message");
            confirmLabel = requireText(confirmLabel, "confirmLabel");
            tone = tone == null ? Tone.DEFAULT : tone;
        }

        public static Spec danger(String title, String message, String confirmLabel) {
            return new Spec(title, message, confirmLabel, Tone.DANGER);
        }

        public static Spec warning(String title, String message, String confirmLabel) {
            return new Spec(title, message, confirmLabel, Tone.WARNING);
        }
    }

    /**
     * Render a closed native dialog. Call {@link rsp.page.EventContext#showModal(ElementRef)}
     * from the invoking button to display it.
     */
    public static <I> Definition render(String stableKey,
                                        ElementRef dialogRef,
                                        Spec spec,
                                        IntentDispatcher<I> intents,
                                        I confirmedIntent) {
        Objects.requireNonNull(dialogRef, "dialogRef");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(intents, "intents");
        Objects.requireNonNull(confirmedIntent, "confirmedIntent");

        String id = "confirmation-" + encodedId(requireText(stableKey, "stableKey"));
        String titleId = id + "-title";
        String messageId = id + "-message";
        String tone = spec.tone().name().toLowerCase(java.util.Locale.ROOT);

        return dialog(
                ref(dialogRef),
                attr("id", id),
                attr("class", "confirmation-dialog confirmation-dialog-" + tone),
                attr("role", "alertdialog"),
                attr("aria-modal", "true"),
                attr("aria-labelledby", titleId),
                attr("aria-describedby", messageId),
                h2(attr("id", titleId), attr("class", "confirmation-dialog-title"), text(spec.title())),
                p(attr("id", messageId), attr("class", "confirmation-dialog-message"), text(spec.message())),
                form(
                        attr("method", "dialog"),
                        attr("class", "confirmation-dialog-actions"),
                        button(
                                attr("type", "submit"),
                                attr("value", "cancel"),
                                attr("class", "confirmation-dialog-cancel"),
                                attr("autofocus", "autofocus"),
                                text("Cancel")),
                        button(
                                attr("type", "submit"),
                                attr("value", "confirm"),
                                attr("class", "confirmation-dialog-confirm confirmation-dialog-confirm-" + tone),
                                text(spec.confirmLabel()),
                                on("click", _ -> intents.dispatch(confirmedIntent)))));
    }

    private static String encodedId(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
