# Composable UI design

This design specification describes a "second level" framework on top of
the experimental web "server-components" framework for SPA-like applications with SSR: https://github.com/vadimv/server-components

## Objectives
- the design should allow build virtually any types of UI, not only CRUD-like interfaces
- the design should allow easy extension with custom components, usage patterns and scenarios
- all elements should be by design easily testable

## The Components Tree

- Application and Configuration
  -Authentication and Authorization
  - Routing
  - Address Bar Sync
  - External World IO and UI Components Management
  - Layout
  - Header
  - Tree View
  - List View
  - New / Edit Dialog
  - etc

## Information flow and state management

- Components provide refined / enriched context to their downstream components -- from upstream to downstream during an initial rendering
- Components send notifications to downstream components, e.g. notification that a state changed in the external world -- from upstream to downstream
- Components send notifications to upstream components, e.g. new entity created with the fields provided -- from downstream to upstream

The state is present on the different levels:
- state stored in the synchronized browser's address bar, e.g. an entity instance ID, this is the primary source of truth for unveiling page's focus
- external persistent state e.g. in a database that can be read and written and provided as a stream of updates
- internal state of the UI components that is managed by the framework and can be synchronized with the external world
- external or internal store for shared session state, e.g. OAuth tokens.


## Application's definition

An application is defined in Java as a list of Modules instantiated on the 'External World IO and UI Components Management' component's level.

For communication between the Application and UI implementation components under Layout a Common Language to be provided with a DSL for configuration and passed in the Context and events.
UI implementation components can be swapped with different implementation as soon as these implementations assigned to the same type e.g. "list" and understand this type-specific definition Language for its materialization.

```java
    final App app = new App(config, uiRegistry, router, new PostsEntity(seviceA), new ModuleB(seviceB), new ModuleC(databaseC));
```

"Module" implies a functional area, that could be a CRUD entity or a ChatModule representing a UI for a stream of messages.
Every Module is identified by its name.
For every Module it should be defined:
- views requirements e.g. of types "list", "create", "edit" and data for rendering downstream components
- callbacks to external events for notifying the changes to the downstream components
- actions definitions e.g. "create", "update", "start", "stop" etc with their code implementations, actions are listening for the events send by downstream components
  This is the part that provides a bridge between the application and external world.

Modules' states can be optionally synchronized with the Address Bar with its configurable position in the path as well as extra query parameters, for example for defining search filters.
Examples of a relative URL starting with a path:
- a list: /"module name"?page=2&sort=asc
- an edit: /"module name"/"module instance id"
  Address bar's pattern should be generally consistent for a page.

Path elements and Query parameter are treated slightly different:
- Paths (/posts/1) should determine Which Module and Contract is selected (Routing).
- Query Params (?sort=asc&page=2) should determine The State of that Contract (Context).

Both Path elements and Query Parameters are automatically mapped and synchronized to the components context.

Resolving of the page state starts on the router level, dispatching the demand for the modules and contracts to appear on the page.
```java
    final Router router = new Router()
        // Path -> Module Name + View ID
        .route("/posts", PostsListContract.class)
        .route("/posts/{id}", PostsEditContract.class)
        .route("/admin/users", UsersListContract.class);

```
Routes dispatch implementation can be iteration-based on be based on a Trie.

Contracts are defined in static inner classes or separate files.

```java

// Inside PostsModule

// 1. Define the Contract explicitly
public static class PostsListContract extends ListViewContract<A> {
    
    // 2. Strongly Typed Accessors (The "Source of Truth")
    public int page() {
        // "getAttribute" is a helper from the base ViewContract
        // It handles parsing and default values (e.g., default to page 1)
        return getAttribute("page", Integer.class).orElse(1); 
    }

    public String sort() {
        return getAttribute("sort", String.class).orElse("desc");
    }

    // 3. Clean Business Logic
    @Override
    public List<A> items() {
        // No magic strings here! Just clean method calls.
        return serviceA.findAll(page(), sort());
    }
}

```


By defining the parameter name ("p" vs "page") explicitly in code, you gain three things:
Decoupling: You can change the public URL (?p=1) without changing your internal variable name (int page).
Documentation: The code itself tells you exactly what URL parameters are supported.
Default Handling: You can centralize logic like "Default page is 0" or "Default sort is DESC" in one definition.
Here is how I recommend implementing this "Typed Parameter Definition" pattern in your spec.
1. The Infrastructure (The Framework Level)
   Introduce a strongly-typed helper class (e.g., QueryParam<T>) to handle the mapping, parsing, and defaults.
   code
   Java
   // Framework Class
   public class QueryParam<T> {
   private final String name;
   private final Class<T> type;
   private final T defaultValue;

   public QueryParam(String name, Class<T> type, T defaultValue) {
   this.name = name;
   this.type = type;
   this.defaultValue = defaultValue;
   }

   // The framework calls this to fetch value from the current Context
   // (Assumes ViewContract has access to the current request Context)
   public T resolve(Context ctx) {
   return ctx.getAttribute(name, type).orElse(defaultValue);
   }
   }
2. The Implementation (The Module Level)
   Now your Contract becomes a declarative definition of the UI's interface. Notice how cleaner items() becomes.
   code
   Java
   // Inside PostsModule
   public static class PostsListContract extends ListViewContract<A> {

   // 1. DEFINE PARAMETERS (The "Public Interface")
   // Explicitly mapping "p" in the URL to an Integer with default 1
   private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);

   // Explicitly mapping "q" to a Search String
   private static final QueryParam<String> SEARCH = new QueryParam<>("q", String.class, "");

   // 2. TYPED ACCESSORS (Optional, but nice for internal use)
   // You delegate the lookup to the definition
   public int page() { return resolve(PAGE); }
   public String search() { return resolve(SEARCH); }

   @Override
   public List<A> items() {
   // 3. USAGE
   // Pure business logic. No strings, no parsing, no null checks.
   return serviceA.findAll(search(), page());
   }
   }


Modules follow similar composition via extension pattern as Components<S>:

```java
    final class PostsEntity extends Module {
        private final ServiceA serviceA;
        
        public PostsEntity(ServiceA serviceA) {
            this.serviceA = serviceA;
        }

        @Override
        public String name() {
            return "posts-module";
        }


        // 4. Registering in the Module
        @Override
        List<ViewPlacement> views() {
            return List.of(
                new ViewPlacement(Slot.PRIMARY, new PostsListContract()),
                new ViewPlacement(Slot.PRIMARY, new PostEditContract()),
                new ViewPlacement(Slot.PRIMARY, new PostCreateContract()) // might not be routed
            );
        }
        
        /**
        @Override
        List<ViewContract> views() {
            // Contract classes act as a bridge between Modules and UI implementation components
            return List.of(
                    new EditViewContract<Post>() {

                        @Override
                        public String name() {
                            return "edit-view";
                        }
                        
                        @Override
                        public Post item() {
                            return serviceA.find(attribute("name"), Long.parseLong(attribute("id"))); // get input from the components context
                            return serviceA.find(attribute("name"), Long.parseLong(attribute("id"))); // get input from the components context
                        }
                    
                    },
                    
                    new ListViewContract<Post>() {

                        @Override
                        public String name() {
                            return "list-view";
                        }
                        @Override
                        String isAuthorized(AuthorizationContext context) {
                            return context.role().equals("admin");
                        }
                
                        @Override
                        public List<Post> items() {
                            return serviceA.findAll(attribute("search"), attribute("page"), attribute("sort", "asc"));
                        }

                        @Override
                        public List<Column<Post>> columns() {
                            return List.of(new Column<Post>("column1", a -> a.field1.toString()),
                                           new Column<Post>("column2", a -> a.field2.toString()));
                        

                        } 
                      }, 
                           new CreateContract() {});
        }
        **/
        
        @Override
        List<NotificationContract> notifications() {
            return List.of(
                    // A notification is advertised in the component context so UI components can request subscription by sending an event
                    new RegularNotification(
                            "update", // notification name
                            // somehow the caller needs to know what it wants to subscribe to e.g. a topic
                            // the result of the function is a subscription handle, an Object that can be used to unsubscribe, e.g. a topic
                            (topic, consumer) -> serviceA.subsribe(topic, a -> consumer.accept(convertToNotification("post-update", // notification name
                                                                                               a)),
                            handle -> serviceA.unsubsribe(handle)) // invoked when all subscribed components request unsubscribe or unmounted     
                    )
            );
        }
        
        @Override
        List<ActionContract> actions() {
            return List.of(new RegularAction(
                    "save", // action name
                    value -> serviceA.save(convertToA(value))
            ));
        }
        
    }
```
As the result the attributes are filled in the Component Context available for UI components. These implementation depend on Contracts.
There should be rules for naming attributes in the component context, with the namespaces hierarchy, forming a tree, so demands should not clash.

Concrete implementations of components UI are discovered on runtime in a registry.

For explicit registration in the constructor:
```java
    new App(config,
        new UiRegistry()
            .register("list-view", MaterialList::new)
            .register("dashboard", MaterialDashboard::new),
        new ModuleA(serviceA),
        ...
)
```
Another option is to use the Java ServiceLoader mechanism for dynamic loading.

## Layout

A concrete Layout implementation can be selected in the App or Router, e.g., StandardAdminLayout.
UI components implementations should provide a hint for the Layout how to position them, e.g. "left", "right", "center", etc.
This ensures simplicity of Layout on the prototype stage.

Later consider using Logical Slots or Areas instead of hints:
- Instead of "left", use "sidebar" or "secondary".
- Instead of "center", use "main" or "primary".
  This enables separation of concerns between UI and may be defined on the Module level:

```java
@Override
List<ViewPlacement> views() {
    return List.of(
        // "I want the List View to be in the Primary slot"
        new ViewPlacement( // the new abstraction for layout
            Slot.PRIMARY, 
            new ListViewContract<A>("main-list", ...)
        ),

        // "I want the Details View to be in the Secondary slot (side panel)"
        new ViewPlacement(
            Slot.SECONDARY, 
            new DetailsViewContract<A>("quick-details", ...)
        )
    );
}
```

## Generalizing a Contract and UI

CRUD is just a subset:
- ListContract extends ViewContract
- FormContract extends ViewContract

Other ViewDefinition Types:
- DashboardContract extends ViewContract (for a grid of widgets)
- KanbanContract extends ViewContract (columns, drag-and-drop)
- CustomViewContract extends ViewContract (arbitrary HTML/Component)

In the similar fashion UI implementation should extend the "UI" class/interface e.g. Component<S>.

## External World IO Lifecycle & Cleanup
The framework maintains a registry of active subscription handles associated with the user's session.
Upon session termination (WebSocket disconnect or timeout), the framework automatically invokes the unsubscribe callbacks defined in the contracts to release resources and prevent memory leaks.

## The "Custom View" Escape Hatch:
- If a developer wants to build a completely custom UI that doesn't fit any "Definition" pattern, they should be able to plugin a Component<S> in the framework.

To achieve that Custom View Escape Hatch functionality the standard way to be followed:
- provide a custom Contract with its context namespace, following agreements for example to advertise a subscription capability
- provide a custom UI implementation that depends on the introduced custom Contract class and provide a Component<S> implementation and a layout hint
- register the contract-ui pair in the registry alongside other registrations
- define a new module with concrete details

## Authentication and Authorization
- Authentication on the base of OAuth 2 PKCE by default and another implementation could be selected on an init.
- Modules should allow granular specification of access rights for views, notifications and actions

## Errors handling
- Authorization errors result with code 403 and a "non-authorized" page
- Errors or exception is contained within modules result with code 500 and error page

## Testing
- design should allow testing all elements in isolation
- Property-Based-Testing (PBT) is the method to test for the UI components implementations

## Agentic Interface
The system supports a CommandModule that accepts natural language. The Framework exposes a Reflective Schema (listing available Routes, Modules, and Contracts) to an AI Agent.
The Agent is authorized to emit System Events that:
- Mutate Shared Session State (e.g., Theme, Filters).
- Trigger Navigation.
- Inject Layout Overrides (changing Slot assignments dynamically).

Semantic Annotations / Method override on Contracts:
Add descriptions so the AI knows what the UI does.
code
```java
@AiDescription("Displays a list of items with sort/filter capabilities") // or better have a separate method to implement for that e.g. String description()
public class ListViewContract { ... }
```

Registry as a Catalog:
Expose an API to dump the Registry as a JSON Schema. This serves as the "Prompt Context" for the LLM.

Streaming Router:
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