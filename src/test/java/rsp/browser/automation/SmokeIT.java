package rsp.browser.automation;

import com.microsoft.playwright.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@net.jcip.annotations.NotThreadSafe
public class SmokeIT {

    private static final int EXPECTED_RESPONSE_TIME_MS = 300;

    @Test
    public void should_pass_smoke_tests() throws Exception {
        new SimpleServer().run(false);

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
            page.navigate("http://localhost:" + SimpleServer.PORT);
            validatePage(page);
        }
        playwright.close();
    }

    private void validatePage(Page page) throws InterruptedException {
        Assert.assertEquals("test-server-title", page.title());

        assertCounterTextEquals(page,"-1");
        assertCounterStyleAttributeEquals(page, "blue");
        waitForServer();

        page.click("button#b0");
        waitForServer();
        assertCounterTextEquals(page,"0");
        assertCounterStyleAttributeEquals(page, "red");

        page.click("div#d0");
        waitForServer();
        assertCounterTextEquals(page,"10");
        assertCounterStyleAttributeEquals(page, "red");
    }

    private static void assertCounterTextEquals(Page page, String expectedValue) {
        Assert.assertEquals(expectedValue, page.innerText("span#s0"));
    }

    private static void assertCounterStyleAttributeEquals(Page page,  String expectedValue) {
        final Optional<String> s = style(page.getAttribute("span#s0", "style"),
                                      "background-color");
        s.ifPresentOrElse(v -> Assert.assertEquals(expectedValue, v), () -> Assert.fail());
    }

    private static Optional<String> style(String stylesAttributeValue, String styleName) {
        final String[] styles = stylesAttributeValue.split(";");
        for (String style : styles) {
            final String[] styleTokens = style.split(":");
            if (styleName.equals(styleTokens[0].trim())) {
                return Optional.of(styleTokens[1].trim());
            }
        }
        return Optional.empty();
    }

    private static void waitForServer() throws InterruptedException {
        Thread.sleep(EXPECTED_RESPONSE_TIME_MS);
    }
}
