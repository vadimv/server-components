package rsp.component;

import rsp.page.events.Command;

public interface CommandsEnqueue {
    void offer(Command command);
}
