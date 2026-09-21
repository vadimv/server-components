package rsp.page;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Resources owned by one rendered page, independent of component mounts or WebSocket attachments. */
public final class PageScope implements AutoCloseable {
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean closed;

    /** Registers a resource for reverse-order cleanup when the page ends. */
    public <T extends AutoCloseable> T own(T resource) {
        Objects.requireNonNull(resource, "resource");
        synchronized (this) {
            if (!closed) {
                resources.push(resource);
                return resource;
            }
        }
        closeResource(resource);
        throw new IllegalStateException("Page scope is closed");
    }

    @Override
    public void close() {
        final Deque<AutoCloseable> closing;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            closing = new ArrayDeque<>(resources);
            resources.clear();
        }
        while (!closing.isEmpty()) {
            closeResource(closing.pop());
        }
    }

    private static void closeResource(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception failure) {
            System.getLogger(PageScope.class.getName()).log(System.Logger.Level.WARNING,
                    "Page resource cleanup failed [type=" + failure.getClass().getName() + "]");
        }
    }
}
