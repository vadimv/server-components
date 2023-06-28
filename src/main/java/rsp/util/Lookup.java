package rsp.util;

public interface Lookup {
    <T> T lookup(final Class<T> clazz);
}
