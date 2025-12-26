package rsp.server;

import rsp.page.events.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestSessonEventsConsumer implements Consumer<Command> {
    public final List<Command> list = new ArrayList<>();

    @Override
    public void accept(Command event) {
        list.add(event);
    }
}
