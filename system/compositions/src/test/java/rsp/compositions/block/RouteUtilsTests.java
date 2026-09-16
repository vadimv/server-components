package rsp.compositions.block;

import org.junit.jupiter.api.Test;
import rsp.compositions.application.TestLookup;
import rsp.url.RelativeUrl;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteUtilsTests {

    @Test
    void parent_route_restores_the_complete_encoded_list_query() {
        TestLookup lookup = new TestLookup().withData(ContextKeys.URL_QUERY.with("fromQuery"),
                "p=3&size=25&sort=title&dir=desc&q=hello+world&filter.title=hello");

        RelativeUrl parent = RouteUtils.buildParentRoute("/posts/{id}", lookup);

        assertEquals("/posts?p=3&size=25&sort=title&dir=desc&q=hello+world&filter.title=hello",
                parent.toString());
    }

    @Test
    void parent_route_strips_create_tokens_without_a_return_query() {
        assertEquals("/posts", RouteUtils.buildParentRoute("/posts/new", new TestLookup()).toString());
    }

}
