package rsp.server;

import java.io.File;
import java.util.Objects;

public final class StaticResources {
    public final File resourcesBaseDir;
    public final String contextPath;

    public StaticResources(final File resourcesBaseDir, final String contextPath) {
        this.resourcesBaseDir = Objects.requireNonNull(resourcesBaseDir);
        this.contextPath = Objects.requireNonNull(contextPath);
    }
}
