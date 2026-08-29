package rsp.compositions.block;

import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable navigation and layout snapshot for a rendered view.
 *
 * A scene holds block descriptors, never live blocks or lookup scopes.
 * {@link DirectBlockHost} turns each descriptor into a live block only for
 * the lifetime of its rendered branch.
 */
public record Scene(BlockDescriptor routedDescriptor,
                    Map<Object, BlockDescriptor> companionDescriptors,
                    Map<Object, BlockDescriptor> preActivatedDescriptors,
                    Composition composition,
                    long timestamp,
                    AutoOpen autoOpen,
                    String pageTitle,
                    InlineReturnTarget inlineReturnTarget,
                    RelativeUrl effectiveUrl) {
    public Scene {
        companionDescriptors = orderedCopy(companionDescriptors, "companionDescriptors");
        preActivatedDescriptors = orderedCopy(preActivatedDescriptors, "preActivatedDescriptors");
        Objects.requireNonNull(composition, "composition");
        pageTitle = pageTitle == null || pageTitle.isBlank() ? "App" : pageTitle;
    }

    public record AutoOpen(Object blockKey,
                           Class<? extends Block<?, ?>> blockClass,
                           String routePattern) {
        public AutoOpen(Class<? extends Block<?, ?>> blockClass, String routePattern) {
            this(blockClass, blockClass, routePattern);
        }

        public AutoOpen {
            Objects.requireNonNull(blockKey, "blockKey");
            Objects.requireNonNull(blockClass, "blockClass");
            Objects.requireNonNull(routePattern, "routePattern");
        }
    }

    public record InlineReturnTarget(Object blockKey,
                                     Class<? extends Block<?, ?>> blockClass,
                                     String route,
                                     Query query,
                                     Fragment fragment) {
        public InlineReturnTarget(Class<? extends Block<?, ?>> blockClass,
                                  String route,
                                  Query query,
                                  Fragment fragment) {
            this(blockClass, blockClass, route, query, fragment);
        }

        public InlineReturnTarget {
            Objects.requireNonNull(blockKey, "blockKey");
            Objects.requireNonNull(blockClass, "blockClass");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(fragment, "fragment");
        }
    }

    public Group blocks() {
        return composition.blocks();
    }

    public Class<? extends Block<?, ?>> routedBlockClass() {
        return routedDescriptor == null ? null : routedDescriptor.blockClass();
    }

    public Object routedBlockKey() {
        return routedDescriptor == null ? null : routedDescriptor.blockKey();
    }

    public BlockDescriptor companionDescriptor(Object blockKey) {
        return companionDescriptors.get(blockKey);
    }

    public BlockDescriptor preActivatedDescriptor(Object blockKey) {
        return preActivatedDescriptors.get(blockKey);
    }

    public boolean hasPreActivatedBlocks() {
        return !preActivatedDescriptors.isEmpty();
    }

    public boolean isRouted(Class<? extends Block<?, ?>> blockClass) {
        return isRouted((Object) blockClass);
    }

    public boolean isRouted(Object blockKey) {
        return routedDescriptor != null && routedDescriptor.blockKey().equals(blockKey);
    }

    public Scene withRoutedDescriptor(BlockDescriptor descriptor) {
        return new Scene(descriptor, companionDescriptors, preActivatedDescriptors,
                composition, timestamp, autoOpen, "App", inlineReturnTarget, effectiveUrl);
    }

    public Scene withInlineReturnTarget(InlineReturnTarget target) {
        return new Scene(routedDescriptor, companionDescriptors, preActivatedDescriptors,
                composition, timestamp, autoOpen, pageTitle, target, effectiveUrl);
    }

    public Scene clearInlineReturnTarget() {
        return new Scene(routedDescriptor, companionDescriptors, preActivatedDescriptors,
                composition, timestamp, autoOpen, pageTitle, null, effectiveUrl);
    }

    public Scene withEffectiveUrl(RelativeUrl url) {
        return new Scene(routedDescriptor, companionDescriptors, preActivatedDescriptors,
                composition, timestamp, autoOpen, pageTitle, inlineReturnTarget, url);
    }

    public Scene withPageTitle(String title) {
        return new Scene(routedDescriptor, companionDescriptors, preActivatedDescriptors,
                composition, timestamp, autoOpen, title, inlineReturnTarget, effectiveUrl);
    }

    public static Scene of(BlockDescriptor routedDescriptor,
                           Map<Object, BlockDescriptor> companionDescriptors,
                           Composition composition) {
        return new Scene(routedDescriptor, companionDescriptors, Map.of(), composition,
                System.currentTimeMillis(), null, "App", null, null);
    }

    public static Scene withAutoOpen(BlockDescriptor routedDescriptor,
                                     Map<Object, BlockDescriptor> companionDescriptors,
                                     Map<Object, BlockDescriptor> preActivatedDescriptors,
                                     Composition composition,
                                     AutoOpen autoOpen) {
        return new Scene(routedDescriptor, companionDescriptors, preActivatedDescriptors,
                composition, System.currentTimeMillis(), autoOpen, "App", null, null);
    }

    private static <K, V> Map<K, V> orderedCopy(Map<K, V> source, String name) {
        Objects.requireNonNull(source, name);
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
