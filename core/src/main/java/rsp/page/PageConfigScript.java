package rsp.page;

import java.util.Objects;

public record PageConfigScript(String sessionId,
                               String path,
                               String connectionLostWidgetHtml,
                               int heartBeatInterval) {
    public PageConfigScript {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(path);
        Objects.requireNonNull(connectionLostWidgetHtml);
    }
    @Override
    public String toString() {
        return "window['kfg']={"
                + "sid:'" + sessionId + "',"
                + "r:'" + path + "',"
                + "clw:'" + connectionLostWidgetHtml + "',"
                + "heartbeatInterval:" + heartBeatInterval
                + "}";
    }
}
