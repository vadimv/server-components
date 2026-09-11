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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.WARNING;

/**
 * A live page whose component state outlives any one WebSocket attachment.
 */
final class ResumablePageSession {
    private static final System.Logger logger = System.getLogger(ResumablePageSession.class.getName());
    private static final int CLOSE_REPLACED = 4000;

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

        final RemoteOut remoteOut = new RemotePageMessageEncoder(this::publish);
        this.livePage = new LivePageSession(Objects.requireNonNull(eventLoop));
        this.decoder = new RemotePageMessageDecoder(JsonUtils.createParser(), livePage.eventsConsumer());
        livePage.eventsConsumer().accept(new InitSessionCommand(renderedPage.pageBuilder(),
                                                                renderedPage.commandsEnqueue(),
                                                                remoteOut));
        remoteOut.setRenderNum(0);
        livePage.start();
        scheduleExpiry();
        logger.log(DEBUG, () -> "Local live page created: " + sessionId);
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
            if (lastAppliedSequence < lastAcknowledgedSequence || lastAppliedSequence > currentSequence) {
                return AttachResult.rejected("invalid-resume-position");
            }

            acknowledgeLocked(lastAppliedSequence);
            cancelExpiryLocked();
            previous = attachment;
            current = new Attachment(++attachmentGeneration, transport);
            attachment = current;

            try {
                for (final SequencedFrame frame : unacknowledgedFrames) {
                    current.transport().sendText(frame.encodedMessage());
                }
                current.transport().sendText(RspTransportProtocol.resumeAccepted(currentSequence));
            } catch (final IOException ex) {
                attachment = null;
                scheduleExpiryLocked();
                current.transport().closeSocket();
                if (previous != null) {
                    previous.transport().close(CLOSE_REPLACED, "Connection replaced by failed resume");
                }
                logger.log(DEBUG, "WebSocket resume write failed for " + sessionId, ex);
                return AttachResult.rejected("transport-write-failed");
            }
        }

        if (previous != null) {
            previous.transport().close(CLOSE_REPLACED, "Connection replaced by resume");
        }
        logger.log(DEBUG, () -> "Local live page attached: " + sessionId);
        return AttachResult.accepted(new AttachmentHandle(current.generation()));
    }

    void acknowledge(final AttachmentHandle handle, final long sequence) throws WebSocketProtocolException {
        synchronized (this) {
            if (!isCurrentLocked(handle) || closed) {
                return;
            }
            final long currentSequence = nextSequence - 1;
            if (sequence < lastAcknowledgedSequence || sequence > currentSequence) {
                throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR,
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
        logger.log(DEBUG, () -> "Local live page detached: " + sessionId);
    }

    void terminate(final AttachmentHandle handle, final String reason) {
        terminate(handle, reason, WebSocketFrame.CLOSE_NORMAL);
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
        finishClose(termination, reason, WebSocketFrame.CLOSE_NORMAL);
    }

    synchronized boolean isClosed() {
        return closed;
    }

    private void publish(final String applicationMessage) {
        Objects.requireNonNull(applicationMessage);
        Transport failedTransport = null;
        boolean overflow = false;
        synchronized (this) {
            if (closed) {
                return;
            }
            final long sequence = nextSequence++;
            final String encodedMessage = RspTransportProtocol.frame(sequence, applicationMessage);
            final int encodedBytes = encodedMessage.getBytes(StandardCharsets.UTF_8).length;
            unacknowledgedFrames.addLast(new SequencedFrame(sequence, encodedMessage, encodedBytes));
            bufferedBytes += encodedBytes;

            if (unacknowledgedFrames.size() > config.maxBufferedMessages()
                || bufferedBytes > config.maxBufferedBytes()) {
                overflow = true;
            } else if (attachment != null) {
                if (!attachment.transport().isOpen()) {
                    failedTransport = attachment.transport();
                    attachment = null;
                    scheduleExpiryLocked();
                } else {
                    try {
                        attachment.transport().sendText(encodedMessage);
                    } catch (final IOException ex) {
                        logger.log(DEBUG, "WebSocket write failed for " + sessionId, ex);
                        failedTransport = attachment.transport();
                        attachment = null;
                        scheduleExpiryLocked();
                    }
                }
            }
        }

        if (failedTransport != null) {
            failedTransport.closeSocket();
        }
        if (overflow) {
            logger.log(WARNING, () -> "Local session replay buffer exceeded for " + sessionId);
            close("resume-buffer-overflow");
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
        finishClose(termination, "resume-timeout", WebSocketFrame.CLOSE_NORMAL);
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
        logger.log(DEBUG, () -> "Local live page closed (" + reason + "): " + sessionId);
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

    private record SequencedFrame(long sequence, String encodedMessage, int encodedBytes) {
    }

    private record Termination(Attachment attachment) {
    }
}
