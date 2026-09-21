package rsp.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamRecordIdTests {
    @Test
    void idMustBePresentAndCanBeStableAcrossRedelivery() {
        assertThrows(NullPointerException.class, () -> new StreamRecordId(null));
        assertThrows(IllegalArgumentException.class, () -> new StreamRecordId("  "));
        assertEquals(new StreamRecordId("orders/0/42"), new StreamRecordId("orders/0/42"));
    }
}
