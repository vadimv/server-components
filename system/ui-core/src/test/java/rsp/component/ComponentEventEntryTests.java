package rsp.component;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ComponentEventEntryTests {

    private final Consumer<ComponentEventEntry.EventContext> noOpHandler = context -> {};

    @Test
    void matches_exact_event_name() {
        final ComponentEventEntry entry = new ComponentEventEntry("click", noOpHandler, false);
        
        assertTrue(entry.matches("click"));
        assertFalse(entry.matches("click2"));
        assertFalse(entry.matches("other"));
    }

    @Test
    void matches_wildcard_event_name() {
        final ComponentEventEntry entry = new ComponentEventEntry("stateUpdated.*", noOpHandler, false);
        
        assertTrue(entry.matches("stateUpdated.sort"));
        assertTrue(entry.matches("stateUpdated.page"));
        assertTrue(entry.matches("stateUpdated.")); // Matches prefix exactly
        
        assertFalse(entry.matches("stateUpdated")); // Missing dot/suffix
        assertFalse(entry.matches("other.sort"));
    }

    @Test
    void equals_ignores_handler() {
        final Consumer<ComponentEventEntry.EventContext> handler1 = context -> {};
        final Consumer<ComponentEventEntry.EventContext> handler2 = context -> {};
        
        final ComponentEventEntry entry1 = new ComponentEventEntry("click", handler1, false);
        final ComponentEventEntry entry2 = new ComponentEventEntry("click", handler2, false);
        
        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
    }

    @Test
    void equals_respects_event_name_and_prevent_default() {
        final ComponentEventEntry entry1 = new ComponentEventEntry("click", noOpHandler, false);
        final ComponentEventEntry entry2 = new ComponentEventEntry("hover", noOpHandler, false);
        final ComponentEventEntry entry3 = new ComponentEventEntry("click", noOpHandler, true);
        
        assertNotEquals(entry1, entry2);
        assertNotEquals(entry1, entry3);
    }

    @Test
    void constructor_throws_npe_on_nulls() {
        assertThrows(NullPointerException.class, () -> new ComponentEventEntry(null, noOpHandler, false));
        assertThrows(NullPointerException.class, () -> new ComponentEventEntry("click", null, false));
    }
    
    @Test
    void event_context_constructor_throw_npe_on_nulls() {
        assertThrows(NullPointerException.class, () -> new ComponentEventEntry.EventContext(null, new Object()));
        assertThrows(NullPointerException.class, () -> new ComponentEventEntry.EventContext("event", null));
    }
}
