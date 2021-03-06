package rsp.browserautomation;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

@net.jcip.annotations.NotThreadSafe
public class SeleniumSmokeIT {
    private static final int EXPECTED_RESPONSE_TIME_MS = 300;

    private static SimpleServer server;
    private static WebDriver driver;

    @BeforeClass
    public static void init() throws Exception {
        server = SimpleServer.run(false);
    }

    @Test
    public void should_pass_smoke_tests() throws Exception {
        WebDriverManager.chromedriver().setup();

        final ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);
        driver = new ChromeDriver(options);

        validatePage(driver);
    }

    private void validatePage(WebDriver driver) throws InterruptedException {
        driver.navigate().to("http://localhost:" + SimpleServer.PORT);
        Assert.assertEquals("test-server-title", driver.getTitle());

        assertElementTextEquals("s0", "-1");
        assertElementStyleAttributeEquals("s0",  "background-color","blue");
        waitForPageResponse();

        clickOnElement("b0");
        waitForPageResponse();
        assertElementTextEquals("s0", "0");
        assertElementStyleAttributeEquals("s0",  "background-color","red");

        clickOnElement("d0");
        waitForPageResponse();
        assertElementTextEquals("s0", "10");
        assertElementStyleAttributeEquals("s0",  "background-color","red");
    }

    private void clickOnElement(String elementId) {
        driver.findElement(By.id(elementId)).click();
    }

    private void assertElementTextEquals(String elementId, String expectedValue) {
        Assert.assertEquals(expectedValue, driver.findElement(By.id(elementId)).getText());
    }

    private void assertElementStyleAttributeEquals(String elementId, String styleName, String expectedValue) {
        BrowserUtils.style(driver.findElement(By.id(elementId)).getAttribute("style"), styleName)
                .ifPresentOrElse(s -> Assert.assertEquals(expectedValue, s), () -> Assert.fail());
    }

    public static void waitForPageResponse() throws InterruptedException {
        Thread.sleep(EXPECTED_RESPONSE_TIME_MS);
    }

    @AfterClass
    public static void shutdown() throws Exception {
        driver.close();
        server.jetty.stop();
        Thread.sleep(2000);
    }
}
