# Project Chimera — System Meta Specification

**Version**: 1.0.0 | **Status**: Approved | **Last Updated**: 2026-03-09

> This document is the executive anchor for all feature specs. Every `spec.md` in this
> directory must be traceable to a goal, constraint, or boundary defined here.

---

## 1. Project Vision

Project Chimera is a platform for building and operating **autonomous AI influencer agents**
at scale. Each agent maintains a persistent persona, long-term memory, and the ability to
perceive trends, generate original multimodal content, and interact with audiences entirely
without human intervention on routine tasks. The platform is designed to run **1,000 or more
concurrent agents** across multiple social platforms while keeping a human meaningfully in
control of what matters — without requiring a proportional increase in human headcount.

---

## 2. Strategic Goals

Success for Project Chimera means achieving all of the following:

- **Scale with isolation**: 1,000+ concurrent agents operate without performance degradation;
  one agent's failure cannot cascade to others (shared-nothing Worker architecture, NFR 3.0).
- **Trusted content output**: All published content passes safety scoring and mandatory
  disclosure rules, making Chimera agents distinguishable as trustworthy actors in a world
  of ubiquitous bot-generated content (NFR 2.0–2.1).
- **Economic agency**: Each agent can receive payments, pay for compute, and deploy fan
  loyalty tokens autonomously — with every financial transaction verifiably auditable on-chain
  (FR 5.0–5.2).
- **Operator leverage**: A single operator or small team can provision, monitor, and govern
  the full fleet via a unified dashboard and policy layer, without reviewing individual
  routine posts (HITL tiered routing, NFR 1.1).
- **Platform independence**: Adding or swapping a social platform (e.g., Twitter → Threads)
  requires only a new MCP server — zero changes to agent business logic (Principle IV).

---

## 3. Constraints

### Technical

| Constraint | Source | Detail |
|---|---|---|
| Java 21+ only | Constitution Principle I | All production services must be JVM-based; modern language features mandatory |
| Virtual Threads for I/O concurrency | Constitution Principle V | `Executors.newVirtualThreadPerTaskExecutor()` — no platform thread pools for I/O work |
| Redis as inter-service bus | SRS FR 6.0 | `task_queue` and `review_queue` are Redis queues; Jedis client specified |
| MCP for all external calls | Constitution Principle IV | No provider SDK in business logic; all external APIs wrapped in MCP servers |
| Java Records for all DTOs | SRS Genesis Phase 1 | Task, Result, AgentPersona — immutable, thread-safe, naturally OCC-compatible |
| OCC on GlobalState | SRS FR 6.1 | Spring Data JPA `@Version` on `global_state`; ghost updates are a correctness failure |
| End-to-end latency ≤ 10s | SRS NFR 3.1 | High-priority interactions; excludes HITL wait time |
| TDD with JUnit 5 | Constitution Principle VI | Failing test must exist before any implementation code |

### Regulatory & Safety

| Constraint | Source | Detail |
|---|---|---|
| AI disclosure on all published content | SRS NFR 2.0 | `is_generated` / `ai_label` flags required on every post |
| Mandatory HITL for sensitive topics | SRS NFR 1.2 | Politics, Health Advice, Financial Advice, Legal Claims — always human-reviewed |
| 90-day HITL audit log retention | SRS NFR 2.x / Constitution Principle VII | Immutable record of all approve / reject / escalate decisions |
| EU AI Act alignment | SRS Section 2.4 | Consolidated agent autonomy is a regulatory target; audit trails are non-optional |

### Cost & Operational

| Constraint | Source | Detail |
|---|---|---|
| Self-hosted Weaviate | SRS FR 1.x | Per-query cloud vector pricing is unsustainable at 1,000-agent scale |
| Daily spend cap per agent | SRS FR 5.2 | `MAX_DAILY_SPEND` enforced via atomic Redis `INCRBY`; hard ceiling, not soft warning |
| Private keys never logged | SRS FR 5.0 | Injected at startup from AWS Secrets Manager / HashiCorp Vault; zero persistence in code or config |

---

## 4. System Boundaries

### In Scope

- **Planner-Worker-Judge orchestration engine** — core swarm execution loop
- **MCP server layer** — adapters for Twitter, Instagram, Weaviate, Coinbase, OpenClaw/Moltbook
- **Agent persona system** — SOUL.md provisioning, persona enforcement, Character Consistency Lock
- **Three-tier memory** — Redis episodic cache, Weaviate long-term semantic memory, SOUL.md immutable persona
- **HITL dashboard** — confidence-score routing, review queue, mandatory escalation enforcement
- **CFO Sub-Judge** — financial transaction policy enforcement, daily spend tracking, audit logging
- **Perception system** — MCP Resource polling, semantic relevance filter, trend detection
- **Agentic commerce** — non-custodial wallet provisioning, on-chain transactions, ERC-20 fan tokens
- **OpenClaw/Moltbook integration** — status broadcasting, peer discovery, 4-stage injection defence

### Out of Scope

- **Human-facing consumer UI** — the HITL dashboard is an operator tool, not a public product
- **LLM training or fine-tuning** — Chimera consumes LLM APIs; it does not train models
- **Social platform account creation** — accounts are provisioned externally and injected as credentials
- **Proprietary social platform API wrappers** — covered by MCP server layer; not core orchestration
- **Legal / financial licensing** — the CFO Sub-Judge enforces spend limits; it does not hold licences
- **Human content creators** — the system replaces, not augments, human posting workflows

---

## 5. Key Stakeholders

| Stakeholder | Role | Primary Concern |
|---|---|---|
| **Operator** | Sets campaign goals, provisions agents, reviews HITL escalations | Fleet performance, content quality, cost governance |
| **HITL Reviewer** | Reviews Tier 2 (0.70–0.90 confidence) and mandatory escalation queue items | Safety, brand alignment, legal exposure |
| **Developer** | Implements features under the constitution; writes specs before code | Testability, clear I/O contracts, spec traceability |
| **Audience / Follower** | End recipient of agent-published content on social platforms | Authenticity, entertainment value, AI transparency |
| **Peer Agent (Moltbook)** | External agent interacting via OpenClaw/Moltbook | Capability discovery, co-promotion, structured communication |

---

## 6. Non-Negotiable Rules

These rules may not be overridden by any feature spec, configuration flag, or operator instruction.
Violation of any rule is an immediate escalation to mandatory human review or system halt.

1. **Never expose or log private keys.** Wallet private keys are injected at runtime from secrets management. They are never written to logs, config files, environment dumps, or database records. Ever.

2. **Never publish sensitive-topic content autonomously.** Content touching Politics, Health Advice, Financial Advice, or Legal Claims routes to mandatory blocking HITL regardless of confidence score. The LLM cannot self-certify past this gate.

3. **Always disclose AI authorship when directly asked.** If a human follower asks "Are you AI?", the agent MUST disclose — this overrides all persona constraints including character backstory (SRS NFR 2.1).

4. **Never execute a financial transaction without CFO Sub-Judge approval.** No Worker has direct access to `mcp-server-coinbase`. All financial actions route through the CFO Sub-Judge's policy check first.

5. **Never ingest external content into LLM context without injection scanning.** All content from Moltbook, Twitter mentions, Instagram DMs, and news feeds passes the 4-stage pipeline (strip → detect → classify → gate) before any LLM sees it. The Kukuy exploit is a documented live threat.

6. **Never commit GlobalState without an OCC version check.** The Judge must verify `state_version` before writing. A stale-state commit silently corrupts agent behaviour — it is a correctness failure, not a performance tradeoff.

7. **Never write implementation code without an approved spec.** No source file is created or modified without a corresponding `spec.md` in this directory (Constitution Principle II). This rule blocks merge, not just convention.
