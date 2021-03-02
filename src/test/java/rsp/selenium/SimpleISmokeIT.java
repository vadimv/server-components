package rsp.selenium;

import com.microsoft.playwright.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SimpleISmokeIT {

    @Test
    public void should_pass_smoke_tests() throws Exception {
        TestServer.run(false);

        final Playwright playwright = Playwright.create();
        final List<BrowserType> browserTypes = Arrays.asList(
                playwright.chromium(),
                playwright.webkit(),
                playwright.firefox()
        );
        for (BrowserType browserType : browserTypes) {
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
        System.out.println(page.innerText("span#s0"));
        System.out.println(page.getAttribute("span#s0", "style"));

        page.click("button#b0");
        waitForServer();
        System.out.println(page.innerText("span#s0"));
        System.out.println(page.getAttribute("span#s0", "style"));

        page.click("div#d0");
        waitForServer();
        System.out.println(page.innerText("span#s0"));
        System.out.println(page.getAttribute("span#s0", "style"));
    }

    private static void waitForServer() throws InterruptedException {
        Thread.sleep(100);
    }
}
