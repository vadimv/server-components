package rsp.dsl;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.View;
import rsp.component.definitions.InitialStateComponent;
import rsp.page.QualifiedSessionId;
import rsp.server.TestCollectingRemoteOut;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.dsl.Html.*;
import static rsp.page.PageBuilder.DOCUMENT_DOM_PATH;

public class HtmlDefinitionPropertyTests {

    @Property
    void generated_dsl_should_produce_valid_and_correct_html(@ForAll("dslTags") final Tag generatedTag) {
        // 1. Setup the full document plain DSL view
        final View<String> view = _ -> html(
            head(title("pbt-test")),
            body(generatedTag)
        );

        // 2. Render the DSL to an HTML string
        final String renderedHtml = htmlOf(view, "");

        // 3. Validate with jsoup
        final Document parsedDoc = org.jsoup.Jsoup.parse(renderedHtml);

        // 3a. Validate document's structure
        final Element head = parsedDoc.head();
        assertNotNull(head);

        final String title = parsedDoc.title();
        assertEquals("pbt-test", title);

        final Element body = parsedDoc.body();
        assertNotNull(body);

        // 3b. Check for the generated tag in the body
        final Element renderedElement = body.children().first(); // Assuming it's the only child
        assertNotNull(renderedElement);

        // 3c. Validate tag name (case-insensitive for HTML)
        assertEquals(generatedTag.name.toLowerCase(), renderedElement.tagName());
        
        // 3d. Validate counts
        final Counts dslCounts = countDefinitions(generatedTag);
        final Counts htmlCounts = countJsoupElements(renderedElement);
        assertEquals(dslCounts, htmlCounts);
    }

    @Provide
    Arbitrary<Tag> dslTags() {
        return Arbitraries.recursive(
            // Base case: Inline tags / leaf tags
            () -> Arbitraries.of(span(text("text")), br(), input(attr("type", "text"))),

            // Recursive step: Container tags
            (childArbitrary) -> {
                // Div can contain anything
                final Arbitrary<Tag> divWithChildren = childArbitrary.list().ofMaxSize(3)
                    .map(children -> div(children.toArray(new Definition[0])));

                // P should ideally only contain inline elements, but for this test let's just use div to be safe
                // or we can define a separate "inline" generator. 
                // To keep it simple and robust against Jsoup parsing rules, let's stick to div nesting.
                
                return divWithChildren;
            },
            5 // Max recursion depth
        );
    }

    private static <S> String htmlOf(final View<S> view, final S initialState) {
        final var component = new InitialStateComponent<>(initialState, view);
        final TreeBuilder rc = createRenderContext();
        component.render(rc);
        return rc.html();
    }

    private static TreeBuilder createRenderContext() {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("0", "0");
        final TreeBuilder rc = new TreeBuilder(qualifiedSessionId,
                                               DOCUMENT_DOM_PATH,
                                               new ComponentContext(),
                                               _ -> new TestCollectingRemoteOut());
        return rc;
    }

    // --- Counting Infrastructure ---

    private record Counts(int tags, int attributes, int textNodes) {}

    private Counts countDefinitions(final Tag tag) {
        int tags = 1; // Count the tag itself
        int attributes = 0;
        int textNodes = 0;

        try {
            final java.lang.reflect.Field childrenField = Tag.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            final Definition[] definitions = (Definition[]) childrenField.get(tag);

            for (final Definition def : definitions) {
                if (def instanceof Tag childTag) {
                    final Counts childCounts = countDefinitions(childTag);
                    tags += childCounts.tags;
                    attributes += childCounts.attributes;
                    textNodes += childCounts.textNodes;
                } else if (def instanceof Attribute) {
                    attributes++;
                } else if (def instanceof Text) {
                    textNodes++;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inspect Tag children via reflection", e);
        }

        return new Counts(tags, attributes, textNodes);
    }

    private Counts countJsoupElements(final Element element) {
        int tags = 1;
        int attributes = element.attributes().size();
        int textNodes = (int) element.textNodes().stream().filter(tn -> !tn.isBlank()).count();

        for (final Element child : element.children()) {
            final Counts childCounts = countJsoupElements(child);
            tags += childCounts.tags;
            attributes += childCounts.attributes;
            textNodes += childCounts.textNodes;
        }
        return new Counts(tags, attributes, textNodes);
    }
}
