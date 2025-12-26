package rsp.app.counters;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An end-to-end smoke tests running the CounterApp on a web server and browser automation with Playwright.
 * @see CountersApp
 */
@net.jcip.annotations.NotThreadSafe
class CountersAppSmokeIT {

    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final int COUNTER_1_INITIAL_VALUE = 100;
    private static final int COUNTER_2_INITIAL_VALUE = 1001;

    private static final Playwright playwright = Playwright.create();

    private static CountersApp server;

    @BeforeAll
    public static void init() {
        server = CountersApp.run(false);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_pass_smoke_tests(final BrowserType browserType) throws Exception {
       final Browser browser = browserType.launch();
       final BrowserContext context = browser.newContext();
       final Page page = context.newPage();
       System.out.println("Browser type: " + browserType.name());
       validatePageNotFound(page);
       validatePage(page);
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium()
                   //      playwright.webkit()
                   //      playwright.firefox()
                );
    }

    private void validatePageNotFound(final Page page) {
        assertEquals(404, page.navigate("http://localhost:" + CountersApp.PORT + "/none").status());
    }

    private void validatePage(final Page page) throws InterruptedException {
        assertEquals(200, page.navigate("http://localhost:"
                                                        + CountersApp.PORT
                                                        + "/" + COUNTER_1_INITIAL_VALUE
                                                        +"/" + COUNTER_2_INITIAL_VALUE).status());
        assertEquals("Counters", page.title());
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        validateComponent1(page);
        validateComponent2(page);
    }

    private void validateComponent1(final Page page) {
        assertThat(page.locator("#c1_s0")).hasText(Integer.toString(COUNTER_1_INITIAL_VALUE));
        assertThat(page.locator("#c1_s0")).hasClass(expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE));

        clickOnElement(page,"c1_b0");
        assertThat(page.locator("#c1_s0")).hasText(Integer.toString(COUNTER_1_INITIAL_VALUE + 1));
        assertThat(page.locator("#c1_s0")).hasClass(expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE + 1));

        clickOnElement(page,"c1_b0");
        assertThat(page.locator("#c1_s0")).hasText(Integer.toString(COUNTER_1_INITIAL_VALUE + 2));
        assertThat(page.locator("#c1_s0")).hasClass(expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE + 2));
    }

    private void validateComponent2(final Page page) {
        assertThat(page.locator("#c2_s0")).hasText(Integer.toString(COUNTER_2_INITIAL_VALUE));
        assertThat(page.locator("#c2_s0")).hasClass(expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE));

        clickOnElement(page,"c2_b0");
        assertThat(page.locator("#c2_s0")).hasText(Integer.toString(COUNTER_2_INITIAL_VALUE + 1));
        assertThat(page.locator("#c2_s0")).hasClass(expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE + 1));

        clickOnElement(page,"c2_b0");
        assertThat(page.locator("#c2_s0")).hasText(Integer.toString(COUNTER_2_INITIAL_VALUE + 2));
        assertThat(page.locator("#c2_s0")).hasClass(expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE + 2));

        page.goBack();
        assertThat(page.locator("#c2_s0")).hasText(Integer.toString(COUNTER_2_INITIAL_VALUE + 1));
        assertThat(page.locator("#c2_s0")).hasClass(expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE + 1));

        page.goForward();
        assertThat(page.locator("#c2_s0")).hasText(Integer.toString(COUNTER_2_INITIAL_VALUE + 2));
        assertThat(page.locator("#c2_s0")).hasClass(expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE + 2));

    }

    private static String expectedColorAttributeValue(final int value) {
        return value % 2 == 0 ? "red" : "blue";
    }

    private void clickOnElement(final Page page, final String elementId) {
        page.click("#" + elementId);
    }

    private static void waitFor(final long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }

    @AfterAll
    public static void shutdown() throws Exception {
        server.webServer.stop();
        Thread.sleep(2000);
    }
}
