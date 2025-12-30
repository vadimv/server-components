package rsp.compositions;

import java.util.List;

public interface Module {
    String name();
    List<ViewPlacement> views();
    List<NotificationContract> notifications();
    List<ActionContract> actions();
}
