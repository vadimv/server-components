package rsp.compositions.block;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.url.Path;
import rsp.url.Fragment;
import rsp.url.Query;
import rsp.url.RelativeUrl;
import rsp.url.routing.RouteTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.PUSH_URL_ONLY;

/**
 * Centralizes scene-local URL transitions for the base scene.
 * <p>
 * Scene-local transitions mutate {@link Scene} directly and then decorate
 * browser history with {@link AutoAddressBarSyncComponent.PathUpdateMode#PUSH_URL_ONLY}.
 * They should not ask the root route shell to apply the same transition again.
 */
final class SceneNavigator {
    private final ComponentContext savedContext;
    private final CommandsEnqueue commandsEnqueue;

    SceneNavigator(ComponentContext savedContext, CommandsEnqueue commandsEnqueue) {
        this.savedContext = Objects.requireNonNull(savedContext, "savedContext");
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
    }

    /**
     * Capture the URL state of the currently routed block for later restoration
     * after an inline placement.
     * <p>
     * Returns {@code null} when there is no routed block or the routed block
     * has no registered route (e.g., IDE-style UIs without a router).
     */
    Scene.InlineReturnTarget captureInlineReturnTarget(Scene state) {
        if (state.routedDescriptor() == null) {
            return null;
        }
        Composition composition = state.composition();
        if (composition == null || composition.routes() == null) {
            return null;
        }
        Object prevKey = state.routedDescriptor().blockKey();
        Class<? extends Block<?, ?>> prevClass = state.routedDescriptor().blockClass();
        String prevRoute = composition.routes()
                .templateFor(composition.blocks().target(prevKey))
                .map(RouteTemplate::toString)
                .orElse(null);
        if (prevRoute == null) {
            return null;
        }
        return new Scene.InlineReturnTarget(prevKey, prevClass, prevRoute,
                captureQuery(state), captureFragment(state));
    }

    /**
     * Push the URL for an inline replacement and return the effective URL that
     * downstream scene context should expose.
     */
    RelativeUrl pushInlineUrl(Scene state,
                              Object blockKey,
                              Map<String, Object> showData) {
        Composition composition = state.composition();
        if (composition == null || composition.routes() == null) {
            return null;
        }
        RouteTemplate template = composition.routes()
                .templateFor(composition.blocks().target(blockKey))
                .orElse(null);
        if (template == null) {
            return null;
        }
        String resolvedPath = template.expand(showData);
        RelativeUrl url = new RelativeUrl(Path.of(resolvedPath),
                captureQuery(state), captureFragment(state));
        pushUrlOnly(url);
        return url;
    }

    /**
     * Push the URL for a fresh primary-block selection.
     * <p>
     * SET_PRIMARY intentionally clears query and fragment state because it
     * switches to a different primary block class.
     */
    RelativeUrl pushPrimaryUrl(Scene state, Object blockKey) {
        Composition composition = state.composition();
        if (composition == null || composition.routes() == null) {
            return null;
        }
        RouteTemplate route = composition.routes()
                .templateFor(composition.blocks().target(blockKey))
                .orElse(null);
        if (route == null) {
            return null;
        }
        RelativeUrl url = new RelativeUrl(Path.parse(route.expand(Map.of())), Query.EMPTY, Fragment.EMPTY);
        pushUrlOnly(url);
        return url;
    }

    RelativeUrl pushReturnUrl(Scene.InlineReturnTarget target) {
        RelativeUrl url = new RelativeUrl(Path.of(target.route()), target.query(), target.fragment());
        pushUrlOnly(url);
        return url;
    }

    RelativeUrl pushSceneQueryUpdate(RelativeUrl currentUrl, EventKeys.SceneQueryUpdate update) {
        if (currentUrl == null) {
            return null;
        }
        RelativeUrl updatedUrl = withQueryParameter(currentUrl, update.name(), update.value());
        pushUrlOnly(updatedUrl);
        return updatedUrl;
    }

    RelativeUrl pushSceneQueryUpdates(RelativeUrl currentUrl, EventKeys.SceneQueryUpdates updates) {
        if (currentUrl == null) {
            return null;
        }
        RelativeUrl updatedUrl = withQueryParameters(currentUrl, updates.values());
        pushUrlOnly(updatedUrl);
        return updatedUrl;
    }

    private void pushUrlOnly(RelativeUrl url) {
        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
        lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                new AutoAddressBarSyncComponent.PathUpdate(url, PUSH_URL_ONLY));
    }

    private Query captureQuery(Scene state) {
        if (state.effectiveUrl() != null) {
            return state.effectiveUrl().query();
        }
        String prefix = ContextKeys.URL_QUERY.baseKey() + ".";
        Map<String, Object> entries = savedContext.stringEntriesWithPrefix(prefix);
        if (entries.isEmpty()) {
            return Query.EMPTY;
        }
        List<Query.Parameter> params = new ArrayList<>(entries.size());
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            String name = entry.getKey().substring(prefix.length());
            if (entry.getValue() instanceof String value) {
                params.add(new Query.Parameter(name, value));
            }
        }
        return params.isEmpty() ? Query.EMPTY : new Query(params);
    }

    private Fragment captureFragment(Scene state) {
        if (state.effectiveUrl() != null) {
            return state.effectiveUrl().fragment();
        }
        String value = savedContext.get(ContextKeys.URL_FRAGMENT);
        if (value == null || value.isEmpty()) {
            return Fragment.EMPTY;
        }
        return new Fragment(value);
    }

    private static RelativeUrl withQueryParameter(RelativeUrl url, String name, String value) {
        return withQueryParameters(url, Map.of(name, value));
    }

    private static RelativeUrl withQueryParameters(RelativeUrl url, Map<String, String> updates) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (Query.Parameter parameter : url.query().parameters()) {
            values.put(parameter.name(), parameter.value());
        }
        updates.forEach((name, value) -> {
            if (value == null || value.isBlank()) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
        });
        List<Query.Parameter> parameters = values.entrySet().stream()
                .map(entry -> new Query.Parameter(entry.getKey(), entry.getValue()))
                .toList();
        return new RelativeUrl(url.path(), new Query(parameters), url.fragment());
    }
}
