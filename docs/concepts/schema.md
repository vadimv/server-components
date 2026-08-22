# Schema-Driven UI

The `schema` module describes record fields for reusable list and form
components. A `DataSchema` contains field definitions, validators, optional list
column configuration, and whether list rows are selectable. It contains
metadata only; contracts still own data loading, persistence, and authorization.

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
        .column("title")
            .sortable()
            .width("40%")
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

## Lists And Forms

- `listColumns()` returns visible fields in explicitly configured column order,
  or all visible fields when no columns are configured.
- Column settings control sortability, filterability, width, alignment, and
  formatting metadata.
- `withSelectable(true)` enables row-selection state used by bulk list actions.
- `FormContractComponent` uses a schema for initial values, rendering, and
  validation.
- `ListContractComponent` can derive a record schema and customize it through
  `customizeSchema(...)`.

See the real schemas in
[PostCreateContract.java](../../examples/src/main/java/rsp/app/posts/components/PostCreateContract.java)
and the surrounding [compositions model](compositions.md).
