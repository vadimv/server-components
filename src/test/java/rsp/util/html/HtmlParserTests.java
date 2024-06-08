package rsp.util.html;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rsp.dom.*;

import java.util.List;

class HtmlParserTests {

    String htmlString() {
        return """
                  <!DOCTYPE html>
                  <html>
                    <head>
                      <title>title-0</title>
                      <script type="text/javascript"></script>
                    </head>
                    <body>
                        <div>
                          <span>text-0</span>
                          <br>
                          <a href="link-0">link-text-0</a>
                        </div>
                    </body>
                </html>
                """;
    }

    Node expectedNode() {
        var head = new Tag("head", false);
        var title = new Tag("title", false);
        title.addChild(new Text("title-0"));
        head.addChild(title);
        var script = new Tag("script", false);
        script.addAttribute("type", "text/javascript", true);
        head.addChild(script);

        var body = new Tag("body", false);
        var div = new Tag("div", false);
        body.addChild(div);
        var span = new Tag("span", false);
        div.addChild(span);
        var text = new Text("text-0");
        span.addChild(text);
        var br = new Tag("br", true);
        div.addChild(br);
        var a = new Tag("a", false);
        a.addAttribute("href", "link-0", true);
        a.addChild(new Text("link-text-0"));
        div.addChild(a);

        var root = new Tag("html", false);
        root.addChild(head);
        root.addChild(body);

        return root;
    }

    @Test
    void parses_html_document() {
        final HtmlParser htmlParser = new HtmlParser();
        final List<Node> nodes = htmlParser.parse(htmlString());

        nodes.forEach(node -> {
            final HtmlBuilder htmlBuilder = new HtmlBuilder(new StringBuilder(), "<!DOCTYPE html>", HtmlLayout.PAD_2);
            htmlBuilder.buildHtml(node);
            System.out.println(htmlBuilder);
        });


        Assertions.assertEquals(List.of(expectedNode()), nodes);
    }
}
