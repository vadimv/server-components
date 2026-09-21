package rsp.page;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageScopeTests {
    @Test
    void closesResourcesOnceInReverseOrderDespiteOneFailure() {
        PageScope scope = new PageScope();
        List<String> closed = new ArrayList<>();
        scope.own(() -> closed.add("first"));
        scope.own(() -> { closed.add("second"); throw new Exception("broken"); });
        scope.own(() -> closed.add("third"));

        scope.close();
        scope.close();

        assertEquals(List.of("third", "second", "first"), closed);
        assertThrows(IllegalStateException.class, () -> scope.own(() -> closed.add("late")));
        assertEquals(List.of("third", "second", "first", "late"), closed);
    }
}
