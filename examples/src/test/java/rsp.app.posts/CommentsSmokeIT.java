package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.jetty.WebServer;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke tests for the CrudApp Comments functionality using Playwright.
 * Tests all CRUD operations: Create, Read (List), Update (Edit), Delete (single and bulk).
 */
@net.jcip.annotations.NotThreadSafe
class CommentsSmokeIT {

    private static final int PORT = 8085;
    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final int INITIAL_COMMENT_COUNT = 15;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static final Playwright playwright = Playwright.create();
    private static WebServer server;

    @BeforeAll
    public static void init() {
        server = new CrudApp().run(false);
    }

    @AfterAll
    public static void shutdown() throws Exception {
        server.stop();
        Thread.sleep(2000);
    }

    @ParameterizedTest
    @MethodSource("browserTypes")
    void should_pass_comments_smoke_tests(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();
        System.out.println("Browser type: " + browserType.name());

        login(page);
        validateListView(page);
        validatePagination(page);
        validateCreateComment(page);
        validateEditComment(page);
        validateCancel(page);
        validateDeleteComment(page);
        validateBulkDelete(page);
    }

    private static Stream<BrowserType> browserTypes() {
        return Stream.of(playwright.chromium()
                   //      playwright.webkit(),
                   //      playwright.firefox()
                );
    }

    // ========== Test Scenarios ==========

    private void validateListView(final Page page) throws InterruptedException {
        System.out.println("Testing: List View");

        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify page loaded correctly
        assertOnCommentsList(page);

        // Verify table is visible
        assertThat(primaryScope(page).locator("table")).isVisible();

        // Verify Create button is present
        assertThat(primaryScope(page).locator("button.create-button")).isVisible();

        // Verify pagination indicator (use first() to avoid strict mode violation with multiple pagination controls)
        assertThat(primaryScope(page).locator("span:has-text(\"Page\")").first()).isVisible();

        System.out.println("✓ List view validated successfully");
    }

    private void validatePagination(final Page page) throws InterruptedException {
        System.out.println("Testing: Pagination");

        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify on first page
        assertTrue(page.url().contains("/comments"));
        assertThat(primaryScope(page).locator("span:has-text(\"Page 1\")").first()).isVisible();

        // Verify Previous button is disabled on first page (use first() for duplicate pagination controls)
        Locator previousButton = primaryScope(page).locator("button:has-text(\"← Previous\")").first();
        assertTrue(previousButton.isDisabled());

        // Click Next button (use first() for duplicate pagination controls)
        Locator nextButton = primaryScope(page).locator("button:has-text(\"Next →\")").first();
        if (!nextButton.isDisabled()) {
            String beforeUrl = page.url();
            System.out.println("Before Next click, URL: " + beforeUrl);
            nextButton.click();

            // Wait for page indicator to change to Page 2 (with timeout)
            try {
                primaryScope(page).locator("span:has-text(\"Page 2\")").first()
                        .waitFor(new Locator.WaitForOptions().setTimeout(5000));
            } catch (Exception e) {
                System.out.println("Timeout waiting for Page 2 indicator. URL: " + page.url());
            }

            String afterUrl = page.url();
            System.out.println("After Next click, URL: " + afterUrl);

            // Verify we're on page 2
            assertThat(primaryScope(page).locator("span:has-text(\"Page 2\")").first()).isVisible();

            // Click Previous to go back
            previousButton.click();
            primaryScope(page).locator("span:has-text(\"Page 1\")").first()
                    .waitFor(new Locator.WaitForOptions().setTimeout(5000));

            // Verify back on page 1
            assertThat(primaryScope(page).locator("span:has-text(\"Page 1\")").first()).isVisible();
        }

        System.out.println("✓ Pagination validated successfully");
    }

    private void validateCreateComment(final Page page) throws InterruptedException {
        System.out.println("Testing: Create Comment");

        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Click Create button
        clickCreateButton(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify form appeared
        assertFormVisible(page, "Create Comment");

        // Fill the form
        final String testText = "Test Comment " + System.currentTimeMillis();
        final String testPostId = "999";
        fillCommentForm(page, testText, testPostId);

        // Save the form
        saveForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify redirected back to list
        assertOnCommentsList(page);

        // Navigate to first page to search for the new comment
        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Search for the new comment across all pages (it may be on page 2 due to alphabetical sorting)
        boolean found = primaryTableText(page).contains(testText);
        int maxPagesToCheck = 5; // Prevent infinite loop
        int pagesChecked = 1;

        while (!found && pagesChecked < maxPagesToCheck) {
            Locator nextButton = primaryScope(page).locator("button:has-text(\"Next →\")").first();
            if (nextButton.isDisabled()) {
                break; // No more pages
            }
            nextButton.click();
            waitFor(EXPECTED_PAGE_INIT_TIME_MS);
            found = primaryTableText(page).contains(testText);
            pagesChecked++;
        }

        assertTrue(found, "Comment with text '" + testText + "' should exist in the list (checked " + pagesChecked + " pages)");

        System.out.println("✓ Create comment validated successfully");
    }

    private void validateEditComment(final Page page) throws InterruptedException {
        System.out.println("Testing: Edit Comment");

        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Click Edit on first comment
        clickEditButton(page, 1);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify edit form appeared
        assertFormVisible(page, "Edit Comment");

        // Verify current values are loaded
        assertThat(formScope(page).locator("#text")).not().isEmpty();
        assertThat(formScope(page).locator("#postId")).not().isEmpty();

        // Modify the comment
        final String updatedText = "Updated Comment Text " + System.currentTimeMillis();
        final String updatedPostId = "888";
        fillCommentForm(page, updatedText, updatedPostId);

        // Save changes
        saveForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnCommentsList(page);

        // Navigate to first page and search for updated comment across pages
        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Search across all pages for the updated comment
        boolean found = primaryTableText(page).contains(updatedText);
        int maxPagesToCheck = 5;
        int pagesChecked = 1;

        while (!found && pagesChecked < maxPagesToCheck) {
            Locator nextButton = primaryScope(page).locator("button:has-text(\"Next →\")").first();
            if (nextButton.isDisabled()) {
                break;
            }
            nextButton.click();
            waitFor(EXPECTED_PAGE_INIT_TIME_MS);
            found = primaryTableText(page).contains(updatedText);
            pagesChecked++;
        }

        assertTrue(found, "Updated comment with text '" + updatedText + "' should exist in the list (checked " + pagesChecked + " pages)");

        System.out.println("✓ Edit comment validated successfully");
    }

    private void validateCancel(final Page page) throws InterruptedException {
        System.out.println("Testing: Cancel Operations");

        // Test cancel on create
        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        clickCreateButton(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Partially fill form
        page.fill("#text", "This should be cancelled");

        // Cancel
        cancelForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnCommentsList(page);

        // Verify comment was not created
        assertFalse(page.content().contains("This should be cancelled"));

        // Test cancel on edit
        clickEditButton(page, 1);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Get original text
        String originalText = page.inputValue("#text");

        // Modify
        page.fill("#text", "Modified but cancelled");

        // Cancel
        cancelForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnCommentsList(page);

        // Verify changes were not saved
        assertFalse(page.content().contains("Modified but cancelled"));

        System.out.println("✓ Cancel operations validated successfully");
    }

    private void validateDeleteComment(final Page page) throws InterruptedException {
        System.out.println("Testing: Delete Comment");

        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Get a comment text to delete (from the last row to avoid pagination issues)
        // Column order: [checkbox, id, text, postId, actions] - so text is at index 2
        Locator lastRow = primaryScope(page).locator("tbody tr").last();
        String textToDelete = lastRow.locator("td").nth(2).textContent().trim();

        // Click Edit on last comment
        lastRow.locator("button.edit-button").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Setup dialog handler before clicking delete
        page.onDialog(dialog -> {
            System.out.println("Dialog message: " + dialog.message());
            assertTrue(dialog.message().toLowerCase().contains("sure") ||
                      dialog.message().toLowerCase().contains("delete"));
            dialog.accept();
        });

        // Click Delete button
        deleteComment(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnCommentsList(page);

        // Verify comment is gone by checking if the exact text appears in any table cell
        // Use a more specific check than page.content() to avoid false positives from similar text
        Locator textCells = primaryScope(page).locator("tbody tr td:nth-child(3)"); // 3rd column is text (1-indexed)
        int count = textCells.count();
        for (int i = 0; i < count; i++) {
            String cellText = textCells.nth(i).textContent().trim();
            assertFalse(cellText.equals(textToDelete),
                       "Deleted comment should not appear in list: " + textToDelete);
        }

        System.out.println("✓ Delete comment validated successfully");
    }

    private void validateBulkDelete(final Page page) throws InterruptedException {
        System.out.println("Testing: Bulk Delete");

        navigateToCommentsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Get text of first 3 comments to delete (text is at column index 2)
        String text1 = primaryScope(page).locator("tbody tr").nth(0).locator("td").nth(2).textContent().trim();
        String text2 = primaryScope(page).locator("tbody tr").nth(1).locator("td").nth(2).textContent().trim();
        String text3 = primaryScope(page).locator("tbody tr").nth(2).locator("td").nth(2).textContent().trim();

        // Select first 3 comments
        selectRowCheckbox(page, 1);
        selectRowCheckbox(page, 2);
        selectRowCheckbox(page, 3);
        waitFor(100);

        // Verify bulk delete button appears
        Locator bulkDeleteButton = primaryScope(page)
                .locator("button.btn-delete.btn-danger:has-text(\"Delete Selected\")");
        assertThat(bulkDeleteButton).isVisible();

        // Click bulk delete (dialog handler from previous test should still be active)
        bulkDeleteButton.click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify deleted comments are gone by checking exact matches in text cells
        Locator textCells = primaryScope(page).locator("tbody tr td:nth-child(3)");
        int count = textCells.count();
        for (int i = 0; i < count; i++) {
            String cellText = textCells.nth(i).textContent().trim();
            assertFalse(cellText.equals(text1), "Bulk deleted comment 1 should not appear: " + text1);
            assertFalse(cellText.equals(text2), "Bulk deleted comment 2 should not appear: " + text2);
            assertFalse(cellText.equals(text3), "Bulk deleted comment 3 should not appear: " + text3);
        }

        System.out.println("✓ Bulk delete validated successfully");
    }

    // ========== Helper Methods ==========

    private void login(final Page page) throws InterruptedException {
        page.navigate(BASE_URL + "/comments");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.locator("button:has-text('Sign in')").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.waitForURL("**/comments**", new Page.WaitForURLOptions().setTimeout(5000));
    }

    private void navigateToCommentsList(final Page page) {
        Response response = page.navigate(BASE_URL + "/comments");
        assertEquals(200, response.status(), "Should get 200 OK when navigating to /comments");
    }

    private void clickCreateButton(final Page page) {
        primaryScope(page).locator("button.create-button").click();
    }

    private void clickEditButton(final Page page, final int rowIndex) {
        primaryScope(page).locator("tbody tr").nth(rowIndex - 1).locator("button.edit-button").click();
    }

    private void fillCommentForm(final Page page, final String text, final String postId) {
        page.fill("#text", text);
        page.fill("#postId", postId);
    }

    private void saveForm(final Page page) {
        formScope(page).locator("button:has-text(\"Save\")").click();
    }

    private void cancelForm(final Page page) {
        formScope(page).locator("button.cancel-button").click();
    }

    private void deleteComment(final Page page) {
        Locator deleteButton = formScope(page).locator("button.btn-delete.btn-danger");
        deleteButton.click();
    }

    private void assertOnCommentsList(final Page page) {
        assertTrue(page.url().contains("/comments"),
                  "Should be on /comments route, but URL is: " + page.url());
    }

    private void assertFormVisible(final Page page, final String expectedTitle) {
        Locator scope = formScope(page);
        assertThat(scope.locator("h1:has-text(\"" + expectedTitle + "\")")).isVisible();
        assertThat(scope.locator("form")).isVisible();
    }

    private void selectRowCheckbox(final Page page, final int rowIndex) {
        primaryScope(page).locator("tbody tr").nth(rowIndex - 1).locator("input[type='checkbox']").check();
    }

    private Locator primaryScope(final Page page) {
        return page.locator(".layout-primary");
    }

    private Locator formScope(final Page page) {
        Locator modal = page.locator(".modal-content");
        if (modal.count() > 0 && modal.first().isVisible()) {
            return modal.first();
        }
        return primaryScope(page);
    }

    private String primaryTableText(final Page page) {
        Locator tbody = primaryScope(page).locator("tbody");
        String text = tbody.textContent();
        return text != null ? text : "";
    }

    private static void waitFor(final long timeMs) throws InterruptedException {
        Thread.sleep(timeMs);
    }
}
