# Schema-Driven Create And Edit Forms

`FormBlock<T>`, `EditBlock<T>`, and `DefaultFormView` provide the standard
server-driven create/edit workflow. The block owns the typed draft, validation,
capabilities, persistence outcomes, and navigation. The view renders semantic,
accessible controls and dispatches typed intents.

## Share The Data Contract

Use one stable `DataSchema` for a resource's grid and forms. Configure list
columns separately from field behavior: an ID can be an explicit grid column
while its default `FieldType.ID` widget remains hidden in forms.

```java
public static final DataSchema POSTS = DataSchema.builder()
        .field("id", FieldType.ID).label("ID")
        .field("title", FieldType.STRING)
            .required().maxLength(200)
            .placeholder("Enter post title")
        .field("content", FieldType.TEXT)
            .widget(Widget.TEXTAREA).maxLength(10_000)
        .column("id").sortable().width("6rem")
        .column("title").sortable().filterable()
        .column("content").filterable()
        .build();
```

Defaults are converted to the declared Java type during initialization. Empty
optional reference values remain `null`; string fields start as an empty
string, booleans as `false`, and primitive fields use their primitive default.
Invalid configured defaults fail initialization instead of silently changing
the value.

## Implement Create And Edit Blocks

A create block supplies mode, schema, and persistence. Keep the boolean method
while supporting existing callers, and return the richer result to the form
runtime:

```java
public final class PostCreateBlock extends FormBlock<Post> {
    @Override public DataSchema schema() { return POSTS; }
    @Override protected boolean isCreateMode() { return true; }

    @Override public boolean save(Map<String, Object> values) {
        return saveResult(values).succeeded();
    }

    @Override protected FormMutationResult saveResult(Map<String, Object> values) {
        return posts.createResult(new Post(
                null, (String) values.get("title"), (String) values.get("content")));
    }
}
```

An edit block additionally resolves an ID, loads the entity, and deletes it:

```java
public final class PostEditBlock extends EditBlock<Post> {
    @Override protected String resolveIdFromPath(Lookup lookup) {
        return POST_ID.resolve(lookup);
    }
    @Override protected Post item(String id) {
        return posts.find(id).orElse(null);
    }
    @Override public DataSchema schema() { return POSTS; }
    @Override protected FormMutationResult saveResult(Map<String, Object> values) {
        return posts.updateResult(resolveId(), toPost(values));
    }
    @Override protected FormMutationResult deleteResult(String id) {
        return posts.deleteResult(id);
    }
    @Override public boolean save(Map<String, Object> values) {
        return saveResult(values).succeeded();
    }
    @Override protected boolean delete(String id) {
        return deleteResult(id).succeeded();
    }
}
```

Returning `null` from `item` produces a non-mutating not-found state: fields,
save, and delete are unavailable, while cancel can still return to the list.
Exceptions during load, save, or delete become safe form-level feedback rather
than escaping into markup.

## Return Actionable Persistence Outcomes

`FormMutationResult` distinguishes `SUCCESS`, `INVALID`, `NOT_FOUND`,
`CONFLICT`, and `FAILURE`. Invalid results carry a
`Map<String, List<String>>` so persistence and relationship checks can point to
specific controls. Use the `_form` key for errors that do not belong to one
field.

```java
if (!postExists.test(comment.postId())) {
    return FormMutationResult.invalid(Map.of(
            "postId", List.of("The selected post no longer exists")));
}
return FormMutationResult.saved(id, "Comment created.");
```

The service remains the final integrity boundary. In CrudApp, comments reject
unknown post IDs and deleting a post cascades to its comments. Schema and
browser validation improve feedback but do not replace domain validation.

## Submission And Draft Safety

`DefaultFormView` renders a semantic `<form>` with a submit button, so Enter
submission and browser-native validation work. The block then converts every
submitted value according to its declared Java type and runs schema plus custom
validation before persistence.

Only known, editable fields are accepted. Hidden IDs, read-only values, and
unknown payload keys retain their server-owned values. This whitelist applies
equally to browser and agent actions. Conversion failures remain visible as
field errors; an invalid number or date is never replaced silently with zero or
another default.

`FieldChanged` updates the typed draft and marks it dirty. Cancel asks for
confirmation when the rendered draft is dirty. Saving and deleting enter busy
states, disable controls, and ignore duplicate mutations until navigation or a
rendered failure completes.

## Widgets And Accessibility

The default view supports text, password, textarea, number, checkbox, select,
radio, date, and date-time controls. Enum choices come from configured options
or the declared enum constants. Hidden fields are not rendered or submitted;
read-only fields are displayed but protected from submission.

Every control receives a form-scoped unique ID, an associated label, and
schema-derived validation attributes. Required status has screen-reader text;
field errors use `aria-invalid` and `aria-describedby`; the error summary links
back to controls. Operation feedback uses status or alert roles, and unavailable
records retain a cancel action.

The view emits structural CSS classes but no theme. CrudApp's
[style.css](../../examples/src/main/java/rsp/app/posts/style.css) demonstrates
responsive form cards, radio/checkbox groups, messages, errors, action layout,
and narrow-screen behavior.

## Capabilities And Agent Actions

Override `canSave()`, `canDelete()`, or `canCancel()` to remove an operation.
The same effective capabilities gate UI intents, framework events, and declared
agent actions. Runtime metadata includes form mode, status, dirty state,
capabilities, validation errors, and the current draft; password values
are excluded.

The standard agent actions are `set_field`, `save`, `cancel`, and, for edit
forms, `delete`. `set_field` uses the same schema conversion and editability
checks as a browser change. A save payload contains only visible, non-read-only
schema fields.

## Testing Checklist

Cover forms at three levels:

1. Contract tests: defaults, nulls, type conversion, immutable errors, and
   result statuses.
2. Block/view tests: field whitelisting, validation, not-found/load failures,
   capabilities, duplicate mutation protection, semantic markup, widgets, and
   accessibility relationships.
3. Service/browser tests: field and relationship failures, create/edit/delete,
   dirty cancel, Enter submission, unavailable routes, cascade policy, and
   return to the complete originating grid query.

The Posts and Comments forms in CrudApp are the executable reference. See also
the [data-grid guide](data-grid.md) for the shared list side of the schema.

## Migrating Existing Forms

Replace per-block record-derived schemas with a stable shared schema. Migrate
boolean persistence gradually by overriding `saveResult` and `deleteResult`
while retaining boolean adapters for compatibility. Replace custom click-only
save markup with `DefaultFormView`, and route browser/agent mutations through
`FieldChanged` or `FormValuesCollected`. Existing `DefaultEditView` and legacy
`EditViewState` constructors remain available; `DefaultFormView` is the neutral
name for new create and edit bindings.
