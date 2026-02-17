package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.component.Lookup;
import rsp.compositions.contract.Capabilities;
import rsp.compositions.contract.ViewContract;

/**
 * Header contract that displays the active category name.
 * <p>
 * Subscribes to the {@link Capabilities#ACTIVE_CATEGORY} capability
 * published by the primary contract (e.g., "Posts", "Comments").
 * The category name is resolved synchronously before rendering via {@link rsp.compositions.contract.CapabilityBus}.
 */
public class HeaderContract extends ViewContract {

    public static final ContextKey.StringKey<String> HEADER_CATEGORY =
            new ContextKey.StringKey<>("header.category", String.class);

    private String activeCategory = "";

    public HeaderContract(Lookup lookup) {
        super(lookup);
        onCapability(Capabilities.ACTIVE_CATEGORY, category -> {
            this.activeCategory = category;
        });
    }

    @Override
    public String title() {
        return "Header";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context.with(HEADER_CATEGORY, activeCategory);
    }
}
