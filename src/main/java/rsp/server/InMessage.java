package rsp.server;

import rsp.dom.Path;

public interface InMessage {
    HeartBeat HEART_BEAT = new HeartBeat();

    class HeartBeat implements InMessage {
    }

    //`$renderNum:$elementId:$eventType`
    class DomEventInMessage implements InMessage {
        public final int renderNumber;
        public final Path path;
        public final String eventType;

        public DomEventInMessage(int renderNumber, Path path, String eventType) {
            this.renderNumber = renderNumber;
            this.path = path;
            this.eventType = eventType;
        }
    }
    class ExtractPropertyResponseInMessage implements InMessage {
        public final int descriptor;
        public final String value;

        public ExtractPropertyResponseInMessage(int descriptor, String value) {
            this.descriptor = descriptor;
            this.value = value;
        }
    }
}
