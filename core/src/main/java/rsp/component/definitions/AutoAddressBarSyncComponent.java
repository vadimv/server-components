package rsp.component.definitions;

import rsp.component.ComponentContext;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An AddressBarSyncComponent that automatically populates ALL URL data into the ComponentContext
 * using a standard namespace convention.
 *
 * <p>For URL: {@code /posts/123?p=2&sort=desc#top}, the context will contain:</p>
 * <ul>
 *   <li>{@code "url.path.0"} → {@code "posts"}</li>
 *   <li>{@code "url.path.1"} → {@code "123"}</li>
 *   <li>{@code "url.query.p"} → {@code "2"}</li>
 *   <li>{@code "url.query.sort"} → {@code "desc"}</li>
 *   <li>{@code "url.fragment"} → {@code "top"}</li>
 * </ul>
 *
 * <p>This eliminates the need to manually configure which path elements and query parameters
 * to extract - all URL data is discoverable in the context using standard namespace keys.</p>
 *
 * <p><strong>Benefits:</strong></p>
 * <ul>
 *   <li>Zero configuration required</li>
 *   <li>All URL data available by default</li>
 *   <li>Standard namespace convention</li>
 *   <li>Downstream components pick what they need</li>
 *   <li>Follows Unix philosophy: "everything is a typed object in its namespace"</li>
 * </ul>
 */
public abstract class AutoAddressBarSyncComponent extends AddressBarSyncComponent {

    public AutoAddressBarSyncComponent(final RelativeUrl initialRelativeUrl) {
        super(initialRelativeUrl);
    }

    /**
     * Returns an empty list - not used in auto-population mode.
     * All path elements are automatically populated with keys like "url.path.0", "url.path.1", etc.
     */
    @Override
    public List<PositionKey> pathElementsPositionKeys() {
        return List.of();
    }

    /**
     * Returns an empty list - not used in auto-population mode.
     * All query parameters are automatically populated with keys like "url.query.{paramName}".
     */
    @Override
    public List<ParameterNameKey> queryParametersNamedKeys() {
        return List.of();
    }

    /**
     * Automatically populates the ComponentContext with ALL URL data using standard namespaces:
     * <ul>
     *   <li>Path elements: {@code "url.path.0"}, {@code "url.path.1"}, ...</li>
     *   <li>Query parameters: {@code "url.query.{name}"}</li>
     *   <li>Fragment: {@code "url.fragment"}</li>
     * </ul>
     */
    @Override
    public BiFunction<ComponentContext, RelativeUrl, ComponentContext> subComponentsContext() {
        return (componentContext, relativeUrl) -> {
            final Map<String, Object> m = new HashMap<>();

            // Auto-populate ALL path elements with namespace "url.path.{index}"
            for (int i = 0; i < relativeUrl.path().elementsCount(); i++) {
                final String pathElement = relativeUrl.path().get(i);
                if (pathElement != null) {
                    m.put("url.path." + i, pathElement);
                }
            }

            // Auto-populate ALL query parameters with namespace "url.query.{name}"
            for (final Query.Parameter param : relativeUrl.query().parameters()) {
                m.put("url.query." + param.name(), param.value());
            }

            // Fragment (if present) with namespace "url.fragment"
            if (relativeUrl.fragment() != null &&
                !relativeUrl.fragment().isEmpty()) {
                m.put("url.fragment", relativeUrl.fragment().fragmentString());
            }

            return componentContext.with(m);
        };
    }
}
