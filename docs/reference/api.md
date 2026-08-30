# API And Javadocs

Public Java API documentation is generated per Maven module. From the repository
root, run:

```bash
mvn -DskipTests package
```

Each built module writes browsable Javadocs to its `target/apidocs/` directory
and attaches a `*-javadoc.jar`. For example:

- `system/core/target/apidocs/index.html`
- `system/compositions/target/apidocs/index.html`
- `system/http/target/apidocs/index.html`
- `extensions/ai-agent/target/apidocs/index.html`

Generated files are build artifacts and are not committed. Use the
[module map](module-map.md) to identify the owning artifact, then consult its
Javadocs for signatures. Narrative behavior and supported combinations remain
in the concepts, guides, and reference pages because generated API comments do
not replace architectural documentation.

## Data-grid API

The reusable grid API is split deliberately:

| Type | Responsibility |
| --- | --- |
| `ListBlock<T>` | Own state, normalize URL input, reload data, handle intents and mutations |
| `ListQuery` | Validated page, page size, `SortSpec`, search text, and column filters |
| `ListPage<T>` | Immutable result rows plus the exact matching total |
| `ListView.ListViewState` | Render-ready rows, schema, query, totals, selection, capabilities, and feedback |
| `ListCapabilities` | Row-key field and create/edit/delete availability |
| `DeleteResult` | Successfully deleted and failed IDs for complete or partial outcomes |
| `DefaultListView` | Schema-driven HTML table, query controls, pagination, selection, and CRUD controls |
| `DataSchema` / `ColumnConfig` | Column order, label, sort/filter flags, width, alignment, formatter, and selection metadata |

The stable extension points on `ListBlock<T>` are `pageQueryParam()`,
`listSchema()`, `items(ListQuery)`, `defaultSort()`, `rowKey()`, the three
capability methods, `bulkDelete(Set<String>)`, and the create/edit block types.
`items(ListQuery)` must apply the query consistently and return the exact total
before pagination. For the behavioral contract and a complete example, see
[Schema-driven data grids](../guides/data-grid.md).
