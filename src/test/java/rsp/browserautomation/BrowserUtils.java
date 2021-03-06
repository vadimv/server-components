package rsp.browserautomation;

import java.util.Optional;

public class BrowserUtils {
    public static Optional<String> style(String stylesAttributeValue, String styleName) {
        final String[] styles = stylesAttributeValue.split(";");
        for (String style : styles) {
            final String[] styleTokens = style.split(":");
            if (styleName.equals(styleTokens[0].trim())) {
                return Optional.of(styleTokens[1].trim());
            }
        }
        return Optional.empty();
    }
}
