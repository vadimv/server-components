package rsp.util;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;

public class HtmlAssertions {

    public static void assertHtmlFragmentsEqual(String html1, String html2) {
        final Document html1Doc = org.jsoup.Jsoup.parseBodyFragment(html1);
        final Document html2Doc = org.jsoup.Jsoup.parseBodyFragment(html2);

        if (!html1Doc.body().hasSameValue(html2Doc.body())) {
            Assertions.fail("\nExpected:\n" + html1Doc.body().children()
                            + "\nActual:\n" + html2Doc.body().children());
        }
    }
}
