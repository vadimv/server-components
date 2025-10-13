package rsp.server;

import rsp.page.events.SessionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TestSessonEventsConsumer implements Consumer<SessionEvent> {
    public final List<SessionEvent> list = new ArrayList<>();

    @Override
    public void accept(SessionEvent event) {
        list.add(event);
    }
}
