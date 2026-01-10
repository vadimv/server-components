# Composable UI Design

This design specification describes a "second level" framework on top of the experimental web "server-components" framework for SPA-like applications with SSR: https://github.com/vadimv/server-components

## Objectives
- the design should allow build virtually any types of UI, not only CRUD-like interfaces
- the design should allow easy extension with custom components, usage patterns and scenarios
- all elements should be by design easily testable

## Implemented Features

The following are implemented in code (see source for details):
- **App, Module, Router** - Application structure with path parameter routing
- **ViewContract, ListViewContract, EditViewContract** - Contract hierarchy
- **QueryParam, PathParam** - Typed parameter definitions with defaults
- **Slot, ViewPlacement, UiRegistry** - UI component discovery and layout slots
- **AuthComponent, AuthorizationStrategy** - Authentication and authorization
- **DefaultListView, DefaultEditView** - Default UI implementations
- **Address bar sync** - Via AutoAddressBarSyncComponent
- **Custom View escape hatch** - Custom contracts + UI components via UiRegistry

---

## Notifications (Not Implemented)

Notifications allow upstream components to push updates to downstream components when external state changes.

```java
@Override
List<NotificationContract> notifications() {
    return List.of(
        // A notification is advertised in the component context so UI components can request subscription by sending an event
        new RegularNotification(
            "update", // notification name
            // somehow the caller needs to know what it wants to subscribe to e.g. a topic
            // the result of the function is a subscription handle, an Object that can be used to unsubscribe
            (topic, consumer) -> serviceA.subscribe(topic, a -> consumer.accept(convertToNotification("post-update", a))),
            handle -> serviceA.unsubscribe(handle) // invoked when all subscribed components request unsubscribe or unmounted
        )
    );
}
```

## Actions (Not Implemented)

Actions are listening for events sent by downstream components and provide a bridge between the UI and external world.

```java
@Override
List<ActionContract> actions() {
    return List.of(new RegularAction(
        "save", // action name
        value -> serviceA.save(convertToA(value))
    ));
}
```

## Layout (Not Implemented)

A concrete Layout implementation can be selected in the App or Router, e.g., StandardAdminLayout.
UI components implementations should provide a hint for the Layout how to position them.

Using Logical Slots or Areas:
- Instead of "left", use "sidebar" or "secondary"
- Instead of "center", use "main" or "primary"

This enables separation of concerns between UI and may be defined on the Module level.

## External World IO Lifecycle & Cleanup (Not Implemented)

The framework maintains a registry of active subscription handles associated with the user's session.
Upon session termination (WebSocket disconnect or timeout), the framework automatically invokes the unsubscribe callbacks defined in the contracts to release resources and prevent memory leaks.

## Errors Handling (Not Implemented)

- Authorization errors result with code 403 and a "non-authorized" page
- Errors or exceptions contained within modules result with code 500 and error page

## Testing (Partial)

- design should allow testing all elements in isolation
- Property-Based-Testing (PBT) is the method to test for the UI components implementations

## Agentic Interface (Not Implemented)

The system supports a CommandModule that accepts natural language. The Framework exposes a Reflective Schema (listing available Routes, Modules, and Contracts) to an AI Agent.

The Agent is authorized to emit System Events that:
- Mutate Shared Session State (e.g., Theme, Filters)
- Trigger Navigation
- Inject Layout Overrides (changing Slot assignments dynamically)

### Semantic Annotations

Add descriptions so the AI knows what the UI does:
```java
@AiDescription("Displays a list of items with sort/filter capabilities")
// or better have a separate method to implement for that e.g. String description()
public class ListViewContract { ... }
```

### Registry as a Catalog

Expose an API to dump the Registry as a JSON Schema. This serves as the "Prompt Context" for the LLM.

### Streaming Router

Since AI is slow, the Router needs to support Partial Rendering. It should be able to render the "Shell" of the page while the AI is still "thinking" about the optimal layout for the inner content.

```java
public class CommandModule extends Module {

    // 1. View: The Input Box
    @Override
    List<ViewPlacement> views() {
        return List.of(
            new ViewPlacement(Slot.OVERLAY, new PromptContract())
        );
    }

    // 2. Action: Receive the User's Intent
    @Override
    List<ActionContract> actions() {
        return List.of(new RegularAction("submit-prompt", this::handlePrompt));
    }

    // 3. The "Brain"
    private void handlePrompt(ActionContext ctx, String userText) {
        // Step A: Gather Context (Where is the user? What can they do?)
        var appSchema = registry.generateSchema(); // "I have List, Details, Dashboard..."
        var currentRoute = ctx.getCurrentRoute();

        // Step B: Ask AI (The Translation Layer)
        AiInstruction instruction = aiService.interpret(userText, appSchema, currentRoute);

        // Step C: Execute Framework Command
        executeInstruction(ctx, instruction);
    }
}
```
