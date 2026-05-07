package rsp.compositions.contract;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

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
     * Capture the URL state of the currently routed contract for later restoration
     * after an inline placement.
     * <p>
     * Returns {@code null} when there is no routed contract or the routed contract
     * has no registered route (e.g., IDE-style UIs without a router).
     */
    Scene.InlineReturnTarget captureInlineReturnTarget(Scene state) {
        if (state.routedRuntime() == null) {
            return null;
        }
        Composition composition = state.composition();
        if (composition == null || composition.router() == null) {
            return null;
        }
        Class<? extends ViewContract> prevClass = state.routedRuntime().contractClass();
        String prevRoute = composition.router().findRoutePattern(prevClass).orElse(null);
        if (prevRoute == null) {
            return null;
        }
        return new Scene.InlineReturnTarget(prevClass, prevRoute,
                captureQuery(state), captureFragment(state));
    }

    /**
     * Push the URL for an inline replacement and return the effective URL that
     * downstream scene context should expose.
     */
    RelativeUrl pushInlineUrl(Scene state,
                              Class<? extends ViewContract> contractClass,
                              Map<String, Object> showData) {
        Composition composition = state.composition();
        if (composition == null || composition.router() == null) {
            return null;
        }
        String pattern = composition.router().findRoutePattern(contractClass).orElse(null);
        if (pattern == null) {
            return null;
        }
        String resolvedPath = substitutePathParams(pattern, showData);
        RelativeUrl url = new RelativeUrl(Path.of(resolvedPath),
                captureQuery(state), captureFragment(state));
        pushUrlOnly(url);
        return url;
    }

    /**
     * Push the URL for a fresh primary-contract selection.
     * <p>
     * SET_PRIMARY intentionally clears query and fragment state because it
     * switches to a different primary contract class.
     */
    RelativeUrl pushPrimaryUrl(Scene state, Class<? extends ViewContract> contractClass) {
        Composition composition = state.composition();
        if (composition == null || composition.router() == null) {
            return null;
        }
        String route = composition.router()
                .findRoutePattern(contractClass)
                .orElse(null);
        if (route == null) {
            return null;
        }
        RelativeUrl url = new RelativeUrl(Path.of(route), Query.EMPTY, Fragment.EMPTY);
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

    /**
     * Substitute {@code :name} placeholders in a route pattern with values from
     * SHOW data. Unknown parameters are left as-is.
     */
    private static String substitutePathParams(String pattern, Map<String, Object> data) {
        if (data == null || data.isEmpty() || !pattern.contains(":")) {
            return pattern;
        }
        String result = pattern;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() == null) continue;
            result = result.replace(":" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static RelativeUrl withQueryParameter(RelativeUrl url, String name, String value) {
        List<Query.Parameter> parameters = new ArrayList<>(url.query().parameters().size() + 1);
        boolean replaced = false;
        for (Query.Parameter parameter : url.query().parameters()) {
            if (parameter.name().equals(name)) {
                parameters.add(new Query.Parameter(name, value));
                replaced = true;
            } else {
                parameters.add(parameter);
            }
        }
        if (!replaced) {
            parameters.add(new Query.Parameter(name, value));
        }
        return new RelativeUrl(url.path(), new Query(parameters), url.fragment());
    }
}
