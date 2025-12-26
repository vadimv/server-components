package rsp.server;

import java.io.File;
import java.util.Objects;

/**
 * Represents a mapping of a static resources base directory path to a web resource path.
 * @param resourcesBaseDir a directory to recursively read resources, e.g. files and subdirectories, must not be null
 * @param contextPath a relative URL path to the static resources, e.g. "/res/", must not be null
 */
public record StaticResources(File resourcesBaseDir, String contextPath) {
        public StaticResources {
                Objects.requireNonNull(resourcesBaseDir);
                Objects.requireNonNull(contextPath);
        }
}
