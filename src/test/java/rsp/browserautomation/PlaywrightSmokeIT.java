package rsp.browserautomation;

import com.microsoft.playwright.*;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@net.jcip.annotations.NotThreadSafe
public class PlaywrightSmokeIT {

    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final int COUNTER_1_INITIAL_VALUE = 100;
    private static final int COUNTER_2_INITIAL_VALUE = 1001;


    private static SimpleServer server;

    @BeforeClass
    public static void init() {
        server = SimpleServer.run(false);
    }

    @Test
    public void should_pass_smoke_tests() throws Exception {
        try(final Playwright playwright = Playwright.create()) {

        final List<BrowserType> browserTypes = Arrays.asList(
                playwright.chromium(),
                playwright.webkit(),
                playwright.firefox()
        );

        for (final BrowserType browserType : browserTypes) {
               final Browser browser = browserType.launch();
               final BrowserContext context = browser.newContext();
               final Page page = context.newPage();
               System.out.println("Browser type: " + browserType.name());
               validatePageNotFound(page);
               validatePage(page);
           }
        }
    }

    private void validatePageNotFound(final Page page) {
        Assert.assertEquals(404, page.navigate("http://localhost:" + SimpleServer.PORT + "/none").status());
    }

    private void validatePage(final Page page) throws InterruptedException {
        Assert.assertEquals(200, page.navigate("http://localhost:"
                                                        + SimpleServer.PORT
                                                        + "/" + COUNTER_1_INITIAL_VALUE
                                                        +"/" + COUNTER_2_INITIAL_VALUE).status());
        Assert.assertEquals("test-server-title", page.title());
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        validateComponent1(page);
        validateComponent2(page);
    }

    private void validateComponent1(Page page) {
        assertThat(page.locator("#c1_s0")).hasText(Integer.toString(COUNTER_1_INITIAL_VALUE));
        assertThat(page.locator("#c1_s0")).hasClass(expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE));

        clickOnElement(page,"c1_b0");
        assertThat(page.locator("#c1_s0")).hasText(Integer.toString(COUNTER_1_INITIAL_VALUE + 1));
        assertThat(page.locator("#c1_s0")).hasClass(expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE + 1));

        clickOnElement(page,"c1_b0");
        assertThat(page.locator("#c1_s0")).hasText(Integer.toString(COUNTER_1_INITIAL_VALUE + 2));
        assertThat(page.locator("#c1_s0")).hasClass(expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE + 2));
    }

    private void validateComponent2(Page page) {
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

    private static String expectedColorAttributeValue(int value) {
        return value % 2 == 0 ? "red" : "blue";
    }

    private void clickOnElement(final Page page, final String elementId) {
        page.click("#" + elementId);
    }

    private static boolean hasStyle(final Locator element, final String styleName, final String expectedValue) throws InterruptedException {
        int i = 0;
        while(i < 10) {
            final Optional<String> s = BrowserUtils.style(element.getAttribute("style"), styleName);
            if (s.isPresent() && s.get().equals(expectedValue)) return true;
            Thread.sleep(100);
        }
        return false;
    }

    private static void waitFor(long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        server.jetty.stop();
        Thread.sleep(2000);
    }
}
