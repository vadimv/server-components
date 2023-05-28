package rsp.browserautomation;

import java.util.Optional;

public class BrowserUtils {
    public static Optional<String> style(final String stylesAttributeValue, final String styleName) {
        final String[] styles = stylesAttributeValue.split(";");
        for (final String style : styles) {
            final String[] styleTokens = style.split(":");
            if (styleName.equals(styleTokens[0].trim())) {
                return Optional.of(styleTokens[1].trim());
            }
        }
        return Optional.empty();
    }
}
