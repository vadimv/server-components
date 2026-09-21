package rsp.http;

import rsp.page.EventLoop;
import rsp.page.LivePageSession;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.page.events.InitSessionCommand;
import rsp.page.events.ShutdownSessionCommand;
import rsp.server.RemoteOut;
import rsp.server.protocol.RemotePageMessageDecoder;
import rsp.server.protocol.RemotePageMessageEncoder;
import rsp.util.json.JsonUtils;
import rsp.websocket.WebSocketProtocolException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static rsp.websocket.WebSocketCloseCodes.NORMAL;
import static rsp.websocket.WebSocketCloseCodes.PROTOCOL_ERROR;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;
import static rsp.util.SafeDiagnostics.failure;

/**
 * A live page whose component state outlives any one WebSocket attachment.
 */
final class ResumablePageSession {
    private static final System.Logger logger = System.getLogger(ResumablePageSession.class.getName());
    private static final int CLOSE_REPLACED = 4000;
    static final int MAX_TRANSPORT_BATCH_MESSAGES = 128;
    static final int MAX_TRANSPORT_BATCH_BYTES = 64 * 1024;

    private final QualifiedSessionId sessionId;
    private final LocalSessionResumeConfig config;
    private final ExpiryScheduler expiryScheduler;
    private final Consumer<ResumablePageSession> onClosed;
    private final LivePageSession livePage;
    private final RemotePageMessageDecoder decoder;
    private final Deque<SequencedFrame> unacknowledgedFrames = new ArrayDeque<>();

    private Attachment attachment;
    private ExpiryTask expiryTask;
    private long expiryGeneration;
    private long attachmentGeneration;
    private long nextSequence = 1;
    private long lastAcknowledgedSequence;
    private long lastDiscardedSequence;
    private long bufferedBytes;
    private boolean closed;

    ResumablePageSession(final QualifiedSessionId sessionId,
                         final RenderedPage renderedPage,
                         final EventLoop eventLoop,
                         final LocalSessionResumeConfig config,
                         final ExpiryScheduler expiryScheduler,
                         final Consumer<ResumablePageSession> onClosed) {
        this.sessionId = Objects.requireNonNull(sessionId);
        Objects.requireNonNull(renderedPage);
        this.config = Objects.requireNonNull(config);
        this.expiryScheduler = Objects.requireNonNull(expiryScheduler);
        this.onClosed = Objects.requireNonNull(onClosed);

        final RemoteOut remoteOut = RemotePageMessageEncoder.batched(this::publish);
        this.livePage = new LivePageSession(Objects.requireNonNull(eventLoop));
        this.decoder = new RemotePageMessageDecoder(JsonUtils.createParser(), livePage.eventsConsumer());
        livePage.eventsConsumer().accept(new InitSessionCommand(renderedPage.pageBuilder(),
                                                                renderedPage.commandsEnqueue(),
                                                                remoteOut,
                                                                renderedPage.scope()));
        remoteOut.setRenderNum(0);
        livePage.start();
        scheduleExpiry();
        logger.log(DEBUG, () -> "Local live page created");
    }

    AttachResult attach(final Transport transport, final long lastAppliedSequence) {
        Objects.requireNonNull(transport);
        final Attachment previous;
        final Attachment current;
        synchronized (this) {
            if (closed) {
                return AttachResult.rejected("session-expired");
            }
            final long currentSequence = nextSequence - 1;
            if (lastAppliedSequence < lastDiscardedSequence) {
                return AttachResult.rejected("resume-window-exceeded");
            }
            if (lastAppliedSequence < lastAcknowledgedSequence || lastAppliedSequence > currentSequence) {
                return AttachResult.rejected("invalid-resume-position");
            }

            acknowledgeLocked(lastAppliedSequence);
            cancelExpiryLocked();
            previous = attachment;
            current = new Attachment(++attachmentGeneration, transport);
            attachment = current;

            try {
                sendFrames(current.transport(), unacknowledgedFrames);
                current.transport().sendText(RspTransportProtocol.resumeAccepted(currentSequence));
            } catch (final IOException ex) {
                attachment = null;
                scheduleExpiryLocked();
                current.transport().closeSocket();
                if (previous != null) {
                    previous.transport().close(CLOSE_REPLACED, "Connection replaced by failed resume");
                }
                logger.log(DEBUG, () -> failure("WebSocket resume write failed", ex));
                return AttachResult.rejected("transport-write-failed");
            }
        }

        if (previous != null) {
            previous.transport().close(CLOSE_REPLACED, "Connection replaced by resume");
        }
        logger.log(DEBUG, () -> "Local live page attached");
        return AttachResult.accepted(new AttachmentHandle(current.generation()));
    }

    void acknowledge(final AttachmentHandle handle, final long sequence) throws WebSocketProtocolException {
        synchronized (this) {
            if (!isCurrentLocked(handle) || closed) {
                return;
            }
            final long currentSequence = nextSequence - 1;
            if (sequence < lastAcknowledgedSequence || sequence > currentSequence) {
                throw new WebSocketProtocolException(PROTOCOL_ERROR,
                                                     "Invalid RSP acknowledgement sequence");
            }
            acknowledgeLocked(sequence);
        }
    }

    synchronized void acceptApplicationMessage(final AttachmentHandle handle, final String message) {
        if (!isCurrentLocked(handle) || closed) {
            return;
        }
        decoder.decode(message);
    }

    void detach(final AttachmentHandle handle) {
        synchronized (this) {
            if (!isCurrentLocked(handle) || closed) {
                return;
            }
            attachment = null;
            scheduleExpiryLocked();
        }
        logger.log(DEBUG, () -> "Local live page detached");
    }

    void terminate(final AttachmentHandle handle, final String reason) {
        terminate(handle, reason, NORMAL);
    }

    void terminate(final AttachmentHandle handle, final String reason, final int closeCode) {
        final Termination termination;
        synchronized (this) {
            if (!isCurrentLocked(handle)) {
                return;
            }
            termination = beginCloseLocked();
        }
        finishClose(termination, reason, closeCode);
    }

    void close(final String reason) {
        final Termination termination;
        synchronized (this) {
            termination = beginCloseLocked();
        }
        finishClose(termination, reason, NORMAL);
    }

    synchronized boolean isClosed() {
        return closed;
    }

    private void publish(final List<String> applicationMessages) {
        Objects.requireNonNull(applicationMessages);
        if (applicationMessages.isEmpty()) {
            return;
        }
        Transport failedTransport = null;
        boolean overflow = false;
        synchronized (this) {
            if (closed) {
                return;
            }
            final List<SequencedFrame> newFrames = new ArrayList<>(applicationMessages.size());
            for (final String applicationMessage : applicationMessages) {
                Objects.requireNonNull(applicationMessage);
                final long sequence = nextSequence++;
                final int applicationBytes = applicationMessage.getBytes(StandardCharsets.UTF_8).length;
                final int encodedBytes = applicationBytes + 6 + decimalDigits(sequence);
                final SequencedFrame frame = new SequencedFrame(sequence,
                                                                 applicationMessage,
                                                                 applicationBytes,
                                                                 encodedBytes);
                unacknowledgedFrames.addLast(frame);
                newFrames.add(frame);
                bufferedBytes += encodedBytes;
            }

            boolean delivered = false;
            if (attachment != null) {
                if (!attachment.transport().isOpen()) {
                    failedTransport = attachment.transport();
                    attachment = null;
                    scheduleExpiryLocked();
                } else {
                    try {
                        sendFrames(attachment.transport(), newFrames);
                        delivered = true;
                    } catch (final IOException ex) {
                        logger.log(DEBUG, () -> failure("WebSocket write failed", ex));
                        failedTransport = attachment.transport();
                        attachment = null;
                        scheduleExpiryLocked();
                    }
                }
            }

            if (replayBufferExceededLocked()) {
                if (delivered) {
                    trimReplayWindowLocked();
                } else {
                    overflow = true;
                }
            }
        }

        if (failedTransport != null) {
            failedTransport.closeSocket();
        }
        if (overflow) {
            logger.log(WARNING, () -> "Detached local session replay buffer exceeded");
            close("resume-buffer-overflow");
        }
    }

    private void sendFrames(final Transport transport,
                            final Iterable<SequencedFrame> frames) throws IOException {
        final List<SequencedFrame> batch = new ArrayList<>(MAX_TRANSPORT_BATCH_MESSAGES);
        int applicationBytes = 0;
        for (final SequencedFrame frame : frames) {
            if (!batch.isEmpty()
                && (batch.size() == MAX_TRANSPORT_BATCH_MESSAGES
                    || batchEncodedBytes(batch.getFirst().sequence(),
                                         applicationBytes + frame.applicationBytes(),
                                         batch.size() + 1) > MAX_TRANSPORT_BATCH_BYTES)) {
                sendBatch(transport, batch);
                batch.clear();
                applicationBytes = 0;
            }
            batch.add(frame);
            applicationBytes += frame.applicationBytes();
        }
        if (!batch.isEmpty()) {
            sendBatch(transport, batch);
        }
    }

    private void sendBatch(final Transport transport,
                           final List<SequencedFrame> batch) throws IOException {
        if (batch.size() == 1) {
            final SequencedFrame frame = batch.getFirst();
            transport.sendText(RspTransportProtocol.frame(frame.sequence(), frame.applicationMessage()));
            return;
        }
        transport.sendText(RspTransportProtocol.frameBatch(batch.getFirst().sequence(),
                                                           batch.stream()
                                                                   .map(SequencedFrame::applicationMessage)
                                                                   .toList()));
    }

    private static int batchEncodedBytes(final long firstSequence,
                                         final int applicationBytes,
                                         final int messageCount) {
        // [20,<firstSequence>,[<message>,...]]
        return applicationBytes + messageCount - 1 + 8 + decimalDigits(firstSequence);
    }

    private static int decimalDigits(final long value) {
        return Long.toString(value).length();
    }

    private boolean replayBufferExceededLocked() {
        return unacknowledgedFrames.size() > config.maxBufferedMessages()
               || bufferedBytes > config.maxBufferedBytes();
    }

    private void trimReplayWindowLocked() {
        while (!unacknowledgedFrames.isEmpty() && replayBufferExceededLocked()) {
            final SequencedFrame discarded = unacknowledgedFrames.removeFirst();
            bufferedBytes -= discarded.encodedBytes();
            lastDiscardedSequence = discarded.sequence();
        }
    }

    private void acknowledgeLocked(final long sequence) {
        while (!unacknowledgedFrames.isEmpty()
               && unacknowledgedFrames.getFirst().sequence() <= sequence) {
            bufferedBytes -= unacknowledgedFrames.removeFirst().encodedBytes();
        }
        lastAcknowledgedSequence = sequence;
    }

    private boolean isCurrentLocked(final AttachmentHandle handle) {
        return handle != null && attachment != null && attachment.generation() == handle.generation();
    }

    private void scheduleExpiry() {
        synchronized (this) {
            scheduleExpiryLocked();
        }
    }

    private void scheduleExpiryLocked() {
        cancelExpiryLocked();
        final long scheduledGeneration = expiryGeneration;
        expiryTask = expiryScheduler.schedule(() -> expire(scheduledGeneration), config.gracePeriod());
    }

    private void cancelExpiryLocked() {
        expiryGeneration++;
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }

    private void expire(final long scheduledGeneration) {
        final Termination termination;
        synchronized (this) {
            if (closed || attachment != null || scheduledGeneration != expiryGeneration) {
                return;
            }
            termination = beginCloseLocked();
        }
        finishClose(termination, "resume-timeout", NORMAL);
    }

    private Termination beginCloseLocked() {
        if (closed) {
            return null;
        }
        closed = true;
        cancelExpiryLocked();
        final Termination result = new Termination(attachment);
        attachment = null;
        unacknowledgedFrames.clear();
        bufferedBytes = 0;
        return result;
    }

    private void finishClose(final Termination termination, final String reason, final int closeCode) {
        if (termination == null) {
            return;
        }
        if (termination.attachment() != null) {
            termination.attachment().transport().close(closeCode, reason);
        }
        livePage.eventsConsumer().accept(new ShutdownSessionCommand());
        onClosed.accept(this);
        logger.log(DEBUG, () -> "Local live page closed");
    }

    record AttachmentHandle(long generation) {
    }

    record AttachResult(AttachmentHandle handle, String rejectionReason) {
        static AttachResult accepted(final AttachmentHandle handle) {
            return new AttachResult(Objects.requireNonNull(handle), null);
        }

        static AttachResult rejected(final String reason) {
            return new AttachResult(null, Objects.requireNonNull(reason));
        }

        boolean accepted() {
            return handle != null;
        }
    }

    interface Transport {
        boolean isOpen();

        void sendText(String text) throws IOException;

        void close(int code, String reason);

        void closeSocket();
    }

    @FunctionalInterface
    interface ExpiryScheduler {
        ExpiryTask schedule(Runnable task, Duration delay);
    }

    @FunctionalInterface
    interface ExpiryTask {
        void cancel();
    }

    private record Attachment(long generation, Transport transport) {
    }

    private record SequencedFrame(long sequence,
                                  String applicationMessage,
                                  int applicationBytes,
                                  int encodedBytes) {
    }

    private record Termination(Attachment attachment) {
    }
}
