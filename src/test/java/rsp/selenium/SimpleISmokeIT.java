package rsp.selenium;

import com.microsoft.playwright.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SimpleISmokeIT {

    @Test
    public void should_pass_smoke_tests() throws Exception {
        new TestServer().run(false);

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
            page.navigate("http://localhost:" + TestServer.PORT);
            //page.screenshot(new Page.ScreenshotOptions().withPath(Paths.get("screenshot-" + browserType.name() + ".png")));
            validatePage(page);
        }
        playwright.close();
    }

    private void validatePage(Page page) throws InterruptedException {
        Assert.assertEquals("test-server-title", page.title());

        assertCounterTextEquals(page,"-1");
        assertCounterStyleAttributeEquals(page, "background-color:blue;");
        waitForServer();

        page.click("button#b0");
        waitForServer();
        assertCounterTextEquals(page,"0");
        assertCounterStyleAttributeEquals(page, "background-color: red;");

        page.click("div#d0");
        waitForServer();
        assertCounterTextEquals(page,"10");
        assertCounterStyleAttributeEquals(page, "background-color: red;");
    }

    private static void assertCounterTextEquals(Page page, String expectedValue) {
        Assert.assertEquals(expectedValue, page.innerText("span#s0"));
    }

    private static void assertCounterStyleAttributeEquals(Page page, String expectedValue) {
        Assert.assertEquals(expectedValue, page.getAttribute("span#s0", "style"));
    }

    private static void waitForServer() throws InterruptedException {
        Thread.sleep(300);
    }
}
