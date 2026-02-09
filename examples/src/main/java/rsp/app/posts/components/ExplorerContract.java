package rsp.app.posts.components;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.ViewContract;

import java.util.List;

import static rsp.compositions.contract.EventKeys.SET_PRIMARY;

/**
 * ExplorerContract - Navigation sidebar contract.
 * <p>
 * Reads framework-provided {@link NavigationEntry} list from context and relays
 * menu selection events as {@code SET_PRIMARY} commands.
 * <p>
 * Register in LEFT_SIDEBAR slot:
 * <pre>{@code
 * places.place(Slot.LEFT_SIDEBAR, ExplorerContract.class, ExplorerContract::new)
 * }</pre>
 */
public class ExplorerContract extends ViewContract {

    public static EventKey.SimpleKey<NavigationEntry> REQUEST_OPEN_CONTRACT =
            new EventKey.SimpleKey<>("explorer.open.contract", NavigationEntry.class);

    private final List<NavigationEntry> entries;

    public ExplorerContract(Lookup lookup) {
        super(lookup);
        List<NavigationEntry> navEntries = lookup.get(ContextKeys.NAVIGATION_ENTRIES);
        this.entries = navEntries != null ? navEntries : List.of();

        subscribe(REQUEST_OPEN_CONTRACT, (eventName, entry) -> {
            lookup.publish(SET_PRIMARY, entry.contractClass());
        });
    }

    @Override
    public String title() {
        return "Explorer";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context.with(ContextKeys.NAVIGATION_ENTRIES, entries);
    }
}
