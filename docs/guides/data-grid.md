# Schema-Driven Data Grids

`ListBlock<T>` and `DefaultListView` provide the standard server-driven data
grid. The block owns query and mutation behavior; the view renders only what
the block exposes and dispatches typed intents. The result is a grid whose URL,
visible rows, totals, selection, and edit-return target stay consistent.

## Define The Contract

Declare the schema independently of loaded rows. This keeps headers and query
controls present when the result is empty or a load fails.

```java
private static final DataSchema SCHEMA = DataSchema.builder()
        .field("id", FieldType.ID).label("ID")
        .field("title", FieldType.STRING).label("Title")
        .field("content", FieldType.TEXT).label("Content")
        .column("id").sortable().width("6rem").align(TextAlign.RIGHT)
        .column("title").sortable().filterable().width("30%")
        .column("content").filterable().width("auto")
        .build()
        .withSelectable(true);
```

Only configured columns are displayed, in configuration order. Only columns
marked `sortable` or `filterable` receive the corresponding controls. An
explicit column may display a field such as `FieldType.ID` even though that
field's default form widget is hidden. A field explicitly marked `.hidden()`
stays out of the list. A formatter is display-only; services still sort and
filter the underlying value.

## Implement The Block

```java
public final class PostsListBlock extends ListBlock<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private final PostService posts;

    public PostsListBlock(PostService posts,
                          ComponentView<ListView.ListViewState, ListView.ListIntent> view) {
        super(view);
        this.posts = posts;
    }

    @Override protected QueryParam<Integer> pageQueryParam() { return PAGE; }
    @Override protected DataSchema listSchema() { return SCHEMA; }
    @Override protected SortSpec defaultSort() {
        return new SortSpec("title", SortDirection.ASC);
    }
    @Override protected ListPage<Post> items(ListQuery query) {
        return posts.findAll(query);
    }
    @Override protected DeleteResult bulkDelete(Set<String> ids) {
        return posts.deleteAll(ids);
    }
    @Override protected Class<? extends Block<?, ?>> createElementBlock() {
        return PostCreateBlock.class;
    }
    @Override protected Class<? extends Block<?, ?>> editElementBlock() {
        return PostEditBlock.class;
    }
}
```

Override `rowKey()` when records do not use `id`. Override `canCreate()`,
`canEdit()`, or `canDelete()` to remove unavailable controls and reject their
intents at the block boundary. Selection requires `withSelectable(true)` and
valid, unique row-key values on each loaded page.

## Implement The Query Once

`items(ListQuery)` receives normalized state:

- `page` is one-based and positive;
- `pageSize` is between 1 and 100;
- `sort` names a sortable schema column or uses the block default;
- `search` is trimmed;
- `filters` contains only nonblank values for filterable columns.

Apply search and filters first, then sorting, then pagination. Return the exact
matching total, not the size of the current page and not a guessed `hasNext`.
Use a deterministic tie-breaker such as the row ID so moving between pages
does not duplicate or skip equal values.

```java
public ListPage<Post> findAll(ListQuery query) {
    List<Post> matching = posts.stream()
            .filter(post -> matchesSearch(post, query.search()))
            .filter(post -> matchesFilters(post, query.filters()))
            .sorted(comparatorFor(query.sort()).thenComparing(Post::id))
            .toList();

    long offset = (long) (query.page() - 1) * query.pageSize();
    int from = (int) Math.min(offset, matching.size());
    int to = Math.min(from + query.pageSize(), matching.size());
    return new ListPage<>(matching.subList(from, to), matching.size());
}
```

Whitelist sort fields in the persistence layer too. Schema validation protects
the standard UI path, but the service remains responsible for translating a
field name into a safe comparator or database expression.

## URL And Navigation

The standard query parameters are:

| Parameter | Meaning |
| --- | --- |
| `p` | One-based page; omitted for page 1 |
| `size` | Rows per page |
| `sort` | Sort field name |
| `dir` | `asc` or `desc` |
| `q` | Global search text |
| `filter.<field>` | Per-column filter value |

Sort, search, filters, and page-size changes reset to page 1. Related query
changes are pushed atomically as one history entry, and blank values remove
parameters. Browser back/forward reloads the block from URL context. Edit links
encode the complete current query in `fromQuery`; the form's parent return
restores it after save or cancel. The older `fromP` and `fromSort` parameters
remain accepted for existing links.

## Pagination, Empty Results, And Failures

The exact total drives the visible range, page count, and disabled state of
first/previous/next/last controls. If the URL requests a page beyond the last
matching page, the block reloads the last valid page. A zero-result query stays
on page 1 and retains its schema.

The view distinguishes an unconstrained empty list, an empty search/filter
result, and a load failure. Delete operations return `DeleteResult`, allowing
the block to report complete success, complete failure, or partial success. It
must partition every requested ID between `deletedIds` and `failedIds`, without
reporting unrelated IDs. The block then reloads the current query, clears
selection, and moves to the preceding valid page when deletion empties the last
page.

## Accessibility And Responsive Layout

`DefaultListView` supplies a caption, scoped column headers, `aria-sort`, named
pagination regions, labeled selection controls, live result summaries, and
status/alert feedback. Non-sortable headers are plain text. The table is kept
as a semantic table and placed in an overflow container on narrow screens.

The view emits structural class names but does not ship an application theme.
Use the CrudApp rules in
[style.css](../../examples/src/main/java/rsp/app/posts/style.css) as a starting
point for query controls, long-cell wrapping, pagination, messages, focus
states, and the narrow-screen layout.

## Testing Checklist

Cover the contract at three levels:

1. Service tests: every allowed sort direction, search/filter combinations,
   exact totals, stable page boundaries, and partial delete results.
2. View tests: schema column order, sort metadata, formatters, empty/error
   states, action capabilities, and correctly disabled pagination.
3. Mounted block or browser tests: URL synchronization, back/forward, query
   reset to page 1, selection clearing, out-of-range page clamping, and return
   from edit with the full query restored.

The Posts and Comments services and blocks in CrudApp are the executable
reference implementation. Their list and form blocks share one schema; see the
[create/edit forms guide](data-forms.md) for that side of the workflow.

## Migrating An Existing List Block

The grid contract replaces direction-only `sort(Lookup)` and
`items(page, pageSize, sort)` overrides. Move the display definition out of the
first loaded record and into `listSchema()`, replace those loaders with
`items(ListQuery)`, and return `ListPage<T>` with an exact total. Change bulk
delete overrides from a deleted count to `DeleteResult` when partial failures
are possible. Existing direction-only `ListViewState` constructors and
`SortRequested(String)` remain available for custom views, and old
`fromP`/`fromSort` edit links still return correctly, but `ListBlock`
subclasses must adopt the new loader and stable-schema contract.
