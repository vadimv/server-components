# Schema-Driven UI

The `schema` module describes record fields for reusable list and form
components. A `DataSchema` contains field definitions, validators, optional list
column configuration, and whether list rows are selectable. It contains
metadata only; blocks still own data loading, persistence, and authorization.

## Derive A Schema From A Record

For a quick list, derive fields from a Java record:

```java
record Post(String id, String title, String content) {}

DataSchema schema = DataSchema.fromRecordClass(Post.class);
List<Map<String, Object>> rows = schema.toMapList(posts);
```

Reflection-based derivation requires a record. `fromFirstItem(item)` uses the
same mechanism, and `toMap`/`toMapList` reject non-record values.

## Define An Explicit Schema

Use the fluent builder when labels, validation, widgets, or list behavior matter:

```java
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;

DataSchema schema = DataSchema.builder()
        .field("id", FieldType.ID)
            .hidden()
        .field("title", FieldType.STRING)
            .label("Post title")
            .required()
            .maxLength(200)
            .placeholder("Enter a title")
        .field("content", FieldType.TEXT)
            .widget(Widget.TEXTAREA)
        .column("id")
            .sortable()
        .column("title")
            .sortable()
            .filterable()
            .width("40%")
            .formatter(value -> value.toString().trim())
        .build()
        .withSelectable(true);
```

Each call to `field(...)` or `column(...)` finishes the previous definition.
Calling `build()` finishes the current definition and returns an immutable
schema. Immutable customization methods such as `withSelectable(true)` return a
new schema.

## Fields, Widgets, And Validation

`FieldType` describes the semantic value type and supplies a default Java type.
`Widget` selects the default form control. A field can override its label,
widget, Java type, default value, format, visibility, and read-only state.

Built-in validators cover required values, minimum and maximum length, regular
expressions, email addresses, and numeric ranges. Custom validation implements
`Validator`. Validate a submitted value map at the schema boundary:

```java
ValidationResult result = schema.validate(fieldValues);
if (!result.isValid()) {
    Map<String, List<String>> errors = result.errors();
}
```

Validators also expose compatible HTML validation attributes, allowing the
default form view to provide browser feedback without replacing server-side
validation.

At the form boundary, `FormValueCodec` converts untrusted browser or agent
values to the field's declared Java type. It supports strings, numeric types,
booleans, dates, date-times, and enums. Blank optional reference values become
`null`; invalid values produce field errors and are never silently replaced by
a default. Configured defaults pass through the same conversion during form
initialization.

## Lists And Forms

- `listColumns()` returns fields in explicitly configured column order, or all
  visible fields when no columns are configured. Explicit configuration opts a
  field into the list even when its form widget is hidden, which lets an ID be
  hidden by default in forms but visible in a grid. A field explicitly marked
  `.hidden()` remains excluded.
- Column settings control sortability, filterability, width, alignment, and
  formatting. The default grid turns only sortable columns into controls and
  only filterable columns into filter inputs.
- `withSelectable(true)` enables row-selection state used by bulk list actions.
- `renameColumn`, `hideColumn`, and `reorderColumns` preserve the remaining
  column configuration and selectable flag.
- `FormBlock` uses a schema for typed initial values, editable-field
  whitelisting, rendering, and validation. Hidden and read-only fields retain
  their server-owned values when a browser or agent submits a payload.
- `ListBlock` requires a stable `listSchema()` so headers, filters, and column
  behavior remain available for empty and failed result pages. Record-derived
  schemas remain useful for simpler custom views, but a reusable grid should
  declare its list columns explicitly.

See the real schemas in
[PostsListBlock.java](../../examples/src/main/java/rsp/app/posts/components/PostsListBlock.java),
the [data-grid guide](../guides/data-grid.md), the
[data-forms guide](../guides/data-forms.md), and the surrounding
[compositions model](compositions.md).
