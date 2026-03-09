# Summary: Project Chimera — The Agentic Infrastructure Challenge (PDF)

**Source**: `docs/pdf/Project Chimera_ The Agentic Infrastructure Challenge.pdf`
**Type**: Challenge Brief / Engineering Assessment
**Summarized**: 2026-03-08

---

## Key Concepts

### Mission Statement
- **Role**: Forward Deployed Engineer (FDE)
- **Mission**: Architect a Factory that builds Autonomous Influencers
- **Context**: Spec-Driven Development + React + Java + Agentic Orchestration
- **Duration**: 2 Days
- **The Problem**: Most AI projects fail due to fragile prompts and messy codebases — they hallucinate or break at scale
- **The Solution**: A strongly-typed Java 21+ environment where **Intent (Specs)** is the source of truth and **Infrastructure (CI/CD, Tests)** ensures reliability

### Core Philosophies & Rules of Engagement

| Philosophy | Rule |
|---|---|
| Spec-Driven Development (SDD) | No implementation code until Specification is ratified. Use GitHub Spec Kit. Vague specs → agent hallucination |
| Traceability (MCP) | Tenx MCP Sense server connected to IDE at all times — the "Black Box" flight recorder |
| Skills vs. Tools | Skills = reusable agent-callable functions (e.g., `download_video`). MCP Servers = external bridges (e.g., database connector) |
| Git Hygiene | Commit minimum 2x/day. Commit history tells a story of evolving complexity |

### Day 1 Deliverables (Strategist & Architect)
- **Task 1.1 (Deep Research)**: Read the a16z article, OpenClaw/Moltbook articles, and SRS document. Answer: How does Chimera fit into the Agent Social Network? What social protocols are needed for agent-to-agent communication?
- **Task 1.2 (Architecture Strategy)**: `research/architecture_strategy.md` with agent pattern selection (Hierarchical Swarm vs. Sequential Chain), HITL approval placement, database choice (SQL vs. NoSQL), Mermaid.js diagrams
- **Task 2.1 (Environment Setup)**: Git repo + Tenx MCP Sense connection + Java 21+ enterprise environment (Maven/Gradle) + `pom.xml` or `build.gradle` with JUnit 5, Jackson/Gson, MCP Java SDKs
- **Task 2.2 (Master Specification)**: `specs/_meta.md`, `specs/functional.md`, `specs/technical.md` (API contracts + ERD), bonus: `specs/openclaw_integration.md`

### Day 2 Deliverables (Builder & Governor)
- **Task 3.1 (Context Engineering)**: `CLAUDE.md` containing: project context, Prime Directive ("NEVER generate code without checking specs/ first"), Java-specific directives (Java 21+ idioms, Records for DTOs, JUnit 5), traceability directive ("Explain your plan before writing code")
- **Task 3.2 (Tooling Strategy)**: Developer MCP servers documented in `research/tooling_strategy.md`; `skills/` directory with READMEs for ≥2 critical skills defining Input/Output contracts
- **Task 4.1 (TDD)**: Failing JUnit 5 tests: `trendFetcherTest.java` (trend data structure vs. API contract), `skillsInterfaceTest.java` (skill interface parameters + exception handling incl. `BudgetExceededException`)
- **Task 4.2 (Automation)**: `Makefile` with `make setup`, `make test`, `make lint`
- **Task 5 (CI/CD + Governance)**: `.github/workflows/main.yml` + `.coderabbit.yaml` checking Spec Alignment, Java Thread Safety, Security Vulnerabilities

### Assessment Rubric (Velocity vs. Distance Matrix)

| Dimension | FDE Trainee (1-3pts) | Orchestrator (4-5pts) |
|---|---|---|
| Spec Fidelity | Good .md descriptions | Executable specs: API schemas, ERDs, OpenClaw protocols |
| Context Engineering | Basic rules file | Strategic context: Dev MCPs vs. Runtime Skills clearly separated |
| Testing Strategy | Basic unit tests exist | True TDD: failing tests exist BEFORE implementation |
| CI/CD & Automation | Basic Makefile pipeline | Governance pipeline: lint + AI review + Maven/Gradle on every push |
| Java Data Modeling | POJOs with mutable getters/Maps | Java Records for immutable DTOs — thread-safe OCC |
| Swarm Concurrency | Synchronous or legacy Threads | `Executors.newVirtualThreadPerTaskExecutor()` for thousands of parallel Workers |

### Bonus Tasks
- Dockerfile + `make docker-test`
- `specs/openclaw_integration.md`
- `make spec-check` script (code-to-spec alignment verification)

### Final Submission Requirements
1. Public GitHub repo: `specs/`, `tests/`, `skills/`, `Makefile`, `.github/workflows/`, `.cursor/rules`
2. Loom video (≤5 min): spec walkthrough, failing tests running, IDE agent context demo
3. MCP Telemetry: Tenx MCP Sense active throughout (verified via GitHub account)

---

## Relevance to Project Chimera

- This document IS the engineering challenge brief — it defines the **evaluation criteria** by which the Chimera codebase will be judged
- The rubric's "Orchestrator" column maps directly to the Project Chimera constitution's 7 principles: Java Records (Principle I + V), SDD (Principle II), PWJ (Principle III), MCP (Principle IV), Virtual Threads (Principle V), TDD/JUnit 5 (Principle VI), HITL (Principle VII)
- The **Prime Directive** ("NEVER generate code without checking specs/ first") is the literal behavioral rule the `CLAUDE.md` must encode

---

## Architectural Decisions It Forces

1. **`CLAUDE.md` is a graded deliverable** — it must contain all four required elements (project context, prime directive, Java directives, traceability) and must be discoverable by the IDE agent before any code generation occurs
2. **Skills MUST have defined I/O contracts** before implementation — the assessment rubric explicitly evaluates this; skills without contracts are a failing condition
3. **Failing tests MUST exist before any implementation is committed** — the rubric distinguishes "unit tests exist" (trainee) from "failing tests define goal posts" (orchestrator)
4. **Virtual Threads are a graded dimension** — using legacy thread pools or synchronous execution is an explicit downgrade on the rubric. `Executors.newVirtualThreadPerTaskExecutor()` is the target API

---

## Open Questions for the Specs

- The challenge requires `specs/technical.md` to include **API contracts (JSON schemas) AND a database ERD** — are these artifacts already planned in Chimera's spec structure, or do they need to be explicitly scaffolded?
- The `make spec-check` bonus command (script that verifies code aligns with specs) — is this implementable as a grep/AST check, or does it require an LLM call? How does it integrate with the CI/CD pipeline?
- The `specs/openclaw_integration.md` is listed as a bonus spec — given its relevance to Chimera's agent network strategy, should it be elevated to a required spec?
- The assessment requires a **Loom walkthrough demonstrating that the IDE agent answers questions using CLAUDE.md context** — what specific questions should be prepared to demonstrate this convincingly?
