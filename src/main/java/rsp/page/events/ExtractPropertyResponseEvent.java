package rsp.page.events;

import rsp.server.ExtractPropertyResponse;

public record ExtractPropertyResponseEvent(int descriptorId, ExtractPropertyResponse result) implements SessionEvent {
}
