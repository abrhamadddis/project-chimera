<!--
SYNC IMPACT REPORT
==================
Version change: TEMPLATE → 1.0.0
Ratification: 2026-03-08 (initial adoption)
Last Amended: 2026-03-08

Principles added (all new — first ratification):
  - I.   Java 21+ Enterprise Standards
  - II.  Spec-Driven Development (NON-NEGOTIABLE)
  - III. Autonomous AI Agent Architecture (Planner-Worker-Judge)
  - IV.  MCP as Universal Integration Layer
  - V.   Thread Safety via Virtual Threads
  - VI.  Test-First with JUnit 5 (NON-NEGOTIABLE)
  - VII. Human-in-the-Loop Governance

Sections added:
  - Technology Stack & Constraints
  - Development Workflow
  - Governance

Templates updated:
  ✅ .specify/templates/plan-template.md  — Constitution Check gates filled
  ✅ .specify/templates/tasks-template.md — JUnit 5 / TDD notes updated
  ⚠  .specify/templates/spec-template.md  — No structural change required;
       Principle II (Spec-Driven) is enforced by workflow, not template shape.
  ⚠  README.md                            — Currently empty; populate with
       project overview referencing this constitution.

Deferred TODOs:
  - None. All placeholders resolved.
-->

# Project Chimera Constitution

## Core Principles

### I. Java 21+ Enterprise Standards

All production code MUST target Java 21 or higher. Language features introduced
in Java 21—records, sealed classes, pattern matching for switch, and structured
concurrency—MUST be preferred over legacy idioms wherever they improve clarity,
safety, or performance. All dependencies MUST track current major versions.
Deprecated JDK APIs MUST NOT appear in new code. Any deviation requires a
documented justification in the Complexity Tracking table of the feature
`plan.md`.

**Rationale**: Java 21 is the current LTS release. Committing to modern Java
keeps the codebase maintainable, leverages platform-level performance gains
(especially for concurrency), and signals long-term stability to contributors.

### II. Spec-Driven Development (NON-NEGOTIABLE)

No implementation code MUST be written without a corresponding approved
specification (`spec.md`). The sequence is strictly: Spec → Plan → Tasks →
Implementation. Every feature MUST have its `spec.md` reviewed and accepted
before any source file is created or modified. Specs MUST define:

- Prioritised user stories with independent acceptance scenarios.
- Functional requirements (FR-###) using MUST/MUST NOT language.
- Measurable success criteria (SC-###).

Committing implementation code that has no backing spec is a constitution
violation and MUST block merge.

**Rationale**: Specs are the contract between intent and implementation. Writing
code without a spec introduces undefined behaviour, scope creep, and costly
rework. This principle is the workflow foundation on which all others depend.

### III. Autonomous AI Agent Architecture (Planner-Worker-Judge)

All AI agent features MUST implement the Planner-Worker-Judge (PWJ) pattern:

- **Planner**: Decomposes high-level goals into ordered, executable sub-tasks.
  The Planner MUST NOT execute actions directly; it MUST only produce plans.
- **Worker**: Executes a single sub-task in isolation. Workers MUST be
  stateless between invocations and MUST communicate exclusively through
  structured inputs and outputs.
- **Judge**: Evaluates Worker outputs against the acceptance criteria supplied
  by the Planner. The Judge MUST flag outputs whose confidence falls below the
  defined threshold for human review (see Principle VII) rather than silently
  passing or dropping them.

Every Planner decision, Worker execution, and Judge verdict MUST be emitted as
a structured log event with at minimum: `agent_role`, `task_id`, `status`,
`timestamp`, and `payload_hash`.

**Rationale**: PWJ separates concerns cleanly, enables isolated unit testing of
each role, and provides natural HITL insertion points. Observable pipelines are
a prerequisite for debugging and auditing autonomous systems.

### IV. MCP as Universal Integration Layer

All external service integrations MUST be exposed exclusively via Model Context
Protocol (MCP) servers. Provider-specific SDKs and direct REST calls to external
services MUST NOT appear in application business-logic modules; they MUST be
encapsulated within a dedicated MCP server. Internal service-to-service
communication MUST also route through MCP where the target service exposes an
MCP interface.

Every MCP server MUST have contract tests (see Principle VI) that verify its
tool schemas independently of any consumer.

**Rationale**: MCP provides a standardised, observable, and swappable
integration contract. Centralising integrations through MCP makes the system
testable with mock servers, prevents vendor lock-in from permeating business
logic, and gives the Judge a single interception point for auditing agent actions.

### V. Thread Safety via Virtual Threads

All concurrent I/O-bound operations MUST use Java Virtual Threads (Project
Loom, GA in Java 21). Platform thread pools MUST NOT be created for I/O-bound
work. Shared mutable state MUST be protected via `java.util.concurrent`
primitives or immutable data structures (records, unmodifiable collections).
Fan-out / fan-in concurrency patterns MUST use `StructuredTaskScope` to ensure
clean cancellation and error propagation.

**Rationale**: Virtual Threads allow high-throughput concurrent I/O without
callback complexity or manual thread-pool tuning. Structured Concurrency
prevents orphaned threads and makes lifecycle management explicit and auditable.

### VI. Test-First with JUnit 5 (NON-NEGOTIABLE)

Tests MUST be written before implementation code (Red-Green-Refactor). The
sequence per task is: write failing test → confirm failure → implement →
confirm pass → refactor. All tests MUST use JUnit 5 (`@Test`,
`@ParameterizedTest`, `@ExtendWith`). Commits that introduce implementation
without a preceding failing test are a constitution violation and MUST block
merge. Test coverage MUST not regress below the project baseline (defined per
feature in `plan.md`). Contract tests MUST accompany every new MCP server and
every new agent interface boundary.

**Rationale**: TDD forces explicit definition of "done" before writing
implementation. Contract tests ensure that integrations and agent boundaries
are validated independently of implementation detail, catching schema drift early.

### VII. Human-in-the-Loop Governance

Every AI agent action that creates, modifies, or deletes persistent state MUST
present a human-operator confirmation step before execution when running in
interactive mode. Content safety checks MUST run on all LLM-generated outputs
before they are surfaced to end-users. Outputs flagged by the Judge below the
configured safety threshold MUST be quarantined and escalated—they MUST NOT be
silently dropped or auto-approved. An immutable audit log of all HITL decisions
(approve / reject / escalate) MUST be maintained and retained for a minimum of
90 days.

**Rationale**: Autonomous agents can cause irreversible harm without human
oversight. HITL governance ensures accountability for agent actions, satisfies
enterprise compliance requirements, and provides the incident-response trail
needed to diagnose failures.

## Technology Stack & Constraints

All production services MUST be JVM-based (Java 21+). Build tooling MUST be
Maven or Gradle; the choice MUST be documented in the feature `plan.md` and
MUST NOT change mid-feature. Containerisation via Docker is REQUIRED for every
deployable service. Infrastructure-as-Code MUST accompany any new service
deployment. LLM provider integrations MUST be abstracted behind MCP servers;
no provider-specific SDK MUST appear in application or domain modules.

## Development Workflow

Feature branches MUST follow the naming convention `###-feature-name`
(e.g., `001-planner-core`). Every feature MUST progress through the phases:

1. **Spec** (`spec.md` approved) — Principle II gate.
2. **Plan** (`plan.md` with Constitution Check passed).
3. **Tasks** (`tasks.md` generated from plan).
4. **Implementation** (TDD per Principle VI).
5. **Review** (PR with constitution compliance verified).

No phase MAY be skipped. Pull requests MUST reference the spec document and
include evidence that the Constitution Check in `plan.md` passed. Failing tests
MUST block merge; warnings MUST be triaged before merge.

## Governance

This constitution supersedes all other development practices and informal
agreements within Project Chimera. Amendments require:

1. A written proposal describing the change and its rationale.
2. Approval by at least one senior maintainer.
3. A migration plan for existing code when the change is backward-incompatible.
4. A version bump applied per the versioning policy below.

**Versioning Policy**:
- **MAJOR**: Backward-incompatible principle removal or redefinition that
  invalidates existing implementations.
- **MINOR**: New principle or materially expanded guidance added.
- **PATCH**: Clarifications, wording refinements, or typo fixes.

**Compliance Review**: All PRs and code reviews MUST include a Constitution
Check verifying compliance with all seven principles. Non-compliant code MUST
NOT be merged. Complexity violations MUST be justified in the Complexity
Tracking table of `plan.md`. Use `CLAUDE.md` for runtime agent-development
guidance specific to the AI coding assistant.

**Version**: 1.0.0 | **Ratified**: 2026-03-08 | **Last Amended**: 2026-03-08
