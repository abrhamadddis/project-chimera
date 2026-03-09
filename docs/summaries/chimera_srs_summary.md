# Summary: Project Chimera SRS — Autonomous Influencer Network (PDF)

**Source**: `docs/pdf/Project Chimera SRS Document_ Autonomous Influencer Network.pdf`
**Type**: Software Requirements Specification (SRS) — authoritative design document
**Summarized**: 2026-03-08

---

## Key Concepts

### Strategic Objective
Transition from automated content scheduling to **Autonomous Influencer Agents** — persistent, goal-directed digital entities capable of perception, reasoning, creative expression, and **economic agency**. The system supports a fleet potentially numbering in the thousands, managed by a centralized Orchestrator but operating with significant individual autonomy.

### Two Defining Architectural Patterns (2026 Edition)
1. **Model Context Protocol (MCP)** — universal, standardized connectivity to the external world
2. **Swarm Architecture (FastRender Pattern)** — internal task coordination and parallel execution via Planner-Worker-Judge

Plus: **Agentic Commerce** via Coinbase AgentKit — non-custodial crypto wallets enabling agents to transact, earn, and manage resources on-chain without human intervention per micro-transaction.

### Operational Model: Fractal Orchestration
- Single human Super-Orchestrator manages AI **Manager Agents** who direct specialized **Worker Swarms**
- Enables solopreneur or small team to operate thousands of virtual influencers without cognitive overload
- Feasibility rests on **Self-Healing Workflows** (automated triage agents handle API timeouts, generation failures) and **Centralized Context Management** (BoardKit pattern — single `AGENTS.md` policy file propagates to entire fleet)

### Business Models
1. **Digital Talent Agency**: Own and operate in-house Chimera influencers as revenue assets (advertising, brand sponsorships, affiliate sales) — available 24/7, any niche/language, immune to human-talent scandals
2. **Platform-as-a-Service (PaaS)**: License "Chimera OS" to external brands to build/operate their own brand ambassadors — multi-tenant with data isolation
3. **Hybrid Ecosystem**: Flagship fleet demonstrates capability ("Alpha") while infrastructure is licensed to third parties — Coinbase AgentKit integration transforms agents into economic participants (negotiate deals, purchase assets, pay for compute)

### System Architecture — FastRender Swarm

#### The Planner (Strategist)
- Maintains "Big Picture" `GlobalState` (campaign goals, trends, budget)
- Generates a **DAG of tasks** required to achieve goals
- **Dynamic re-planning**: reactive to news events or Worker failures
- Can spawn **Sub-Planners** for complex domains (e.g., "Social Engagement Planner" for Twitter threading)

#### The Worker (Executor)
- Stateless, ephemeral — executes one atomic task from `TaskQueue`
- **Shared-nothing architecture** — Workers do not communicate with each other (prevents cascading failures)
- 50 comments → Planner spawns 50 Workers in parallel
- Workers are the primary consumers of **MCP Tools** (actual API calls)

#### The Judge (Gatekeeper)
- Reviews every Worker output against: Planner's acceptance criteria + persona constraints + safety guidelines
- Authority: **Approve** (commit to GlobalState) / **Reject** (signal Planner to retry) / **Escalate** (HITL queue)
- Implements **Optimistic Concurrency Control (OCC)**: checks `state_version` before committing — prevents ghost updates from stale state

### MCP Integration Layer
- **Hub-and-Spoke**: Central Orchestrator = MCP Host; MCP Servers = capability providers
- All external interactions MUST go through MCP — no direct SDK calls in agent core logic
- Example MCP servers: `mcp-server-twitter`, `mcp-server-weaviate`, `mcp-server-coinbase`
- MCP primitives: **Resources** (passive data sources, agent Perception), **Tools** (executable functions, agent Actions), **Prompts** (reusable reasoning templates)

### Functional Requirements

#### FR 1.x — Cognitive Core & Persona Management
- **SOUL.md**: Immutable agent DNA — backstory, voice/tone, core beliefs, hard behavioral directives
- **Hierarchical Memory**: Short-term (Redis, 1hr window) → Long-term (Weaviate semantic search) → Context Construction (dynamically assembled system prompt)
- **Dynamic Persona Evolution**: Judge triggers memory updates when high-engagement interactions are detected — agent "learns" its persona over time

#### FR 2.x — Perception System
- **Active Resource Monitoring**: Polls MCP Resources (twitter://mentions/recent, news://ethiopia/fashion/trends, market://crypto/eth/price)
- **Semantic Filter**: Lightweight LLM (Gemini 3 Flash) scores relevance against active goals — only content above 0.75 threshold triggers a Planner task
- **Trend Detection**: Background Worker clusters news over 4-hour windows → "Trend Alert" fed into Planner context

#### FR 3.x — Creative Engine
- **Multimodal Generation**: Text (Gemini 3 Pro / Claude Opus), Images (mcp-server-ideogram/midjourney), Video (mcp-server-runway/luma)
- **Character Consistency Lock**: All image generation MUST include `character_reference_id` or style LoRA ID — preserves visual identity across thousands of posts
- **Hybrid Video Rendering**: Tier 1 (daily) = Static Image + Motion Brush (cost-effective); Tier 2 (hero) = full Text-to-Video for campaign milestones

#### FR 4.x — Action System (Social Interface)
- **Platform-Agnostic Publishing**: All social actions via MCP Tools only — `twitter.post_tweet`, `instagram.publish_media`. Direct API calls from agent core are strictly prohibited
- **Bi-Directional Interaction Loop**: Ingest (mention) → Plan (reply task) → Generate (Worker) → Act (Tool call) → Verify (Judge safety check) → Publish

#### FR 5.x — Agentic Commerce
- **Non-Custodial Wallet**: Each agent has a unique wallet via Coinbase AgentKit; private key secured via AWS Secrets Manager / HashiCorp Vault; never logged
- **Autonomous On-Chain Transactions**: `native_transfer`, `deploy_token`, `get_balance`
- **CFO Sub-Agent (Budget Governor)**: Specialized Judge reviews every transaction; enforces configurable daily spend limits; anomaly detection → REJECT + HITL escalation

#### FR 6.x — Orchestration & Swarm Governance
- **Planner Service** → Redis `task_queue` → **Worker Pool** (stateless containers) → Redis `review_queue` → **Judge Service** → GlobalState commit
- **OCC**: Judge checks `state_version` hash before committing; if GlobalState has drifted, result is invalidated and task re-queued

### Non-Functional Requirements

#### NFR 1.x — HITL & Confidence Thresholds
- Every Worker output includes `confidence_score` (0.0–1.0) from LLM self-assessment
- **> 0.90**: Auto-Approve (no human intervention)
- **0.70–0.90**: Async Approval (pause + Orchestrator Dashboard queue)
- **< 0.70**: Auto-Reject + Planner retry with refined prompt
- **Sensitive Topics** (Politics, Health, Financial Advice, Legal Claims): ALWAYS HITL regardless of confidence score

#### NFR 2.x — Ethical & Transparency
- Platform-native AI labeling on all published content (`is_generated` / `ai_label` flags)
- **Honesty Directive**: If asked "Are you AI?", agent MUST disclose — overrides all persona constraints

#### NFR 3.x — Performance & Scalability
- Support minimum **1,000 concurrent agents** without Orchestrator degradation (stateless Orchestrator + clustered Weaviate/PostgreSQL)
- End-to-end latency for high-priority interactions: **≤ 10 seconds** (excl. HITL time)

### Data Schemas
- **Agent Task JSON**: `task_id` (UUID v4), `task_type`, `priority`, `context` (goal, persona constraints, required MCP resources), `assigned_worker_id`, `status`
- **MCP Tool Definition**: `name`, `description`, `inputSchema` (JSON Schema), `disclosure_level` enum (automated/assisted/none)

### Implementation Roadmap (Genesis Prompts)
- **Phase 1**: Core Swarm — Planner/Worker/Judge loop + Redis TaskQueue using Java Records for Task/Result schemas, Jedis for queuing
- **Phase 2**: MCP Integration — Java MCP SDK + MCPClient class with Stdio transport + `callTool(serverName, toolName, Map<String, Object>)` method
- **Phase 3**: Agentic Commerce — Coinbase AgentKit integration + `CdpEvmWalletProvider` + `@budget_check` AOP aspect + atomic Redis daily spend tracking

### Operational Environment
- **Compute**: AWS/GCP + Kubernetes auto-scaling for burst workloads
- **AI Inference**: Gemini 3 Pro / Claude Opus 4.5 (reasoning/judging) + Gemini 3 Flash / Haiku 3.5 (high-volume, low-latency tasks)
- **Data**: Weaviate (semantic memory) + PostgreSQL (transactional) + Redis (episodic cache/task queuing) + Base/Ethereum/Solana (ledger)

---

## Relevance to Project Chimera

- This is the **canonical system specification** — every feature spec (`specs/*.md`) must trace back to a requirement in this SRS
- The three-phase implementation roadmap (Core Swarm → MCP Integration → Agentic Commerce) defines the **natural feature ordering** for `tasks.md` generation
- The data schemas (Agent Task JSON, MCP Tool Definition) are the **contract tests** that JUnit 5 tests must validate (FR 4.1 in the challenge brief)

---

## Architectural Decisions It Forces

1. **Java Records are mandatory for all inter-agent DTOs** — Task, Result, AgentPersona, ConfidenceScore must all be Records (immutable, thread-safe, naturally compatible with OCC)
2. **Redis is the inter-service bus** — `task_queue` and `review_queue` are Redis queues; Jedis is the specified client library; this is a hard infrastructure dependency
3. **The Judge implements OCC with `state_version` hash checking** — this is not optional; without it, parallel Workers produce ghost updates on shared GlobalState
4. **SOUL.md is the persona contract** — the spec defines required fields (backstory, voice/tone, core beliefs, directives); implementing this as a Java Record backed by a parsed YAML-frontmatter Markdown file is the specified approach
5. **Confidence scoring is a required output field** on every Worker result — it is not a post-hoc annotation; the LLM must be prompted to produce it as part of its structured output
6. **CFO Sub-Agent is a separate Judge specialization** — budget governance is not an aspect of the main Judge; it is its own service with its own policy enforcement logic

---

## Open Questions for the Specs

- The SRS specifies Gemini 3 Pro / Claude Opus 4.5 for reasoning — but the constitution (Principle I) requires Java 21+. What is the **Java MCP SDK approach** for calling Gemini vs. Anthropic APIs, and should these be abstracted behind a unified `LLMClient` MCP server?
- The **Weaviate semantic memory** spec (FR 1.1) describes a multi-tiered retrieval process — what is the Java client library for Weaviate, and does one exist with sufficient maturity for production use?
- The **Character Consistency Lock** (FR 3.1) requires a `character_reference_id` or LoRA ID on all image generation requests — where is this ID stored (SOUL.md? PostgreSQL?), and what is the provisioning flow for a new agent?
- The SRS mentions Coinbase AgentKit primarily via Python SDK with MCP bridge — for the Java implementation, is the approach a **Python MCP server wrapping the Python AgentKit**, or is there a Java-native CDP SDK?
- **Horizontal scaling**: The SRS targets 1,000 concurrent agents on Kubernetes. What is the Java container packaging strategy (Spring Boot fat jar? Quarkus native? Plain JVM)? The choice has significant cold-start and memory implications at this scale
