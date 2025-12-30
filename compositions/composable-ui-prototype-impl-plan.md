## The goal
A working prototype of a CRUD web admin-like UI prototype for Posts and one "admin" user.

## Steps

### 1. Domain & Service Layer
Create a self-contained domain model and service.
- Define `Post` record (id, title, content).
- Implement `PostService` with in-memory storage:
    - `List<Post> findAll(int page, String sort)`
    - `Optional<Post> find(int id)`
    - `int create(Post post)`
    - `boolean update(int id, Post post)`
- Pre-populate with dummy data (Lorem Ipsum).

### 2. Core Framework Skeleton
Implement the fundamental abstractions in `rsp.compositions`.
- **Typed Common Language**: Define the interfaces/classes for typed communication (e.g., `QueryParam<T>`, `Context`).
- **Contracts**: Define `ViewContract`, `ListViewContract`, `EditViewContract`.
- **Module System**: Define `Module`, `ViewPlacement`, and `Slot` enum (PRIMARY, SECONDARY, OVERLAY).
- **Registry**: Define `UiRegistry` interface for mapping Contracts to UI Components.

### 3. Auth & Basic App Shell
Implement the application entry point and security.
- Implement `StubAuth` (no external deps) to handle "admin" login/logout.
- Create `App` class that initializes the server.
- Implement a basic "Hello Admin" view to verify the session/auth flow works.

### 4. Routing & Registry Implementation
Connect the URL to the Contracts.
- Implement a `Router` that maps URL paths (e.g., `/posts`) to `Contract` classes.
- Implement a simple `UiRegistry` that allows registering a `Component` factory for a specific `Contract` type.
- Ensure the Router can resolve a request to a specific Module and Contract.

### 5. Posts Module & Contract
Implement the business logic layer.
- Create `PostsListContract` extending `ListViewContract`.
    - Use `QueryParam<Integer>` for pagination.
    - Implement `items()` delegating to `PostService`.
- Create `PostsModule` extending `Module`.
    - Register `PostsListContract` to `Slot.PRIMARY`.

### 6. UI Implementation (The Renderer)
Create the actual visual components.
- Create a `SimpleListView` component that implements `Component<S>`.
    - It should accept a `ListViewContract` as input.
    - It should render an HTML table using the data from the contract.
- Register `SimpleListView` in the `UiRegistry` bound to `ListViewContract`.

### 7. Layout & Assembly
Put it all together.
- Implement `MainLayout` component.
    - It should define the HTML skeleton.
    - It should render content into the correct `Slot` locations based on the active Module.
- Assemble the `App` with `StubAuth`, `Router`, `UiRegistry`, and `PostsModule`.

### 8. Verification
- Verify login as admin.
- Navigate to `/posts`.
- Verify the list renders.
- Test query params: `/posts?p=2` should show the second page of data.