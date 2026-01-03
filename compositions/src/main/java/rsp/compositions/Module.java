package rsp.compositions;

import java.util.List;

public interface Module {
    List<ViewPlacement> views();
    List<NotificationContract> notifications();
    List<ActionContract> actions();
}
