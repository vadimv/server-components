package rsp.page;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.page.events.Command;

import java.util.Objects;
import java.util.Optional;

/**
 * A mutable collector of a component tree for materializing an HTML page.
 */
public final class PageBuilder extends TreeBuilder {
    public static final TreePositionPath WINDOW_DOM_PATH = TreePositionPath.of("");
    public static final TreePositionPath DOCUMENT_DOM_PATH = TreePositionPath.of("1");

    private final Optional<LiveBootstrap> liveBootstrap;
    private boolean headWasOpened;

    public PageBuilder(final QualifiedSessionId sessionId,
                       final Optional<LiveBootstrap> liveBootstrap,
                       final ComponentContext componentContext,
                       final CommandsEnqueue remotePageMessagesOut) {
        super(sessionId,
              DOCUMENT_DOM_PATH,
              componentContext,
              remotePageMessagesOut);
        this.liveBootstrap = Objects.requireNonNull(liveBootstrap, "liveBootstrap");
    }

    @Override
    public void openNode(final XmlNs xmlNs, final String name, final boolean isSelfClosing) {
        if (liveBootstrap.isPresent() && !headWasOpened && xmlNs.equals(XmlNs.html) && name.equals("body")) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            super.openNode(XmlNs.html, "head", false);
            upgradeHeadTag();
            super.closeNode("head", false);
        } else if (xmlNs.equals(XmlNs.html) && name.equals("head")) {
            headWasOpened = true;
        }
        super.openNode(xmlNs, name, isSelfClosing);
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        if (liveBootstrap.isPresent() && headWasOpened && upgrade && name.equals("head")) {
            upgradeHeadTag();
        }
        super.closeNode(name, upgrade);
    }

    private void upgradeHeadTag() {
        LiveBootstrap bootstrap = liveBootstrap.orElseThrow();
        super.openNode(XmlNs.html, "script", false);
        super.addTextNode(bootstrap.pageConfigScript());
        super.closeNode("script", false);

        super.openNode(XmlNs.html, "script", false);
        super.setAttr(XmlNs.html, "src", bootstrap.clientScriptPath(), false);
        super.setAttr(XmlNs.html, "defer", "defer", true);
        super.closeNode("script", true);
    }

    @Override
    public TreeBuilder createTreeBuilder(final TreePositionPath baseDomPath) {
        return DOCUMENT_DOM_PATH.equals(baseDomPath) ? new PageBuilder(sessionId,
                                                                       liveBootstrap,
                                                                       componentContext,
                                                                       remotePageMessagesOut)
                                                             : super.createTreeBuilder(baseDomPath);
    }

    /** UI bootstrap data supplied by an adapter for live rendering. */
    public record LiveBootstrap(String pageConfigScript, String clientScriptPath) {
        public LiveBootstrap {
            Objects.requireNonNull(pageConfigScript, "pageConfigScript");
            Objects.requireNonNull(clientScriptPath, "clientScriptPath");
        }
    }
}
