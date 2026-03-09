# Project Chimera — Task Breakdown

**Generated**: 2026-03-09
**Sources**: `specs/_meta.md`, `specs/functional.md`, `specs/technical.md`
**Constitution**: v1.1.0

> Tasks are ordered within each module by dependency chain. HIGH priority tasks block
> subsequent modules or are safety/correctness requirements. No implementation task
> may begin without reading the relevant spec file (Constitution Prime Directive).

---

## Foundation — Cross-Cutting Infrastructure

These tasks have no dependencies on each other and must be completed before any module
work begins. All are **HIGH** priority.

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| F-1 | Define all Java Records: `AgentTask`, `TaskContext`, `AgentResult`, `ResultOutput`, `AgentPersona`, `TrendAlert` with compact constructor validation | `technical.md §2.1–2.4` | HIGH | — |
| F-2 | Define all enums: `TaskType`, `TaskStatus`, `ResultStatus`, `ContentType`, `SensitiveTopic`, `AgentAvailability` | `technical.md §2.1–2.2` | HIGH | — |
| F-3 | Bootstrap PostgreSQL schema: `agents`, `campaigns`, `global_state`, `audit_log`, `hitl_queue` tables | `technical.md §3.1` | HIGH | — |
| F-4 | Bootstrap Weaviate `AgentMemory` collection with vectoriser config and hybrid search settings | `technical.md §3.3` | HIGH | — |
| F-5 | Configure Redis Cluster: key namespace `chimera:{namespace}:{id}`, Jedis connection pool | `technical.md §3.2` | HIGH | — |
| F-6 | Implement `MCPClient` wrapper: `callTool(server, tool, arguments)` with audit linkage fields | `technical.md §1.3`, `specs/_meta.md §3` | HIGH | F-1 |
| F-7 | Configure Jackson: `SnakeCaseStrategy`, JSR310 module, `@Nullable` handling | `technical.md §1` | HIGH | — |
| F-8 | Configure Checkstyle rules: ban direct SDK imports in `com.chimera.worker.*`, enforce Records for DTOs | `technical.md §5.4`, `specs/_meta.md §3` | HIGH | — |

---

## Module 1 — Cognitive Core & Persona Management

**SRS**: FR 1.0, 1.1, 1.2 | **Functional spec**: US-1.1–US-1.5

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| M1-1 | **SOUL.md Parser**: Read YAML frontmatter, construct `AgentPersona` record, fail fast on missing required fields | `functional.md US-1.1`, `technical.md §2.3` | HIGH | F-1, F-3 |
| M1-2 | **SOUL.md Hash Verification**: Compute SHA-256 of SOUL.md content, write to `agents.soul_md_hash`, reject mismatches on reload | `functional.md US-1.1` | HIGH | M1-1, F-3 |
| M1-3 | **Context Window Assembler**: Compose 3-layer prompt in order — (1) SOUL.md persona, (2) Weaviate memories, (3) Redis episodic cache | `functional.md US-1.2`, `technical.md §3.2–3.3` | HIGH | M1-1, F-4, F-5 |
| M1-4 | **Episodic Cache Read**: `HGETALL chimera:cache:{agent_id}` with TTL ≥ 1s guard; graceful empty-cache fallback | `functional.md US-1.2`, `technical.md §3.2` | HIGH | F-5 |
| M1-5 | **Long-Term Memory Retrieval**: Weaviate hybrid query (vector + BM25, alpha=0.75) filtered by `agentId` and `memoryType` | `functional.md US-1.2`, `technical.md §3.3` | HIGH | F-4 |
| M1-6 | **Episodic Cache Write (Judge)**: `HSET chimera:cache:{agent_id}` with TTL reset on AUTO-APPROVE only; skip on REJECTED/HITL | `functional.md US-1.3`, `technical.md §3.2` | HIGH | M1-4 |
| M1-7 | **Long-Term Memory Write (Judge)**: Store `AgentMemory` to Weaviate on high-engagement signal; dedup check at 0.97 cosine similarity | `functional.md US-1.4`, `technical.md §3.3` | MEDIUM | M1-5, F-4 |
| M1-8 | **Persona Constraint Enforcement (Judge)**: Semantic classify `output.content` vs each `hardDirective`; AUTO-REJECT at ≥ 0.80 match; log `PERSONA_VIOLATION` to `audit_log` | `functional.md US-1.5`, `specs/_meta.md §6 Rule 2` | HIGH | M1-1, F-3 |

---

## Module 2 — Perception System

**SRS**: FR 2.0, 2.1, 2.2 | **Functional spec**: US-2.1–US-2.4

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| M2-1 | **MCP Resource Poller**: Background Virtual Thread scheduler polling each `requiredMcpResources` URI at configurable interval (default 60s); DEGRADED state + Operator alert on double failure | `functional.md US-2.1`, `technical.md §5.1` | HIGH | F-6 |
| M2-2 | **Semantic Relevance Filter**: LLM call (Claude Haiku) with fixed scoring prompt; route ≥ 0.75 to task creation; write filtered scores to `chimera:metrics:filtered:{agent_id}:{date}` | `functional.md US-2.2`, `technical.md §5.3` | HIGH | M2-1, F-5 |
| M2-3 | **Priority Mapper**: Derive `AgentTask.priority` from relevance score (≥ 0.90 → 1; 0.75–0.90 → 2) | `functional.md US-2.2`, `technical.md §1.1` | HIGH | M2-2 |
| M2-4 | **4-Stage Injection Defence Pipeline**: Implement all four stages — `strip_executable_content`, `detect_instruction_override`, `semantic_intent_classifier` (sandboxed Claude Haiku: no tools, no memory), `confidence_threshold_gate` (0.25 threshold); log `INJECTION_BLOCKED` to `audit_log`; silent rejection to sender | `functional.md US-2.4`, `specs/openclaw_integration.md §2 Path 2`, `specs/_meta.md §6 Rule 5` | HIGH | F-3, F-6 |
| M2-5 | **Sanitized Flag Enforcement**: Workers reject `AgentTask` where `context.sanitized != true`; escalate to mandatory HITL | `functional.md US-2.4`, `technical.md §1.1` | HIGH | M2-4 |
| M2-6 | **Trend Detection**: Store scored payloads to Weaviate (`memoryType = TREND`); hybrid cluster query over 4-hour rolling window; emit `TrendAlert` when ≥ 3 payloads with cosine ≥ 0.80 and avg relevance ≥ 0.75 | `functional.md US-2.3`, `technical.md §2.4, §3.3` | MEDIUM | M2-2, F-4 |
| M2-7 | **TrendAlert Dedup in GlobalState**: Check `trendId` against `global_state.state_json` before injecting; block duplicate trend DAGs | `functional.md US-2.3`, `technical.md §3.1` | MEDIUM | M2-6, M6-3 |

---

## Module 3 — Creative Engine

**SRS**: FR 3.0, 3.1, 3.2 | **Functional spec**: US-3.1–US-3.4

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| M3-1 | **Text Content Generation Worker**: Call `mcp-server-llm` with assembled context; parse structured JSON response (`content`, `confidenceScore`, `sensitiveTopicFlags`); auto-trim on character limit breach (one retry); FAILURE if response missing required fields | `functional.md US-3.1`, `technical.md §1.2` | HIGH | M1-3, F-6 |
| M3-2 | **Duplicate Content Check (Judge)**: Weaviate query for `CONTENT_ARCHIVE` at ≥ 0.92 cosine similarity; AUTO-REJECT with `DUPLICATE_CONTENT`; include top-3 similar records in retry context | `functional.md US-3.4`, `technical.md §3.3` | HIGH | F-4, M6-3 |
| M3-3 | **Content Archive Write**: On AUTO-APPROVE after MCP publish confirms success, write to Weaviate (`memoryType = CONTENT_ARCHIVE`) | `functional.md US-3.4`, `technical.md §3.3` | HIGH | M3-2 |
| M3-4 | **Image Generation Worker**: Validate `characterReferenceId` present in `TaskContext` before `MCPToolCall` to `mcp-server-ideogram`; FAILED with `MISSING_CHARACTER_REFERENCE` if null; signal provisioning task if agent not yet provisioned | `functional.md US-3.2`, `technical.md §1.3` | MEDIUM | M1-1, F-6 |
| M3-5 | **Character Reference Provisioning Flow**: Detect null `characterReferenceId` in `AgentPersona`; Planner creates provisioning task before re-queuing image tasks | `functional.md US-3.2`, `technical.md §3.1` | MEDIUM | M3-4, M6-1 |
| M3-6 | **Video Generation — Tier 1 Worker**: Call `mcp-server-runway` with `mode = image_motion_brush` on static image; used for all routine `GENERATE_VIDEO_TIER1` tasks | `functional.md US-3.3`, `technical.md §1.1` | LOW | M3-4, F-6 |
| M3-7 | **Video Generation — Tier 2 Worker**: Call `mcp-server-runway` or `mcp-server-luma` with `mode = text_to_video`; enforce `campaign_milestone_quota` (max 1 per 24h); auto-downgrade second assignment to Tier 1 | `functional.md US-3.3`, `technical.md §3.1` | LOW | M3-6 |

---

## Module 4 — Action System

**SRS**: FR 4.0, 4.1 | **Functional spec**: US-4.1–US-4.3

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| M4-1 | **Platform-Agnostic Publishing Worker**: `MCPToolCall` to `mcp-server-instagram` / `mcp-server-twitter`; set `disclosureLevel` = `automated` (AUTO-APPROVE) or `assisted` (HITL-approve); record `post_id` + `url` in `mcpToolResponse`; FAILURE on null/error response | `functional.md US-4.1`, `technical.md §1.3`, `specs/_meta.md §6 Rule 3` | HIGH | M1-3, F-6, M3-1 |
| M4-2 | **Mention Ingestion Poller**: Poll `twitter://mentions/recent` + `instagram://mentions/recent` via MCP Resource; pass through M2-2 relevance filter and M2-4 injection pipeline; create `REPLY_MENTION` task per unprocessed mention | `functional.md US-4.2`, `technical.md §1.1` | HIGH | M2-1, M2-2, M2-4 |
| M4-3 | **Mention Deduplication**: After queuing reply task, write `last_mention_id` to episodic cache; skip mentions already in cache | `functional.md US-4.2`, `technical.md §3.2` | HIGH | M4-2, M1-6 |
| M4-4 | **AI Identity Disclosure Detector**: Semantic classify inbound messages for disclosure intent; trigger at ≥ 0.80; generate templated reply (no persona styling); AUTO-APPROVE with `confidenceScore = 1.0`; log `AI_DISCLOSURE` to `audit_log`; Redis counter `chimera:disclosure:{agent_id}:{date}` | `functional.md US-4.3`, `specs/_meta.md §6 Rule 3` | HIGH | M2-4, F-5, F-3 |

---

## Module 5 — Agentic Commerce

**SRS**: FR 5.0, 5.1, 5.2 | **Functional spec**: US-5.1–US-5.4

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| M5-1 | **Wallet Provisioning at Startup**: Retrieve private key from AWS Secrets Manager / HashiCorp Vault at path `chimera/agent/{agent_id}/wallet_key`; hold in-memory only; verify derived address matches `AgentPersona.walletAddress`; fail startup on mismatch | `functional.md US-5.1`, `specs/_meta.md §6 Rule 1` | HIGH | M1-1 |
| M5-2 | **CFO Sub-Judge Service**: Intercept all `TRANSFER_FUNDS` / `DEPLOY_TOKEN` task types before confidence routing; workers have zero direct access to `mcp-server-coinbase` (enforce at MCP layer) | `functional.md US-5.2`, `specs/_meta.md §6 Rule 4`, `technical.md §4` | HIGH | F-3, F-6 |
| M5-3 | **Atomic Daily Spend Enforcement**: Lua script executing `INCRBY chimera:spend:{agent_id}:{YYYYMMDD}`; GET to verify total; reject if total > `campaign.max_daily_spend`; insert into `hitl_queue` (`CFO_REVIEW`) with `spend_context` metadata on rejection; TTL 86400s auto-reset | `functional.md US-5.3`, `technical.md §3.2` | HIGH | M5-2, F-5 |
| M5-4 | **On-Chain Transaction Execution**: On CFO Sub-Judge approval, call `mcp-server-coinbase` `native_transfer`; write transaction hash to `audit_log` (`TRANSACTION_EXECUTED`); write to blockchain ledger record | `functional.md US-5.2`, `technical.md §4` | HIGH | M5-3 |
| M5-5 | **ERC-20 Fan Token Deployment**: Planner dedup check (`agents.token_contract_address` IS NULL); `DEPLOY_TOKEN` task via CFO Sub-Judge with gas cost estimate; write contract address to `agents` table and Moltbook status record on success | `functional.md US-5.4`, `technical.md §3.1` | LOW | M5-4, M6-1 |

---

## Module 6 — Orchestration & Swarm Governance

**SRS**: FR 6.0, 6.1 | **Functional spec**: US-6.1–US-6.5

| ID | Task | Spec Reference | Priority | Depends On |
|---|---|---|---|---|
| M6-1 | **Task DAG Generator (Planner)**: Read `GlobalState` from PostgreSQL with `state_version` check; generate sibling `AgentTask` records for independent work; `LPUSH` each task atomically to `chimera:queue:tasks` with `dag_id` tag; Planner must never call MCP Tools directly | `functional.md US-6.1`, `technical.md §1.1, §3.1` | HIGH | F-1, F-3, F-5 |
| M6-2 | **Stateless Worker Execution Engine**: `Executors.newVirtualThreadPerTaskExecutor()`; `RPOP` (or `BRPOP`) from `chimera:queue:tasks`; execute one task; always `LPUSH` `AgentResult` to `chimera:queue:review` — even on unhandled exception (FAILURE result); no shared mutable state between Workers | `functional.md US-6.2`, `technical.md §5.1`, `specs/_meta.md §3` | HIGH | F-1, F-5, M6-1 |
| M6-3 | **Judge OCC Commit**: Read `state_version` from PostgreSQL before write; Spring Data JPA `@Version`-guarded `save()`; catch `OptimisticLockException` → re-queue task with refreshed `state_version` (not a rejection); emit structured log event per Constitution Principle III | `functional.md US-6.3`, `technical.md §3.1`, `specs/_meta.md §6 Rule 6` | HIGH | F-1, F-3, M6-2 |
| M6-4 | **Judge Audit Logger**: On every routing decision (APPROVED, REJECTED, ESCALATED, MANDATORY_HITL), write to `audit_log` with `payload_hash` (SHA-256 of `AgentResult` JSON); enforce 90-day retention | `functional.md US-6.3`, `technical.md §3.1`, `specs/_meta.md §3` | HIGH | M6-3, F-3 |
| M6-5 | **HITL Queue Writer**: Insert into `hitl_queue` for ASYNC_REVIEW (confidence 0.70–0.90), MANDATORY_ESCALATION (sensitive topics), and CFO_REVIEW (budget exceeded); non-blocking — agent continues other tasks | `functional.md US-6.3`, `technical.md §3.1, §4` | HIGH | M6-3, F-3 |
| M6-6 | **Dynamic Re-Planning on Failure**: Monitor `chimera:queue:review` for FAILURE results; check `retry_count < max_retries`; re-queue with `retry_count + 1` and enriched `TaskContext` (failure reason as negative example); mark FAILED and notify Operator when `max_retries` exhausted | `functional.md US-6.5` | MEDIUM | M6-1, M6-2 |
| M6-7 | **Reactive Re-Planning on TrendAlert**: When `TrendAlert` is injected into `GlobalState` mid-campaign, generate new tasks and append to existing DAG without cancelling in-flight tasks; link via `dag_id` | `functional.md US-6.5`, `technical.md §2.4` | MEDIUM | M6-1, M2-7 |
| M6-8 | **Status Broadcasting (Moltbook)**: Planner schedules `BROADCAST_STATUS` every 4 hours; idempotency check via `last_broadcast_at` in episodic cache (skip if < 3h ago); Worker calls `mcp-server-openclaw` `publish_status` with `AgentStatusPayload` (excludes financial task types, SOUL.md narrative, private key material); Judge AUTO-APPROVEs without confidence routing; log `STATUS_BROADCAST` to `audit_log` | `functional.md US-6.4`, `specs/openclaw_integration.md §2 Path 1, §3` | MEDIUM | M6-2, M6-3, M1-6, M5-1 |
| M6-9 | **DAG Orphan Cleanup on Restart**: On Planner startup, query `chimera:queue:tasks` for tasks with `dag_id` where DAG is incomplete; re-queue or mark FAILED based on retry state | `functional.md US-6.1`, `technical.md §3.2` | MEDIUM | M6-1, F-5 |

---

## Dependency Graph Summary

```
Foundation (F-1 through F-8)
    └── All module tasks depend on Foundation

M1-1 (SOUL.md Parser)
    └── M1-2 (Hash Verify) → M1-3 (Context Assembler) → M3-1 (Text Gen) → M4-1 (Publish)
    └── M1-8 (Persona Enforcement)
    └── M5-1 (Wallet Provisioning)

M2-4 (Injection Pipeline)
    └── M2-5 (Sanitized Flag) → All Worker tasks that process inbound content
    └── M4-2 (Mention Poller) → M4-3 (Mention Dedup)

M6-1 (DAG Generator)
    └── M6-2 (Worker Engine)
        └── M6-3 (Judge OCC) → M6-4 (Audit Logger)
                             → M6-5 (HITL Queue Writer)
                             → M1-6 (Episodic Cache Write)
                             → M1-7 (Long-Term Memory Write)
                             → M3-2 (Duplicate Check) → M3-3 (Archive Write)
    └── M6-6 (Re-Plan on Failure)
    └── M6-7 (Re-Plan on Trend) ← M2-7 (TrendAlert Dedup)

M5-2 (CFO Sub-Judge)
    └── M5-3 (Spend Enforcement) → M5-4 (On-Chain Tx) → M5-5 (Fan Token)
```

---

## Implementation Order (Recommended Phases)

### Phase 1 — Core Swarm Loop (Unblocks Everything)
F-1, F-2, F-3, F-5, F-7, F-8, M1-1, M1-2, M6-1, M6-2, M6-3, M6-4, M6-5

### Phase 2 — Perception + Content + Publishing
F-4, F-6, M1-3, M1-4, M1-5, M1-6, M1-8, M2-1, M2-2, M2-3, M2-4, M2-5, M3-1, M3-2, M3-3, M4-1, M4-2, M4-3, M4-4

### Phase 3 — Commerce + OpenClaw + Resilience
M5-1, M5-2, M5-3, M5-4, M1-7, M2-6, M2-7, M6-6, M6-7, M6-8, M6-9

### Phase 4 — Low Priority Enhancements
M3-4, M3-5, M3-6, M3-7, M5-5

---

*Total tasks: 8 Foundation + 8 Module 1 + 7 Module 2 + 7 Module 3 + 4 Module 4 + 5 Module 5 + 9 Module 6 = **48 tasks***
