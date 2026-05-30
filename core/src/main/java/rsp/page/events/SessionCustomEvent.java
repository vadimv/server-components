package rsp.page.events;

import rsp.dom.NodeId;

public record SessionCustomEvent(NodeId nodeId, CustomEvent customEvent) implements Command {
}
