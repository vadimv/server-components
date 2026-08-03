package rsp.compositions.contract;

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
 * A scene holds contract descriptors, never live contracts or lookup scopes.
 * {@link DirectContractHost} turns each descriptor into a live contract only for
 * the lifetime of its rendered branch.
 */
public record Scene(ContractDescriptor routedDescriptor,
                    Map<Class<? extends Contract>, ContractDescriptor> companionDescriptors,
                    Map<Class<? extends Contract>, ContractDescriptor> preActivatedDescriptors,
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

    public record AutoOpen(Class<? extends Contract> contractClass, String routePattern) {
        public AutoOpen {
            Objects.requireNonNull(contractClass, "contractClass");
            Objects.requireNonNull(routePattern, "routePattern");
        }
    }

    public record InlineReturnTarget(Class<? extends Contract> contractClass,
                                     String route,
                                     Query query,
                                     Fragment fragment) {
        public InlineReturnTarget {
            Objects.requireNonNull(contractClass, "contractClass");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(fragment, "fragment");
        }
    }

    public Group contracts() {
        return composition.contracts();
    }

    public Class<? extends Contract> routedContractClass() {
        return routedDescriptor == null ? null : routedDescriptor.contractClass();
    }

    public ContractDescriptor companionDescriptor(Class<? extends Contract> contractClass) {
        return companionDescriptors.get(contractClass);
    }

    public ContractDescriptor preActivatedDescriptor(Class<? extends Contract> contractClass) {
        return preActivatedDescriptors.get(contractClass);
    }

    public boolean hasPreActivatedContracts() {
        return !preActivatedDescriptors.isEmpty();
    }

    public boolean isRouted(Class<? extends Contract> contractClass) {
        return routedDescriptor != null && routedDescriptor.contractClass().equals(contractClass);
    }

    public Scene withRoutedDescriptor(ContractDescriptor descriptor) {
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

    public static Scene of(ContractDescriptor routedDescriptor,
                           Map<Class<? extends Contract>, ContractDescriptor> companionDescriptors,
                           Composition composition) {
        return new Scene(routedDescriptor, companionDescriptors, Map.of(), composition,
                System.currentTimeMillis(), null, "App", null, null);
    }

    public static Scene withAutoOpen(ContractDescriptor routedDescriptor,
                                     Map<Class<? extends Contract>, ContractDescriptor> companionDescriptors,
                                     Map<Class<? extends Contract>, ContractDescriptor> preActivatedDescriptors,
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
