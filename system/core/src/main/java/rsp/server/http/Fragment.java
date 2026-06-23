package rsp.server.http;

import java.util.Objects;

/**
 * Represents a URL fragment.
 * @see RelativeUrl
 * @param fragmentString a string that refers to a subordinate resource
 */
public record Fragment(String fragmentString) {
    public static final Fragment EMPTY = new Fragment("");

    public Fragment {
        Objects.requireNonNull(fragmentString);
   }

   public boolean isEmpty() {
        return fragmentString.isEmpty();
   }

    @Override
    public String toString() {
        return fragmentString.isEmpty() ? "" : "#" + fragmentString;
    }
}
