package rsp.util.html;

import org.junit.jupiter.api.Test;
import rsp.dom.Node;

import java.util.List;

public class HtmlParserTests {

    final String html =
            """
                <!DOCTYPE html>
                <html>
                    <head>
                      <title>test-title</title>
                      <script type="text/javascript"></script>
                    </head>
                    <body>
                        <div>
                          <span>text-0</span>
                          <br>
                          <a href="link">link</a>
                        </div>
                    </body>
                </html>
            """;
    @Test
    void parses_html_document() {
        final HtmlParser htmlParser = new HtmlParser();
        final List<Node> nodes = htmlParser.parse(html);
        System.out.println(nodes);

    }
}
