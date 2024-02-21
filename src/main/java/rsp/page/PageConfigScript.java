package rsp.page;

public record PageConfigScript(String sessionId,
                               String path,
                               String connectionLostWidgetHtml,
                               int heartBeatInterval) {
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
