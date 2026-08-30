package rsp.compositions.block;

import org.junit.jupiter.api.Test;
import rsp.compositions.application.TestLookup;
import rsp.server.http.RelativeUrl;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteUtilsTests {

    @Test
    void parent_route_restores_the_complete_encoded_list_query() {
        TestLookup lookup = new TestLookup().withData(ContextKeys.URL_QUERY.with("fromQuery"),
                "p=3&size=25&sort=title&dir=desc&q=hello+world&filter.title=hello");

        RelativeUrl parent = RouteUtils.buildParentRoute("/posts/:id", lookup);

        assertEquals("/posts?p=3&size=25&sort=title&dir=desc&q=hello+world&filter.title=hello",
                parent.toString());
    }

    @Test
    void legacy_return_parameters_remain_supported() {
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.URL_QUERY.with("fromP"), "2")
                .withData(ContextKeys.URL_QUERY.with("fromSort"), "desc");

        assertEquals("/posts?p=2&sort=desc",
                RouteUtils.buildParentRoute("/posts/new", lookup).toString());
    }
}
