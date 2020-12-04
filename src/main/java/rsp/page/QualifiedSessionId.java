package rsp.page;

import java.util.Objects;

public final class QualifiedSessionId {
    public final String deviceId;
    public final String sessionId;

    public QualifiedSessionId(String deviceId, String sessionId) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final QualifiedSessionId qsid = (QualifiedSessionId) o;
        return deviceId.equals(qsid.deviceId) &&
                sessionId.equals(qsid.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, sessionId);
    }

    @Override
    public String toString() {
        return deviceId + "-" + sessionId;
    }
}
