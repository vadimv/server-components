package rsp.server;

import java.io.File;

public final class StaticResources {
    public final File resourcesBaseDir;
    public final String contextPath;

    public StaticResources(final File resourcesBaseDir, final String contextPath) {
        this.resourcesBaseDir = resourcesBaseDir;
        this.contextPath = contextPath;
    }
}
