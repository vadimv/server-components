package rsp.server.http;

import java.util.Objects;

public record Fragment(String fragmentString) {
    public static final Fragment EMPTY = new Fragment("");

    public Fragment {
        Objects.requireNonNull(fragmentString);
   }
    @Override
    public String toString() {
        return fragmentString.isEmpty() ? "" : "#" + fragmentString;
    }
}
