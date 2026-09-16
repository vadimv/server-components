package rsp.server;

import rsp.component.CommandsEnqueue;
import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestSessonEventsConsumer implements CommandsEnqueue {
    public final List<Command> list = new ArrayList<>();

    @Override
    public void offer(Command event) {
        list.add(event);
    }
}
