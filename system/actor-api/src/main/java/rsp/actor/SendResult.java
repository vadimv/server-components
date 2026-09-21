package rsp.actor;

/** Admission result only; ACCEPTED does not mean processed or persisted. */
public enum SendResult {
    ACCEPTED,
    NOT_STARTED,
    MAILBOX_FULL,
    STOPPED,
    UNKNOWN_ACTOR
}
