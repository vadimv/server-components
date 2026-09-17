# API And Javadocs

Public Java API documentation is generated per Maven module. From the repository
root, run:

```bash
mvn -DskipTests package
```

Each built module writes browsable Javadocs to its `target/apidocs/` directory
and attaches a `*-javadoc.jar`. For example:

- `system/ui-core/target/apidocs/index.html`
- `system/authentication-api/target/apidocs/index.html`
- `system/application-api/target/apidocs/index.html`
- `system/http-api/target/apidocs/index.html`
- `system/http-routing/target/apidocs/index.html`
- `system/http-json/target/apidocs/index.html`
- `system/websocket-api/target/apidocs/index.html`
- `system/server-jdk/target/apidocs/index.html`
- `system/compositions/target/apidocs/index.html`
- `system/ui-http/target/apidocs/index.html`
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
| `ListView.ListViewState` | Render-ready rows, schema, query, totals, selection, capabilities, feedback, and operation status |
| `ListCapabilities` | Row-key field and create/edit/delete availability |
| `ListStatus` | Ready/deleting lifecycle used for busy UI and duplicate-mutation suppression |
| `DeleteResult` | Successfully deleted and failed IDs for complete or partial outcomes |
| `RelatedListLinkSpec` / `ListView.RelatedListColumn` | Declared and route-resolved inverse relationship links appended to the grid |
| `DefaultListView` | Schema-driven HTML table, query controls, pagination, selection, and CRUD controls |
| `ConfirmationDialog` | Accessible native confirmation markup that dispatches a typed intent only on confirmation |
| `DataSchema` / `ColumnConfig` | Column order, label, sort/filter flags, width, alignment, formatter, and selection metadata |

The stable extension points on `ListBlock<T>` are `pageQueryParam()`,
`listSchema()`, `items(ListQuery)`, `defaultSort()`, `rowKey()`, the three
capability methods, `bulkDelete(Set<String>)`, `relatedListLinks()`, and the
create/edit block types.
`items(ListQuery)` must apply the query consistently and return the exact total
before pagination. For the behavioral contract and a complete example, see
[Schema-driven data grids](../guides/data-grid.md).

## Data-form API

| Type | Responsibility |
| --- | --- |
| `FormBlock<T>` | Initialize and own a typed draft; whitelist fields; validate, save, cancel, and expose agent actions |
| `EditBlock<T>` | Resolve/load an entity and add typed single-entity delete behavior |
| `EditView.EditViewState` / `EditView.ChoiceSet` | Render-ready draft plus immutable resolved choices, mode, capabilities, status, errors, message, dirty flag, and return route |
| `FormMode` / `FormStatus` | Explicit create/edit mode and ready/busy/unavailable/failure lifecycle |
| `FormCapabilities` | Save, delete, and cancel availability shared by handlers and views |
| `FormMutationResult` | Success, invalid, not-found, conflict, or failure outcome with field errors |
| `FormValueCodec` | Strict conversion between browser/agent values and schema-declared Java types |
| `ReferenceDef` / `FieldChoice` | Resource metadata and authorized scalar ID/human-label choices for relationship selectors |
| `DefaultFormView` | Semantic accessible form, schema widgets, validation summary, feedback, and guarded actions |
| `DataSchema` / `FieldDef` | Field type, widget, defaults, editability, validation, labels, and input hints |

The principal extension points are `schema()`/`formSchema()`, `isCreateMode()`,
`item(...)`, `fieldChoices(...)`, `saveResult(...)`, `validate(...)`, the three
capability methods, and, on `EditBlock`, ID resolution plus
`deleteResult(...)`. Boolean `save` and `delete` methods remain compatibility
hooks for existing blocks. For the behavioral contract and complete example, see
[Schema-driven create and edit forms](../guides/data-forms.md).

## Native Dialog API

`EventContext.showModal(ElementRef)` is the typed server-to-browser operation
for opening a rendered `<dialog>` with `HTMLDialogElement.showModal()`. It
replaces raw `evalJs("confirm(...)")` usage and keeps element resolution in the
existing `ElementRef`/`NodeId` protocol. `ConfirmationDialog` builds on it for
local confirmations. `ModalLayerLayout` marks routed layer dialogs for
automatic promotion into the browser top layer after their event listeners are
registered, preserving Escape, focus containment, backdrop dismissal, and
server-side `HIDE` handling.
