package rsp.page.events;

import rsp.dom.TreePositionPath;

public record SessionCustomEvent(TreePositionPath path, CustomEvent customEvent) implements Command {
}
