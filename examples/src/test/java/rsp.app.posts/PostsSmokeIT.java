package rsp.app.posts;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rsp.http.WebServer;

import java.util.stream.Stream;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end smoke tests for the CrudApp Posts functionality using Playwright.
 * Tests all CRUD operations: Create, Read (List), Update (Edit), Delete (single and bulk).
 */
@net.jcip.annotations.NotThreadSafe
class PostsSmokeIT {

    private static final int PORT = 8085;
    private static final int EXPECTED_PAGE_INIT_TIME_MS = 300;
    private static final int INITIAL_POST_COUNT = 25;
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
    void should_pass_posts_smoke_tests(final BrowserType browserType) throws Exception {
        final Browser browser = browserType.launch();
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();
        System.out.println("Browser type: " + browserType.name());

        login(page);
        validateListView(page);
        validatePagination(page);
        validateGridQuerying(page);
        validateEditPreservesPagination(page);
        validateDirectCreateRoute(page);
        validateNativeFormValidation(page);
        validateUnavailableEdit(page);
        validateCreatePost(page);
        validateEditPost(page);
        validateCancel(page);
        validateDeletePost(page);
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

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify page loaded correctly
        assertOnPostsList(page);

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

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify on first page
        assertTrue(page.url().contains("/posts"));
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

            page.waitForURL(url -> url.contains("p=2"),
                    new Page.WaitForURLOptions().setTimeout(5000));

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

    private void validateEditPreservesPagination(final Page page) throws InterruptedException {
        System.out.println("Testing: Edit preserves the complete grid query");

        String expectedQuery = "p=2&size=10&sort=id&dir=desc&q=Post&filter.title=Title";
        page.navigate(BASE_URL + "/posts?" + expectedQuery);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        assertGridQuery(page, expectedQuery);
        assertThat(primaryScope(page).locator("span:has-text(\"Page 2\")").first()).isVisible();

        // --- Cancel path ---
        clickEditButton(page, 1);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        assertFormVisible(page, "Edit Post");
        cancelForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertGridQuery(page, expectedQuery);
        assertThat(primaryScope(page).locator("span:has-text(\"Page 2\")").first()).isVisible();

        // --- Save path ---
        clickEditButton(page, 1);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        assertFormVisible(page, "Edit Post");
        fillPostForm(page,
                     "P2-Edited-" + System.currentTimeMillis(),
                     "edited while on page 2");
        saveForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertGridQuery(page, expectedQuery);
        assertThat(primaryScope(page).locator("span:has-text(\"Page 2\")").first()).isVisible();

        System.out.println("✓ Edit preserves pagination validated successfully");
    }

    private void validateGridQuerying(final Page page) throws InterruptedException {
        System.out.println("Testing: Grid sorting, search, filters, totals, and page size");

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        Locator scope = primaryScope(page);
        assertEquals(2, scope.locator(".grid-sort-button").count(),
                "Only ID and Title should be sortable");

        scope.locator("th:has-text(\"ID\") .grid-sort-button").click();
        page.waitForURL(url -> url.contains("sort=id") && url.contains("dir=asc"));
        assertEquals("1", scope.locator("tbody tr").first().locator("td").nth(1).textContent().trim());

        scope.locator("th:has-text(\"ID\") .grid-sort-button").click();
        page.waitForURL(url -> url.contains("sort=id") && url.contains("dir=desc"));
        assertEquals("25", scope.locator("tbody tr").first().locator("td").nth(1).textContent().trim());

        scope.locator(".grid-search-field input").fill("Post Title 25");
        scope.locator(".grid-query button[type=submit]").click();
        page.waitForURL(url -> url.contains("q="));
        assertEquals(1, scope.locator("tbody tr").count());
        assertTrue(primaryTableText(page).contains("Post Title 25"));
        assertTrue(scope.locator(".pagination-summary").first().textContent().contains("1–1 of 1"));

        scope.locator(".grid-clear-button").click();
        page.waitForURL(url -> !url.contains("q="));
        scope.locator("input[name=\"filter.title\"]").fill("Title 2");
        scope.locator(".grid-query button[type=submit]").click();
        page.waitForURL(url -> url.contains("filter.title="));
        Locator filteredRows = scope.locator("tbody tr");
        assertTrue(filteredRows.count() > 1);
        for (int i = 0; i < filteredRows.count(); i++) {
            assertTrue(filteredRows.nth(i).locator("td").nth(2).textContent().contains("Title 2"));
        }

        scope.locator(".grid-clear-button").click();
        page.waitForURL(url -> !url.contains("filter.title="));
        scope.locator(".page-size-control select").first().selectOption("25");
        page.waitForURL(url -> url.contains("size=25"));
        assertEquals(25, scope.locator("tbody tr").count());
        assertTrue(scope.locator("button:has-text(\"Next →\")").first().isDisabled());

        System.out.println("✓ Grid querying validated successfully");
    }

    private static void assertGridQuery(Page page, String expectedQuery) {
        for (String parameter : expectedQuery.split("&")) {
            assertTrue(page.url().contains(parameter),
                    () -> "Expected query parameter '" + parameter + "' in " + page.url());
        }
    }

    private void validateDirectCreateRoute(final Page page) throws InterruptedException {
        System.out.println("Testing: Direct Create Route");

        Response response = page.navigate(BASE_URL + "/posts/new");
        assertEquals(200, response.status(), "Should get 200 OK when navigating to /posts/new");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertTrue(page.url().contains("/posts/new"),
                  "Should stay on /posts/new route, but URL is: " + page.url());
        assertFormVisible(page, "Create Post");

        cancelForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        assertOnPostsList(page);
        assertThat(primaryScope(page).locator("table")).isVisible();

        System.out.println("✓ Direct create route validated successfully");
    }

    private void validateCreatePost(final Page page) throws InterruptedException {
        System.out.println("Testing: Create Post");

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Click Create button
        clickCreateButton(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify form appeared
        assertFormVisible(page, "Create Post");

        // Fill the form
        final String testTitle = "Test Post Title " + System.currentTimeMillis();
        final String testContent = "Test Post Content created by automated test";
        fillPostForm(page, testTitle, testContent);

        // A semantic form submits from Enter in a single-line field.
        formScope(page).locator("[name=title]").press("Enter");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify redirected back to list
        assertOnPostsList(page);

        // Navigate to first page to search for the new post
        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Search for the new post across all pages (it may be on page 2 or 3 due to alphabetical sorting)
        boolean found = primaryTableText(page).contains(testTitle);
        int maxPagesToCheck = 5; // Prevent infinite loop
        int pagesChecked = 1;

        while (!found && pagesChecked < maxPagesToCheck) {
            Locator nextButton = primaryScope(page).locator("button:has-text(\"Next →\")").first();
            if (nextButton.isDisabled()) {
                break; // No more pages
            }
            nextButton.click();
            waitFor(EXPECTED_PAGE_INIT_TIME_MS);
            found = primaryTableText(page).contains(testTitle);
            pagesChecked++;
        }

        assertTrue(found, "Post with title '" + testTitle + "' should exist in the list (checked " + pagesChecked + " pages)");

        System.out.println("✓ Create post validated successfully");
    }

    private void validateNativeFormValidation(final Page page) throws InterruptedException {
        System.out.println("Testing: Native form validation and protected fields");

        page.navigate(BASE_URL + "/posts/new");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        Locator form = formScope(page).locator("form.data-form");
        assertThat(form).isVisible();
        assertEquals(0, form.locator("[name=id]").count(), "Hidden IDs must not be submitted by the form");

        saveForm(page);
        assertTrue(page.url().contains("/posts/new"));
        assertTrue((Boolean) form.locator("[name=title]")
                .evaluate("element => element.matches(':invalid')"));

        cancelForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        assertFalse(page.url().contains("/posts/new"));
        System.out.println("✓ Native form validation validated successfully");
    }

    private void validateUnavailableEdit(final Page page) throws InterruptedException {
        System.out.println("Testing: Missing edit entity");

        page.navigate(BASE_URL + "/posts/999999");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        assertThat(formScope(page).locator(".form-message-error")).containsText("not found");
        assertEquals(0, formScope(page).locator("form").count());

        cancelForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        assertFalse(page.url().contains("999999"));
        System.out.println("✓ Missing edit entity validated successfully");
    }

    private void validateEditPost(final Page page) throws InterruptedException {
        System.out.println("Testing: Edit Post");

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Click Edit on first post
        clickEditButton(page, 1);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify edit form appeared
        assertFormVisible(page, "Edit Post");

        // Verify current values are loaded
        assertThat(formScope(page).locator("[name=title]")).not().isEmpty();
        assertThat(formScope(page).locator("[name=content]")).not().isEmpty();

        // Modify the post
        final String updatedTitle = "Updated Post Title " + System.currentTimeMillis();
        final String updatedContent = "Updated content by automated test";
        fillPostForm(page, updatedTitle, updatedContent);

        // Save changes
        saveForm(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnPostsList(page);

        // Navigate to first page and search for updated post across pages
        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Search across all pages for the updated post
        boolean found = primaryTableText(page).contains(updatedTitle);
        int maxPagesToCheck = 5;
        int pagesChecked = 1;

        while (!found && pagesChecked < maxPagesToCheck) {
            Locator nextButton = primaryScope(page).locator("button:has-text(\"Next →\")").first();
            if (nextButton.isDisabled()) {
                break;
            }
            nextButton.click();
            waitFor(EXPECTED_PAGE_INIT_TIME_MS);
            found = primaryTableText(page).contains(updatedTitle);
            pagesChecked++;
        }

        assertTrue(found, "Updated post with title '" + updatedTitle + "' should exist in the list (checked " + pagesChecked + " pages)");

        System.out.println("✓ Edit post validated successfully");
    }

    private void validateCancel(final Page page) throws InterruptedException {
        System.out.println("Testing: Cancel Operations");

        // Test cancel on create
        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        clickCreateButton(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Partially fill form
        page.fill("[name=title]", "This should be cancelled");
        formScope(page).locator("[name=title]").blur();
        waitFor(100);

        // Cancel
        cancelForm(page);
        acceptDiscardDialog(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnPostsList(page);

        // Verify post was not created
        assertFalse(page.content().contains("This should be cancelled"));

        // Test cancel on edit
        clickEditButton(page, 1);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Get original title
        String originalTitle = page.inputValue("[name=title]");

        // Modify
        page.fill("[name=title]", "Modified but cancelled");
        formScope(page).locator("[name=title]").blur();
        waitFor(100);

        // Cancel
        cancelForm(page);
        acceptDiscardDialog(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnPostsList(page);

        // Verify changes were not saved
        assertFalse(page.content().contains("Modified but cancelled"));

        System.out.println("✓ Cancel operations validated successfully");
    }

    private void validateDeletePost(final Page page) throws InterruptedException {
        System.out.println("Testing: Delete Post");

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Get a post title to delete (from the last row to avoid pagination issues)
        // Column order: [checkbox, id, title, content, actions] - so title is at index 2
        Locator lastRow = primaryScope(page).locator("tbody tr").last();
        String titleToDelete = lastRow.locator("td").nth(2).textContent().trim();

        // Click Edit on last post
        lastRow.locator(".edit-button").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Click Delete button
        deletePost(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify back on list
        assertOnPostsList(page);

        // Verify post is gone by checking if the exact title appears in any table cell
        // Use a more specific check than page.content() to avoid false positives from similar titles
        Locator titleCells = primaryScope(page).locator("tbody tr td:nth-child(3)"); // 3rd column is title (1-indexed)
        int count = titleCells.count();
        for (int i = 0; i < count; i++) {
            String cellText = titleCells.nth(i).textContent().trim();
            assertFalse(cellText.equals(titleToDelete),
                       "Deleted post should not appear in list: " + titleToDelete);
        }

        System.out.println("✓ Delete post validated successfully");
    }

    private void validateBulkDelete(final Page page) throws InterruptedException {
        System.out.println("Testing: Bulk Delete");

        navigateToPostsList(page);
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Get titles of first 3 posts to delete (title is at column index 2)
        String title1 = primaryScope(page).locator("tbody tr").nth(0).locator("td").nth(2).textContent().trim();
        String title2 = primaryScope(page).locator("tbody tr").nth(1).locator("td").nth(2).textContent().trim();
        String title3 = primaryScope(page).locator("tbody tr").nth(2).locator("td").nth(2).textContent().trim();

        // Select first 3 posts
        selectRowCheckbox(page, 1);
        selectRowCheckbox(page, 2);
        selectRowCheckbox(page, 3);
        waitFor(100);

        // Verify bulk delete button appears
        Locator bulkDeleteButton = primaryScope(page)
                .locator("button.btn-delete.btn-danger:has-text(\"Delete Selected\")");
        assertThat(bulkDeleteButton).isVisible();

        // Click bulk delete and confirm in the native modal.
        bulkDeleteButton.click();
        confirmDialog(page, "delete selected");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);

        // Verify deleted posts are gone by checking exact matches in title cells
        Locator titleCells = primaryScope(page).locator("tbody tr td:nth-child(3)");
        int count = titleCells.count();
        for (int i = 0; i < count; i++) {
            String cellText = titleCells.nth(i).textContent().trim();
            assertFalse(cellText.equals(title1), "Bulk deleted post 1 should not appear: " + title1);
            assertFalse(cellText.equals(title2), "Bulk deleted post 2 should not appear: " + title2);
            assertFalse(cellText.equals(title3), "Bulk deleted post 3 should not appear: " + title3);
        }

        System.out.println("✓ Bulk delete validated successfully");
    }

    // ========== Helper Methods ==========

    private void login(final Page page) throws InterruptedException {
        page.navigate(BASE_URL + "/posts");
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.locator("button:has-text('Sign in')").click();
        waitFor(EXPECTED_PAGE_INIT_TIME_MS);
        page.waitForURL("**/posts**", new Page.WaitForURLOptions().setTimeout(5000));
    }

    private void navigateToPostsList(final Page page) {
        Response response = page.navigate(BASE_URL + "/posts");
        assertEquals(200, response.status(), "Should get 200 OK when navigating to /posts");
    }

    private void clickCreateButton(final Page page) {
        primaryScope(page).locator("button.create-button").click();
    }

    private void clickEditButton(final Page page, final int rowIndex) {
        primaryScope(page).locator("tbody tr").nth(rowIndex - 1).locator(".edit-button").click();
    }

    private void fillPostForm(final Page page, final String title, final String content) {
        page.fill("[name=title]", title);
        page.fill("[name=content]", content);
    }

    private void saveForm(final Page page) {
        formScope(page).locator("button:has-text(\"Save\")").click();
    }

    private void cancelForm(final Page page) {
        formScope(page).locator("button.cancel-button").click();
    }

    private void acceptDiscardDialog(final Page page) {
        confirmDialog(page, "discard");
    }

    private void deletePost(final Page page) {
        Locator deleteButton = formScope(page).locator("button.btn-delete.btn-danger");
        deleteButton.click();
        confirmDialog(page, "delete");
    }

    private void confirmDialog(final Page page, final String expectedText) {
        Locator dialog = page.locator("dialog.confirmation-dialog[open]");
        assertThat(dialog).isVisible();
        assertTrue(dialog.textContent().toLowerCase().contains(expectedText.toLowerCase()));
        dialog.locator("button.confirmation-dialog-confirm").click();
    }

    private void assertPostExists(final Page page, final String title) {
        assertTrue(primaryTableText(page).contains(title),
                  "Post with title '" + title + "' should exist in the list");
    }

    private void assertOnPostsList(final Page page) {
        assertTrue(page.url().contains("/posts"),
                  "Should be on /posts route, but URL is: " + page.url());
    }

    private void assertFormVisible(final Page page, final String expectedTitle) {
        assertNoModalVisible(page);
        Locator scope = primaryScope(page);
        assertThat(scope.locator("h1:has-text(\"" + expectedTitle + "\")")).isVisible();
        assertThat(scope.locator("form")).isVisible();
    }

    private void assertNoModalVisible(final Page page) {
        Locator modal = page.locator(".modal-content");
        if (modal.count() > 0) {
            assertFalse(modal.first().isVisible(),
                    "Posts forms should render inline in .layout-primary, not in a modal");
        }
    }

    private void selectRowCheckbox(final Page page, final int rowIndex) {
        primaryScope(page).locator("tbody tr").nth(rowIndex - 1).locator("input[type='checkbox']").check();
    }

    private Locator primaryScope(final Page page) {
        return page.locator(".layout-primary");
    }

    private Locator formScope(final Page page) {
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
