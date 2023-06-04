package rsp.page;

import rsp.component.StatefulComponent;
import rsp.dom.VirtualDomPath;
import rsp.server.In;
import rsp.server.Out;
import rsp.util.data.Either;
import rsp.util.json.JsonDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.DEBUG;

/**
 * A server-side object representing an open browser's page.
 * @param <S> the application's state's type
 */
public final class LivePage<S> implements In, Schedule {
    private static final System.Logger logger = System.getLogger(LivePage.class.getName());

    public final QualifiedSessionId qsid;
    private final StatefulComponent<S> pageRootComponent;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Out out;

    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers = new HashMap<>();
    private final Map<Object, ScheduledFuture<?>> schedules = new HashMap<>();

    public LivePage(final QualifiedSessionId qsid,
                    final StatefulComponent<S> pageRootComponent,
                    final ScheduledExecutorService scheduledExecutorService,
                    final Out out) {
        this.qsid = qsid;
        this.pageRootComponent = pageRootComponent;
        this.scheduledExecutorService = scheduledExecutorService;
        this.out = out;
    }


    public void shutdown() {
        logger.log(DEBUG, () -> "Live Page shutdown: " + this);
        synchronized (this) {
            for (final var timer : schedules.entrySet()) {
                timer.getValue().cancel(true);
            }
        }
    }

    @Override
    public void handleExtractPropertyResponse(final int descriptorId, final Either<Throwable, JsonDataType> result) {

        pageRootComponent.handleExtractPropertyResponse(descriptorId, result);
    }

    @Override
    public void handleEvalJsResponse(final int descriptorId, final JsonDataType value) {
      pageRootComponent.handleEvalJsResponse(descriptorId, value);
    }

    @Override
    public void handleDomEvent(final int renderNumber,
                               final VirtualDomPath path,
                               final String eventType,
                               final JsonDataType.Object eventObject) {
        pageRootComponent.handleDomEvent(renderNumber,
                                         path,
                                         eventType,
                                         eventObject);
    }

    @Override
    public synchronized Timer scheduleAtFixedRate(final Runnable command,
                                                  final Object key,
                                                  final long initialDelay, final long period, final TimeUnit unit) {
        final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(() -> {
            synchronized (this) {
                command.run();
            }
        }, initialDelay, period, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized Timer schedule(final Runnable command, final Object key, final long delay, final TimeUnit unit) {
        final ScheduledFuture<?> timer =  scheduledExecutorService.schedule(() -> {
            synchronized (this) {
                command.run();
            }
        }, delay, unit);
        schedules.put(key, timer);
        return new Timer(key, () -> cancel(key));
    }

    @Override
    public synchronized void cancel(final Object key) {
        final ScheduledFuture<?> schedule = schedules.get(key);
        if (schedule != null) {
            schedule.cancel(true);
            schedules.remove(key);
        }
    }
}