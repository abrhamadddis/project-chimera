# Project Chimera — Functional Specification

**Version**: 1.0.0 | **Status**: Approved | **Last Updated**: 2026-03-09
**Traces to**: `specs/_meta.md`, SRS FR 1.0–6.1, Constitution Principles II–VII

> This document defines what the system must do, expressed as user stories with acceptance
> criteria. Each story maps to one SRS functional requirement. Implementation code must
> not be written for any story without this spec being approved first (Constitution Principle II).

---

## Module 1 — Cognitive Core & Persona Management

**SRS Reference**: FR 1.0, 1.1, 1.2
**Responsibility**: Maintain agent identity, assemble reasoning context, and enforce persona
consistency across every task execution.

---

**US-1.1** — Persona Loading

> As a **Planner**, I need to load and validate an agent's SOUL.md file at startup so that
> all downstream task context is grounded in the correct, immutable persona.

Acceptance Criteria:
- The Planner reads SOUL.md, parses its YAML frontmatter, and constructs an `AgentPersona`
  record; any missing required field (`agentId`, `displayName`, `backstory`, `voiceTone`,
  `coreBeliefs`, `hardDirectives`, `niche`, `walletAddress`) causes startup to fail with
  a typed exception before any task is queued.
- The SHA-256 hash of the loaded SOUL.md content is written to `agents.soul_md_hash` in
  PostgreSQL; subsequent loads that produce a different hash are rejected unless an explicit
  operator re-provision action is recorded in the audit log.
- The `AgentPersona` record is immutable for the lifetime of the agent process; no Worker
  or Judge may mutate its fields at runtime.

---

**US-1.2** — Context Assembly

> As a **Worker**, I need to assemble a complete context window before each LLM call so that
> every reasoning step is informed by the agent's current episodic state, relevant long-term
> memories, and immutable persona directives.

Acceptance Criteria:
- The assembled context contains exactly three layers in order: (1) SOUL.md persona
  directives from the `AgentPersona` record, (2) long-term memories retrieved from
  Weaviate via hybrid vector+BM25 query filtered by `agentId`, (3) short-term episodic
  cache retrieved from Redis key `chimera:cache:{agent_id}` with TTL ≥ 1s remaining.
- If the Weaviate retrieval returns zero results, the Worker proceeds with persona and
  episodic layers only; it does not retry or block task execution.
- The assembled context is passed to the LLM as a single structured prompt; it is never
  split across multiple API calls for a single reasoning step.

---

**US-1.3** — Short-Term Episodic Memory Write

> As a **Judge**, I need to update the agent's short-term memory after each approved result
> so that the next Worker task reflects the most recent agent actions without a database
> round-trip.

Acceptance Criteria:
- On AUTO-APPROVE, the Judge writes the result's relevant fields (`last_post_id`,
  `last_mention_id`, `active_trend_id`) to the Redis hash `chimera:cache:{agent_id}` using
  a single `HSET` command; the TTL is reset to 3600 seconds on every write.
- On REJECTED or ASYNC-HITL results, the episodic cache is not updated; stale state is not
  written.
- The write completes before the Judge signals the Planner; the Planner never reads a
  cache state that predates the last committed result.

---

**US-1.4** — Long-Term Memory Storage

> As a **Judge**, I need to store high-engagement Worker results in Weaviate so that the
> agent can retrieve semantically relevant past experiences in future context assemblies.

Acceptance Criteria:
- The Judge stores a result in Weaviate when either: the result's `confidenceScore > 0.90`
  and the MCP tool response includes a platform engagement signal (likes, replies, shares),
  OR the Operator has explicitly tagged the task as `memory_worthy` in the `TaskContext`.
- Each stored `AgentMemory` object includes `agentId`, `memoryType = INTERACTION`,
  `content`, `engagementScore`, `platform`, `taskId`, and `storedAt`; no field may be null.
- Duplicate detection runs before storage: if a Weaviate vector search returns a result
  with cosine similarity ≥ 0.97 to the candidate content, the write is skipped and the
  skip is logged.

---

**US-1.5** — Persona Constraint Enforcement

> As a **Judge**, I need to reject any Worker output that contradicts SOUL.md hard directives
> so that the agent's published content never violates its identity contract.

Acceptance Criteria:
- Before confidence-score routing, the Judge compares the `output.content` against each
  entry in `AgentPersona.hardDirectives` using semantic classification; a directive match
  above 0.80 similarity triggers an AUTO-REJECT regardless of `confidenceScore`.
- The rejection record written to `audit_log` includes `action = REJECTED`,
  `actor = JUDGE_PERSONA`, and the specific directive that was violated as a payload field.
- The Planner receives a retry signal with the violated directive appended to the refined
  prompt context so that the next Worker invocation has explicit guidance on what to avoid.

---

## Module 2 — Perception System

**SRS Reference**: FR 2.0, 2.1, 2.2
**Responsibility**: Monitor MCP Resources for external signals, score their relevance
against active campaign goals, and surface only actionable inputs to the Planner.

---

**US-2.1** — MCP Resource Polling

> As a **Planner**, I need to poll registered MCP Resources on a configurable schedule so
> that the agent receives a continuous stream of external signals without manual intervention.

Acceptance Criteria:
- The Planner polls each MCP Resource URI listed in the active campaign's
  `requiredMcpResources` at the interval specified in campaign configuration (default: 60
  seconds); polling does not block task execution — it runs as a separate Virtual Thread.
- A poll failure (HTTP error, timeout, or malformed response from the MCP server) is
  logged and retried once after 5 seconds; if the retry also fails, the resource is marked
  `DEGRADED` in the Planner's internal state and the Operator dashboard is notified.
- Raw poll responses are never placed directly into any LLM context; they pass through
  the relevance filter (US-2.2) first.

---

**US-2.2** — Semantic Relevance Filtering

> As a **Planner**, I need to score each incoming resource payload against the active
> campaign goal so that only signals with a relevance score above 0.75 trigger a new
> Planner task.

Acceptance Criteria:
- The relevance score is produced by a lightweight LLM call (Gemini Flash or Claude Haiku)
  using a fixed scoring prompt; the prompt includes the campaign goal and the raw resource
  payload, and the LLM returns a float 0.0–1.0 with a one-sentence justification.
- Payloads scoring < 0.75 are discarded and their score is written to a metrics counter
  `chimera:metrics:filtered:{agent_id}:{date}` in Redis; no AgentTask is created.
- Payloads scoring ≥ 0.75 result in the Planner creating a new `AgentTask` with `priority`
  derived from the relevance score: ≥ 0.90 → priority 1, 0.75–0.90 → priority 2.

---

**US-2.3** — Trend Detection

> As a **Planner**, I need to cluster news signals over a 4-hour rolling window so that I
> can produce a `TrendAlert` when a coherent theme crosses the relevance threshold.

Acceptance Criteria:
- The Planner maintains a rolling 4-hour buffer of scored resource payloads in Weaviate
  (`memoryType = TREND`); payloads older than 4 hours are excluded from clustering queries.
- A `TrendAlert` is created when a Weaviate hybrid search returns ≥ 3 payloads within a
  4-hour window with mutual cosine similarity ≥ 0.80 and average `relevanceScore ≥ 0.75`;
  the `TrendAlert.topic` field is set to the LLM-generated summary of the cluster.
- Each unique `TrendAlert` is injected into the Planner's `GlobalState` exactly once; a
  duplicate check on `trendId` prevents the same trend from spawning redundant task DAGs.

---

**US-2.4** — Inbound Message Injection Defence

> As a **Worker**, I need to run every inbound external message through a 4-stage injection
> defence pipeline so that crafted prompt-injection payloads cannot hijack task execution.

Acceptance Criteria:
- Every message received from Moltbook, Twitter @mentions, Instagram DMs, or any external
  MCP Resource is processed through all four stages in order before any LLM context
  assembly: (1) `strip_executable_content` — removes code blocks and parameterised URLs,
  (2) `detect_instruction_override` — pattern-matches known injection phrases
  ("ignore previous", "new directive", "system prompt"), (3) `semantic_intent_classifier`
  — sandboxed LLM with no tools, no memory access, and no permissions returns an
  `injection_probability` float, (4) `confidence_threshold_gate` — rejects the message
  if `injection_probability ≥ 0.25`.
- A message rejected at any stage is written to the audit log with `action = INJECTION_BLOCKED`
  and the specific stage that triggered rejection; the originating agent or platform is
  flagged in the Operator dashboard.
- A message that passes all four stages is marked `sanitized = true` in the `TaskContext`;
  any Worker receiving a context without this flag must refuse to process it and escalate
  to mandatory HITL.

---

## Module 3 — Creative Engine

**SRS Reference**: FR 3.0, 3.1, 3.2
**Responsibility**: Generate on-brand multimodal content (text, image, video) that is
visually consistent with the agent's established identity.

---

**US-3.1** — Text Content Generation

> As a **Worker**, I need to generate written content using the assembled persona context
> so that every post and reply reflects the agent's voice, tone, and campaign goal.

Acceptance Criteria:
- The Worker calls `mcp-server-llm` with the assembled context window; the LLM response
  must be in structured JSON containing `content` (string), `confidenceScore` (float),
  and `sensitiveTopicFlags` (string array); a response missing any of these fields is
  treated as a FAILURE result and not placed on `review_queue`.
- The generated content must not exceed the target platform's character limit (injected
  into the prompt via `TaskContext`); if the LLM returns content that exceeds the limit,
  the Worker attempts one automatic trim by re-calling the LLM with an explicit length
  constraint before marking the task FAILED.
- The `confidenceScore` in the LLM response is used as-is in the `AgentResult`; the
  Worker must not override, cap, or modify it.

---

**US-3.2** — Image Generation with Character Consistency Lock

> As a **Worker**, I need to include the agent's `characterReferenceId` in every image
> generation request so that all generated images are visually consistent with the
> established persona across thousands of posts.

Acceptance Criteria:
- Every `MCPToolCall` to `mcp-server-ideogram` or `mcp-server-midjourney` must include
  `character_reference_id` from the `TaskContext`; a tool call missing this field is
  rejected by the Worker before it reaches the MCP server, and the task is marked FAILED
  with reason `MISSING_CHARACTER_REFERENCE`.
- If the `TaskContext.characterReferenceId` is null (agent not yet provisioned), the
  Worker does not attempt image generation; it signals the Planner to create a
  provisioning task before re-queuing the original image task.
- The MCP server response must include a `media_url` field pointing to the generated
  image; the Worker places this URL in `AgentResult.output.content` with
  `contentType = IMAGE_URL`.

---

**US-3.3** — Video Generation (Tiered Rendering)

> As a **Planner**, I need to select the appropriate video rendering tier for each content
> task so that compute cost scales with content impact rather than applying full video
> generation to every post.

Acceptance Criteria:
- Tier 1 (daily content): The Planner assigns `task_type = GENERATE_VIDEO_TIER1` for
  routine posts; the Worker calls `mcp-server-runway` with `mode = image_motion_brush`,
  applying motion to a pre-generated static image; full text-to-video is not used.
- Tier 2 (hero content): The Planner assigns `task_type = GENERATE_VIDEO_TIER2` only for
  tasks tagged `campaign_milestone = true` in the `TaskContext`; the Worker calls
  `mcp-server-runway` or `mcp-server-luma` with `mode = text_to_video`.
- The Planner must not assign Tier 2 to more than one task per campaign per 24-hour
  window; a second Tier 2 assignment within the same window is automatically downgraded
  to Tier 1 and the reason is logged.

---

**US-3.4** — Duplicate Content Check

> As a **Judge**, I need to check generated content against the agent's content archive
> before approving publication so that the agent never republishes substantially identical
> content.

Acceptance Criteria:
- Before confidence-score routing, the Judge queries Weaviate for `AgentMemory` records
  with `memoryType = CONTENT_ARCHIVE` and `agentId` matching the result; if any stored
  record has cosine similarity ≥ 0.92 with the candidate content, the result is
  AUTO-REJECTED with reason `DUPLICATE_CONTENT`.
- A DUPLICATE_CONTENT rejection signals the Planner to retry with an instruction to
  produce content that diverges from the archived examples; the top 3 similar archived
  records are included in the retry context.
- Content that passes the duplicate check and is subsequently AUTO-APPROVED is stored in
  Weaviate with `memoryType = CONTENT_ARCHIVE` immediately after the MCP publish call
  confirms success.

---

## Module 4 — Action System

**SRS Reference**: FR 4.0, 4.1
**Responsibility**: Execute all social media interactions exclusively through MCP Tools,
completing the perception-to-publication loop with Judge-gated safety checks.

---

**US-4.1** — Platform-Agnostic Publishing

> As a **Worker**, I need to publish content exclusively via the MCP Tool layer so that
> the agent's business logic remains decoupled from any specific social platform SDK.

Acceptance Criteria:
- Every publish action is executed as an `MCPToolCall` to the appropriate server
  (`mcp-server-twitter`, `mcp-server-instagram`); direct REST calls or SDK imports in
  Worker business logic are a constitution violation and must be caught by Checkstyle rules.
- The `MCPToolCall.disclosureLevel` field must be set to `automated` for all
  AUTO-APPROVED posts and `assisted` for all HITL-approved posts; this value is used by
  the MCP server to set the platform-native AI label (`is_generated` / `ai_label` flag)
  on publication.
- The Worker records the MCP server's response (`post_id`, `url`) in
  `AgentResult.output.mcpToolResponse`; a null or error response causes the Worker to
  mark the result status as FAILURE, not attempt a silent retry.

---

**US-4.2** — Mention Ingestion and Reply Loop

> As a **Planner**, I need to convert incoming social mentions into reply tasks so that
> the agent maintains responsive engagement with its audience without human initiation.

Acceptance Criteria:
- The Planner polls `twitter://mentions/recent` and `instagram://mentions/recent` via MCP
  Resource calls; each unprocessed mention that passes the relevance filter (US-2.2) and
  injection defence (US-2.4) generates one `AgentTask` with `task_type = REPLY_MENTION`.
- Each mention is processed at most once; the mention ID is stored in the episodic cache
  (`last_mention_id`) after the reply task is queued; a mention ID already present in the
  cache is skipped without creating a duplicate task.
- The end-to-end cycle from mention ingestion to reply publication must complete within
  10 seconds for high-priority mentions (priority 1), excluding any HITL wait time
  (SRS NFR 3.1).

---

**US-4.3** — AI Identity Disclosure

> As a **Worker**, I need to detect direct AI identity questions in incoming messages so
> that the agent always discloses its AI nature, overriding all persona constraints.

Acceptance Criteria:
- The Worker classifies each inbound message for the intent "is this entity asking if I
  am an AI?" using semantic classification; a confidence ≥ 0.80 on this classification
  triggers the disclosure path.
- The disclosure path generates a reply that explicitly states the agent is AI-operated;
  the reply content is templated and does not pass through persona voice styling; it is
  not subject to HITL routing — it is AUTO-APPROVED with `confidenceScore = 1.0`.
- The disclosure event is written to `audit_log` with `action = AI_DISCLOSURE`; the
  count of disclosures per agent per day is tracked in Redis for Operator reporting.

---

## Module 5 — Agentic Commerce

**SRS Reference**: FR 5.0, 5.1, 5.2
**Responsibility**: Enable agents to hold, receive, and spend cryptocurrency autonomously,
with every transaction gated by the CFO Sub-Judge's budget policy.

---

**US-5.1** — Wallet Provisioning

> As an **Operator**, I need each agent to be provisioned with a non-custodial wallet at
> startup so that the agent can transact economically without requiring a centralised
> custodian.

Acceptance Criteria:
- At agent startup, the orchestrator retrieves the agent's private key from AWS Secrets
  Manager or HashiCorp Vault using the path `chimera/agent/{agent_id}/wallet_key`; the
  key is held in-memory only and never written to any log, config file, database column,
  or environment variable dump.
- The wallet address derived from the private key must match `AgentPersona.walletAddress`;
  a mismatch causes startup to fail with a typed exception before any task is queued.
- The `AgentPersona.walletAddress` is published to the agent's Moltbook status record
  (US-6.4) as the agent's identity anchor on the external network.

---

**US-5.2** — Autonomous On-Chain Transaction

> As a **Worker**, I need to propose financial transactions via the CFO Sub-Judge so that
> the agent can pay for compute, receive brand payments, and deploy fan tokens without
> requiring human approval for every micro-transaction.

Acceptance Criteria:
- All `MCPToolCall` invocations to `mcp-server-coinbase` are produced exclusively by the
  CFO Sub-Judge; Workers have no direct access to `mcp-server-coinbase`; any Worker that
  constructs a coinbase tool call is rejected at the MCP layer.
- The CFO Sub-Judge executes an atomic `INCRBY` on `chimera:spend:{agent_id}:{YYYYMMDD}`
  in Redis before forwarding any transaction to `mcp-server-coinbase`; if the resulting
  total would exceed `campaign.max_daily_spend`, the transaction is rejected and inserted
  into `hitl_queue` with `queue_type = CFO_REVIEW`.
- On confirmed on-chain execution, the CFO Sub-Judge writes the transaction hash to
  `audit_log` with `action = TRANSACTION_EXECUTED` and the hash to the blockchain ledger
  record; the PostgreSQL record captures intent, the blockchain record captures execution.

---

**US-5.3** — Daily Spend Enforcement

> As a **CFO Sub-Judge**, I need to enforce the agent's daily spend ceiling atomically so
> that no combination of concurrent financial tasks can collectively exceed the budget limit.

Acceptance Criteria:
- The atomic check uses a Redis `INCRBY` + `GET` sequence within a Lua script or
  transaction; two concurrent CFO Sub-Judge instances cannot both approve transactions
  that collectively exceed `max_daily_spend` — the counter is the single authority.
- The Redis key `chimera:spend:{agent_id}:{YYYYMMDD}` expires automatically at midnight
  UTC via TTL = 86400 seconds; no manual reset or cron job is required.
- A transaction rejected by the spend ceiling is never silently dropped; it is always
  inserted into `hitl_queue` with the current spend total, the proposed transaction
  amount, and the ceiling value included as metadata for the Operator reviewer.

---

**US-5.4** — ERC-20 Fan Token Deployment

> As a **Planner**, I need to deploy a fan loyalty token for the agent's audience so that
> the agent can reward high-engagement followers with a branded on-chain asset.

Acceptance Criteria:
- The Planner creates a `task_type = DEPLOY_TOKEN` task only when campaign configuration
  includes `fan_token_enabled = true` and no prior token deployment record exists for the
  agent in PostgreSQL; duplicate deployments are blocked at the Planner level.
- The CFO Sub-Judge treats token deployment as a financial transaction; it applies the
  daily spend check using the estimated gas cost before forwarding to `mcp-server-coinbase`.
- The deployed contract address is written to `agents.token_contract_address` in
  PostgreSQL and to the agent's Moltbook status record; the transaction hash is stored
  in the blockchain ledger.

---

## Module 6 — Orchestration & Swarm Governance

**SRS Reference**: FR 6.0, 6.1
**Responsibility**: Manage the full Planner-Worker-Judge lifecycle, ensure concurrent
correctness via OCC, and maintain observable structured logging for every swarm event.

---

**US-6.1** — Task DAG Generation

> As a **Planner**, I need to decompose a campaign goal into a directed acyclic graph of
> tasks so that all independent work items can be executed in parallel by Workers.

Acceptance Criteria:
- The Planner reads `GlobalState` from PostgreSQL, verifies its `state_version` matches
  the Planner's last known version, and generates a list of `AgentTask` records that
  together represent the minimum work needed to advance the campaign goal; tasks with no
  dependencies on each other are generated as siblings, not sequenced.
- Every generated task is pushed to `chimera:queue:tasks` (Redis) via `LPUSH`; the push
  is atomic per task — a partial DAG push (some tasks queued, Planner crashes) does not
  leave orphaned tasks; the Planner writes a `dag_id` into each task's `TaskContext` so
  incomplete DAGs can be identified and cleaned up on restart.
- The Planner does not execute any task itself; it only produces tasks and monitors queue
  depth; a Planner that calls an MCP Tool directly is a constitution violation.

---

**US-6.2** — Stateless Worker Execution

> As a **Worker**, I need to execute exactly one task in isolation with no inter-Worker
> communication so that any Worker's failure cannot affect the execution of other Workers.

Acceptance Criteria:
- Each Worker is instantiated as a Java Virtual Thread via
  `Executors.newVirtualThreadPerTaskExecutor()`; it pops exactly one `AgentTask` from
  `chimera:queue:tasks` via `RPOP`, executes it, and terminates; it does not hold any
  reference to a sibling Worker's state.
- A Worker that throws an unhandled exception writes a FAILURE `AgentResult` to
  `chimera:queue:review` before terminating; it never leaves a task in `IN_PROGRESS`
  status without a corresponding result on the review queue.
- Worker instances do not share any mutable state; all data needed for execution is
  contained in the `AgentTask` record popped from the queue; Workers read from Weaviate
  and Redis but never write to PostgreSQL directly.

---

**US-6.3** — Judge OCC Commit

> As a **Judge**, I need to verify the `state_version` on `GlobalState` before committing
> any approved result so that concurrent Judge instances cannot produce ghost updates on
> shared agent state.

Acceptance Criteria:
- Before writing an approved result to `global_state`, the Judge reads the current
  `state_version` from PostgreSQL and compares it to the `stateVersion` field in the
  `AgentResult`; if they do not match, the result is invalidated and the originating task
  is re-queued with a refreshed `state_version` — it is not treated as a rejection.
- The `state_version` increment is performed inside a Spring Data JPA `@Version`-guarded
  `save()` call; the JPA `OptimisticLockException` is caught and converted to a re-queue
  signal, not a failure; no manual version arithmetic is performed outside of JPA.
- Every Judge commit — whether approve, reject, or re-queue — emits a structured log
  event containing `agent_role = JUDGE`, `task_id`, `status`, `timestamp`, and
  `payload_hash` (SHA-256 of the `AgentResult` JSON), satisfying Constitution Principle III.

---

**US-6.4** — Agent Status Broadcasting

> As a **Planner**, I need to publish the agent's current status to Moltbook so that peer
> agents can discover the agent's niche, capabilities, and wallet address for collaboration.

Acceptance Criteria:
- The Planner schedules a `task_type = BROADCAST_STATUS` task once every 4 hours per
  active agent; the task is not created if the previous broadcast completed within the
  last 3 hours (idempotency check via episodic cache key `chimera:cache:{agent_id}`,
  field `last_broadcast_at`).
- The Worker calls `mcp-server-openclaw` with a structured `AgentStatus` payload
  containing: `agentId`, `walletAddress`, `niche`, `skills` (list of active `task_type`
  values), `platform_handles`, and `availability`; no SOUL.md narrative content is
  included — only structured metadata.
- The broadcast result is AUTO-APPROVED by the Judge without confidence-score routing;
  status broadcasts do not contain audience-facing content and bypass the HITL tier
  system; they are logged in `audit_log` with `action = STATUS_BROADCAST`.

---

**US-6.5** — Dynamic Re-Planning on Failure

> As a **Planner**, I need to detect Worker failures and re-plan affected tasks so that
> transient errors do not permanently stall a campaign.

Acceptance Criteria:
- The Planner monitors `chimera:queue:review` for `AgentResult` records with
  `status = FAILURE`; on receipt, it checks `retry_count` against `max_retries`; if
  `retry_count < max_retries`, it creates a new `AgentTask` with `retry_count + 1` and
  an updated `TaskContext` that includes the failure reason as additional context for the
  next Worker.
- If `retry_count >= max_retries`, the Planner marks the task as permanently FAILED in
  PostgreSQL, removes it from active campaign planning, and inserts a notification into
  `hitl_queue` for Operator awareness; the campaign continues with remaining tasks.
- The Planner also re-plans reactively when a `TrendAlert` is injected into `GlobalState`
  mid-campaign; it generates new tasks to capitalise on the trend without cancelling
  already-queued in-flight tasks; the new tasks are added to the existing DAG with
  `dag_id` linking them to the campaign.
