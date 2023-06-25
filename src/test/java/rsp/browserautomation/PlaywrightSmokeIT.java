package rsp.browserautomation;

import com.microsoft.playwright.*;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@net.jcip.annotations.NotThreadSafe
public class PlaywrightSmokeIT {

    private static final int EXPECTED_RESPONSE_TIME_MS = 300;
    private static final int COUNTER_1_INITIAL_VALUE = 100;
    private static final int COUNTER_2_INITIAL_VALUE = 1001;


    private static SimpleServer server;

    @BeforeClass
    public static void init() {
        server = SimpleServer.run(false);
    }

    @Test
    public void should_pass_smoke_tests() throws Exception {
        final Playwright playwright = Playwright.create();
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
        playwright.close();
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
        validateComponent1(page);
        validateComponent2(page);
    }

    private void validateComponent1(Page page) throws InterruptedException {
        assertElementTextEquals(page, "c1_s0", Integer.toString(COUNTER_1_INITIAL_VALUE));
        assertElementStyleAttributeEquals(page, "c1_s0",  "background-color", expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE));
        waitForPageResponse();

        clickOnElement(page,"c1_b0");
        waitForPageResponse();
        assertElementTextEquals(page,"c1_s0", Integer.toString(COUNTER_1_INITIAL_VALUE + 1));
        assertElementStyleAttributeEquals(page, "c2_s0", "background-color", expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE + 1));

        clickOnElement(page,"c1_b0");
        waitForPageResponse();
        assertElementTextEquals(page,"c1_s0", Integer.toString(COUNTER_1_INITIAL_VALUE + 2));
        assertElementStyleAttributeEquals(page, "c1_s0","background-color", expectedColorAttributeValue(COUNTER_1_INITIAL_VALUE + 2));
    }

    private void validateComponent2(Page page) throws InterruptedException {
        assertElementTextEquals(page, "c2_s0", Integer.toString(COUNTER_2_INITIAL_VALUE));
        assertElementStyleAttributeEquals(page, "c2_s0",  "background-color", expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE));
        waitForPageResponse();

        clickOnElement(page,"c2_b0");
        waitForPageResponse();
        assertElementTextEquals(page,"c2_s0", Integer.toString(COUNTER_2_INITIAL_VALUE + 1));
        assertElementStyleAttributeEquals(page, "c2_s0", "background-color", expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE + 1));

        clickOnElement(page,"c2_b0");
        waitForPageResponse();
        assertElementTextEquals(page,"c2_s0", Integer.toString(COUNTER_2_INITIAL_VALUE + 2));
        assertElementStyleAttributeEquals(page, "c2_s0","background-color", expectedColorAttributeValue(COUNTER_2_INITIAL_VALUE + 2));
    }

    private static String expectedColorAttributeValue(int value) {
        return value % 2 == 0 ? "red" : "blue";
    }

    private void clickOnElement(final Page page, final String elementId) {
        page.click("#" + elementId);
    }

    private static void assertElementTextEquals(final Page page, final String elementId, final String expectedValue) {
        Assert.assertEquals(expectedValue, page.innerText("#" + elementId));
    }

    private static void assertElementStyleAttributeEquals(final Page page, final String elementId, final String styleName, final String expectedValue) {
        final Optional<String> s = BrowserUtils.style(page.getAttribute("#" + elementId, "style"), styleName);
        s.ifPresentOrElse(v -> Assert.assertEquals(expectedValue, v), () -> Assert.fail());
    }

    private static void waitForPageResponse() throws InterruptedException {
        Thread.sleep(EXPECTED_RESPONSE_TIME_MS);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        server.jetty.stop();
        Thread.sleep(2000);
    }
}
