package rsp.util;

import java.util.List;

public class TestUtils {
    public static <U> boolean containsType(final Class<?> modifyDomOutMessageClass, final List<U> list) {
        for (U element : list) {
            if (modifyDomOutMessageClass.isAssignableFrom(element.getClass())) {
                return true;
            }
        }
        return false;
    }
}
