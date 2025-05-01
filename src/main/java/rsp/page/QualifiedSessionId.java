package rsp.page;

public record QualifiedSessionId(String deviceId, String sessionId) {
    @Override
    public String toString() {
        return deviceId + "-" + sessionId;
    }
}
