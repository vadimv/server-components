package rsp.util;

import java.util.List;
import java.util.Optional;

public class TestUtils {
    public static <U> Optional<U> findFirstListElementByType(final Class<U> modifyDomOutMessageClass, final List<?> list) {
        for (int i = 0; i < list.size();i++) {
            if (modifyDomOutMessageClass.isAssignableFrom(list.get(i).getClass())) {
                return (Optional<U>) Optional.of(list.get(i));
            }
        }
        return Optional.empty();
    }
}
