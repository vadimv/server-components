package rsp.page;

/**
 * An identifier for a session.
 * @param deviceId an identifier for a user device / browser
 * @param sessionId an identifier for an active session / browser tab
 */
public record QualifiedSessionId(String deviceId, String sessionId) {
    @Override
    public String toString() {
        return deviceId + "-" + sessionId;
    }
}
