package rsp.server;

import java.io.File;

public class StaticResources {
    public final File resourcesBaseDir;
    public final String contextPath;

    public StaticResources(File resourcesBaseDir, String contextPath) {
        this.resourcesBaseDir = resourcesBaseDir;
        this.contextPath = contextPath;
    }
}
