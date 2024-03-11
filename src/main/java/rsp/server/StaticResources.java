package rsp.server;

import java.io.File;
import java.util.Objects;

public record StaticResources(File resourcesBaseDir, String contextPath) {
        public StaticResources {
                Objects.requireNonNull(resourcesBaseDir);
                Objects.requireNonNull(contextPath);
        }
}
