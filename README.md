# Server Components

Build AI-controlled admin interfaces and internal tools in pure modern Java, without writing JavaScript.

Server Components is a pure-Java toolkit for stateful, server-driven back-office apps. It removes REST glue, frontend build steps, and most UI boilerplate while allowing AI agents to understand your application's structure out of the box.

## Turn Workflows into Sentences

(Insert GIF here showing the Comments Admin page)

Prompt: "Open comments, go to page two, and select all items."

The AI agent natively understands your application's structure, navigates the UI, and queues up the exact actions while keeping the human in the loop for final approval.

## Why it feels different

You build internal tools as typed Java compositions, not as a pile of controllers, DTOs, and frontend state management. Backend services bind directly to default CRUD views, and the same structure that defines navigation and screens can also be exposed to an AI agent as prompt context.

## Getting started

1. Prerequisites
   - Java 25+
   - Maven 3.9+

2. Clone the repository

```bash
git clone https://github.com/vadimv/server-components.git
cd server-components
```

3. Build the project

```bash
mvn install
```

4. Run the included admin example

```bash
mvn exec:java -pl examples -Dexec.mainClass="rsp.app.posts.CrudApp"
```

Open `http://localhost:8085`, click `Sign in`, and explore the Posts/Comments admin app.

By default, `CrudApp` uses `RegexAgentService`, a deterministic regex-based agent stub included in the repository. It is meant for demos and local validation, so you can try the AI workflow without setting up Anthropic, Ollama, or any other LLM backend.

5. Try a few prompts

- `open comments`
- `go to page 2`
- `open comments and go to page 2 and select all`
- `search all posts with id < 2`

6. Generate your own admin app

Once you've run the example, open your favorite AI coding assistant in the project root and paste a prompt like this:

```
Read examples/src/main/java/rsp/app/posts/CrudApp.java.
Generate a similar real-time admin tool for managing Employees and Departments.
Create mock services and a new EmployeeAdminApp.java using the same routing, composition, and AI-agent integration patterns.
```

7. Upgrade to a full LLM agent

When you're ready, run `CrudApp` with `-Dai.agent=claude` or `-Dai.agent=ollama` and connect your preferred model backend.

## Auditable by design

This project aims to provide strong runtime supply-chain guarantees. The target architecture is zero third-party runtime dependencies outside the web-server layer.
