# Project Chimera — Technical Specification

**Version**: 1.0.0 | **Status**: Approved | **Last Updated**: 2026-03-09
**Traces to**: `specs/_meta.md`, `specs/functional.md`, SRS FR 6.0–6.2, Constitution Principles I–VI

> This document defines the data contracts, database schemas, routing logic, and technology
> stack that all feature implementations must conform to. No implementation may deviate from
> these schemas without a corresponding spec amendment.

---

## 1. API Contracts

All inter-service payloads are JSON, serialised and deserialised via Jackson. Every field
marked required MUST be present; the Judge rejects any `AgentResult` missing a required
field before safety scoring begins. Field names use `camelCase` in Java and `snake_case`
in JSON wire format — Jackson handles the mapping via `@JsonNaming(SnakeCaseStrategy.class)`.

---

### 1.1 AgentTask — Planner → Worker

The unit of work pushed by the Planner onto `chimera:queue:tasks`. A Worker pops and
executes exactly one task per Virtual Thread lifecycle.

```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "task_type": "POST_INSTAGRAM",
  "priority": 1,
  "context": {
    "goal": "Grow Instagram following during fashion week",
    "persona_constraints": [
      "maintain upbeat tone",
      "no brand endorsements without CFO approval"
    ],
    "required_mcp_resources": [
      "instagram://mentions/recent",
      "news://trends/fashion"
    ],
    "character_reference_id": "zara-v3-lora-abc123",
    "dag_id": "dag-fashionweek-2026-001",
    "sanitized": true
  },
  "agent_id": "agent-zara-001",
  "campaign_id": "campaign-fashionweek-2026",
  "assigned_worker_id": null,
  "status": "PENDING",
  "created_at": "2026-03-09T10:00:00Z",
  "retry_count": 0,
  "max_retries": 3,
  "state_version": 42
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `task_id` | UUID v4 string | Yes | Unique per task; links `AgentResult` back to its originating task |
| `task_type` | enum string | Yes | Controls which MCP server the Worker calls (see enum below) |
| `priority` | integer 1–5 | Yes | 1 = highest; derived from relevance score in Planner (FR 2.1) |
| `context.goal` | string | Yes | Natural language campaign goal passed to Worker LLM prompt |
| `context.persona_constraints` | string[] | Yes | Subset of SOUL.md `hardDirectives` relevant to this task |
| `context.required_mcp_resources` | string[] | Yes | MCP Resource URIs pre-fetched before Worker assembles context |
| `context.character_reference_id` | string | No | Required for all `GENERATE_IMAGE` / `GENERATE_VIDEO_*` tasks |
| `context.dag_id` | string | Yes | Links task to its parent DAG for crash-recovery cleanup |
| `context.sanitized` | boolean | Yes | Must be `true`; Workers reject tasks where this is `false` or absent |
| `agent_id` | string | Yes | Maps to `agents.agent_id` in PostgreSQL and the loaded `AgentPersona` |
| `campaign_id` | string | Yes | Foreign key to `campaigns.campaign_id` |
| `assigned_worker_id` | string | No | Null until a Worker claims the task via RPOP |
| `status` | enum string | Yes | `PENDING \| IN_PROGRESS \| COMPLETED \| FAILED \| REJECTED` |
| `created_at` | ISO 8601 string | Yes | Set by Planner at task creation time |
| `retry_count` | integer | Yes | Incremented by Planner on each retry; starts at 0 |
| `max_retries` | integer | Yes | Default 3; set per task type in campaign config |
| `state_version` | long | Yes | OCC version; Judge verifies this matches `global_state.state_version` before committing |

**`task_type` enum values**:
`POST_INSTAGRAM`, `POST_TWEET`, `REPLY_MENTION`, `GENERATE_IMAGE`, `GENERATE_VIDEO_TIER1`,
`GENERATE_VIDEO_TIER2`, `BROADCAST_STATUS`, `TRANSFER_FUNDS`, `DEPLOY_TOKEN`

---

### 1.2 AgentResult — Worker → Judge

Produced by every Worker on task completion (success or failure) and pushed to
`chimera:queue:review`. The Judge consumes this record to route, approve, or reject.
A missing `confidence_score` is an automatic rejection at the Judge's input validation step.

```json
{
  "task_id": "550e8400-e29b-41d4-a716-446655440000",
  "worker_id": "worker-vt-00142",
  "agent_id": "agent-zara-001",
  "status": "SUCCESS",
  "output": {
    "content_type": "TEXT",
    "content": "Just spotted the most stunning silhouettes at the Armani show…",
    "mcp_tool_called": "mcp-server-instagram.post_media",
    "mcp_tool_response": {
      "post_id": "ig-post-9988776",
      "url": "https://www.instagram.com/p/abc123"
    }
  },
  "confidence_score": 0.94,
  "sensitive_topic_flags": [],
  "processing_time_ms": 1840,
  "completed_at": "2026-03-09T10:00:03Z",
  "state_version": 42
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `task_id` | UUID v4 string | Yes | Correlates to the originating `AgentTask` |
| `worker_id` | string | Yes | Identifies the Virtual Thread Worker instance |
| `agent_id` | string | Yes | Must match the originating `AgentTask.agent_id` |
| `status` | enum string | Yes | `SUCCESS \| FAILURE` — Worker execution outcome, not the Judge's routing decision |
| `output.content_type` | enum string | Yes | `TEXT \| IMAGE_URL \| VIDEO_URL \| STATUS_JSON \| TRANSACTION_HASH` |
| `output.content` | string | Yes | The generated content or transaction reference |
| `output.mcp_tool_called` | string | Yes | Format: `{server-name}.{tool_name}` |
| `output.mcp_tool_response` | object | No | Raw MCP server response; null on FAILURE |
| `confidence_score` | float 0.0–1.0 | **Yes** | LLM self-assessment; drives Judge tier routing; never modified by Worker |
| `sensitive_topic_flags` | string[] | Yes | Empty array if none; values: `POLITICS \| HEALTH \| FINANCE \| LEGAL` |
| `processing_time_ms` | long | Yes | Used for NFR 3.1 latency tracking; must be ≤ 10,000 for high-priority tasks |
| `completed_at` | ISO 8601 string | Yes | Worker completion timestamp |
| `state_version` | long | Yes | Must match the `state_version` from the originating `AgentTask` |

---

### 1.3 MCPToolCall — Worker → MCP Server

The standard envelope for every tool invocation from a Worker to any MCP server.
Direct REST calls or SDK imports in Worker business logic are a constitution violation
enforced by Checkstyle rules.

```json
{
  "server": "mcp-server-instagram",
  "tool": "post_media",
  "arguments": {
    "caption": "Just spotted the most stunning silhouettes…",
    "media_url": "https://cdn.chimera.internal/images/zara-armani-2026.jpg",
    "hashtags": ["#FashionWeek", "#Armani"],
    "character_reference_id": "zara-v3-lora-abc123"
  },
  "disclosure_level": "automated",
  "caller_task_id": "550e8400-e29b-41d4-a716-446655440000",
  "caller_agent_id": "agent-zara-001"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `server` | string | Yes | Must match a registered MCP server name |
| `tool` | string | Yes | Must match a tool exposed by that server's `inputSchema` |
| `arguments` | object | Yes | Tool-specific; validated against the tool's JSON Schema before dispatch |
| `disclosure_level` | enum string | Yes | `automated` = no human reviewed; `assisted` = HITL approved; `none` = internal system action |
| `caller_task_id` | UUID string | Yes | Audit linkage — ties every external API call to an `AgentTask` |
| `caller_agent_id` | string | Yes | Used by MCP server for per-agent rate limiting and billing |

**`disclosure_level` rules**: AUTO-APPROVED results produce `automated`; HITL-approved results
produce `assisted`. The MCP server uses this value to set the platform-native AI label
(`is_generated` / `ai_label`) on publication (SRS NFR 2.0).

---

## 2. Java Data Models

All inter-agent DTOs are Java Records — immutable, naturally thread-safe, and OCC-compatible.
Compact constructors validate invariants at construction time. Nullable fields are annotated
`@Nullable` (from `org.jspecify.annotations`). All records live in `com.chimera.domain`.

---

### 2.1 AgentTask

```java
package com.chimera.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AgentTask(
    UUID         taskId,             // NOT NULL
    TaskType     taskType,           // NOT NULL
    int          priority,           // NOT NULL — 1 (highest) to 5 (lowest)
    TaskContext  context,            // NOT NULL
    String       agentId,           // NOT NULL
    String       campaignId,        // NOT NULL
    @Nullable String assignedWorkerId, // nullable until claimed by Worker
    TaskStatus   status,            // NOT NULL
    Instant      createdAt,         // NOT NULL
    int          retryCount,        // NOT NULL — default 0
    int          maxRetries,        // NOT NULL — default 3
    long         stateVersion       // NOT NULL — OCC version counter
) {
    public AgentTask {
        Objects.requireNonNull(taskId,      "taskId must not be null");
        Objects.requireNonNull(taskType,    "taskType must not be null");
        Objects.requireNonNull(context,     "context must not be null");
        Objects.requireNonNull(agentId,     "agentId must not be null");
        Objects.requireNonNull(campaignId,  "campaignId must not be null");
        Objects.requireNonNull(status,      "status must not be null");
        Objects.requireNonNull(createdAt,   "createdAt must not be null");
        if (priority < 1 || priority > 5)
            throw new IllegalArgumentException("priority must be 1–5, got: " + priority);
        if (retryCount < 0)
            throw new IllegalArgumentException("retryCount must be >= 0");
        if (maxRetries < 1)
            throw new IllegalArgumentException("maxRetries must be >= 1");
        if (!context.sanitized())
            throw new IllegalArgumentException("TaskContext must be sanitized before task creation");
    }
}

public record TaskContext(
    String       goal,                     // NOT NULL
    List<String> personaConstraints,       // NOT NULL — may be empty list
    List<String> requiredMcpResources,     // NOT NULL — may be empty list
    @Nullable String characterReferenceId, // nullable; required for GENERATE_IMAGE / VIDEO tasks
    String       dagId,                    // NOT NULL — parent DAG identifier
    boolean      sanitized                 // NOT NULL — must be true; enforced in AgentTask compact constructor
) {}

public enum TaskType {
    POST_INSTAGRAM, POST_TWEET, REPLY_MENTION,
    GENERATE_IMAGE, GENERATE_VIDEO_TIER1, GENERATE_VIDEO_TIER2,
    BROADCAST_STATUS, TRANSFER_FUNDS, DEPLOY_TOKEN
}

public enum TaskStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, REJECTED
}
```

---

### 2.2 AgentResult

```java
package com.chimera.domain;

import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AgentResult(
    UUID              taskId,              // NOT NULL — correlates to AgentTask
    String            workerId,           // NOT NULL
    String            agentId,            // NOT NULL
    ResultStatus      status,             // NOT NULL
    ResultOutput      output,             // NOT NULL
    double            confidenceScore,    // NOT NULL — 0.0 to 1.0 inclusive
    List<SensitiveTopic> sensitiveTopicFlags, // NOT NULL — empty list if none
    long              processingTimeMs,   // NOT NULL
    Instant           completedAt,        // NOT NULL
    long              stateVersion        // NOT NULL — must match originating AgentTask
) {
    public AgentResult {
        Objects.requireNonNull(taskId,             "taskId must not be null");
        Objects.requireNonNull(workerId,           "workerId must not be null");
        Objects.requireNonNull(agentId,            "agentId must not be null");
        Objects.requireNonNull(status,             "status must not be null");
        Objects.requireNonNull(output,             "output must not be null");
        Objects.requireNonNull(sensitiveTopicFlags,"sensitiveTopicFlags must not be null");
        Objects.requireNonNull(completedAt,        "completedAt must not be null");
        if (confidenceScore < 0.0 || confidenceScore > 1.0)
            throw new IllegalArgumentException(
                "confidenceScore must be in [0.0, 1.0], got: " + confidenceScore);
        if (processingTimeMs < 0)
            throw new IllegalArgumentException("processingTimeMs must be >= 0");
    }
}

public record ResultOutput(
    ContentType   contentType,        // NOT NULL
    String        content,            // NOT NULL
    String        mcpToolCalled,      // NOT NULL — format: "{server}.{tool}"
    @Nullable Object mcpToolResponse  // nullable on FAILURE status
) {
    public ResultOutput {
        Objects.requireNonNull(contentType,   "contentType must not be null");
        Objects.requireNonNull(content,       "content must not be null");
        Objects.requireNonNull(mcpToolCalled, "mcpToolCalled must not be null");
    }
}

public enum ResultStatus   { SUCCESS, FAILURE }
public enum ContentType    { TEXT, IMAGE_URL, VIDEO_URL, STATUS_JSON, TRANSACTION_HASH }
public enum SensitiveTopic { POLITICS, HEALTH, FINANCE, LEGAL }
```

---

### 2.3 AgentPersona

Parsed from SOUL.md YAML frontmatter at agent startup. Immutable for the process lifetime;
any field change requires an operator re-provision action recorded in `audit_log`.

```java
package com.chimera.domain;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Objects;

public record AgentPersona(
    String       agentId,               // NOT NULL
    String       displayName,           // NOT NULL — public persona name
    String       backstory,             // NOT NULL — origin and background narrative
    String       voiceTone,             // NOT NULL — e.g. "warm, witty, fashion-forward"
    List<String> coreBeliefs,           // NOT NULL — values that shape decisions
    List<String> hardDirectives,        // NOT NULL — absolute behavioural rules; min 1 entry
    String       niche,                 // NOT NULL — e.g. "fashion", "tech", "fitness"
    String       walletAddress,         // NOT NULL — Coinbase AgentKit address; Moltbook identity anchor
    @Nullable String characterReferenceId, // nullable until first image generation provisioning
    String       soulMdHash             // NOT NULL — SHA-256 of SOUL.md; verified against agents table
) {
    public AgentPersona {
        Objects.requireNonNull(agentId,        "agentId must not be null");
        Objects.requireNonNull(displayName,    "displayName must not be null");
        Objects.requireNonNull(backstory,      "backstory must not be null");
        Objects.requireNonNull(voiceTone,      "voiceTone must not be null");
        Objects.requireNonNull(coreBeliefs,    "coreBeliefs must not be null");
        Objects.requireNonNull(hardDirectives, "hardDirectives must not be null");
        Objects.requireNonNull(niche,          "niche must not be null");
        Objects.requireNonNull(walletAddress,  "walletAddress must not be null");
        Objects.requireNonNull(soulMdHash,     "soulMdHash must not be null");
        if (hardDirectives.isEmpty())
            throw new IllegalArgumentException("hardDirectives must contain at least one directive");
    }
}
```

---

### 2.4 TrendAlert

Produced by the Perception System after the 4-hour rolling cluster window detects a
coherent theme crossing the relevance threshold (FR 2.2). Injected into Planner
`GlobalState` to trigger reactive task DAG generation.

```java
package com.chimera.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TrendAlert(
    String       trendId,          // NOT NULL — unique per detection; used for dedup in GlobalState
    String       topic,            // NOT NULL — LLM-generated cluster summary
    double       relevanceScore,   // NOT NULL — 0.75 to 1.0; values below 0.75 never reach Planner
    List<String> sourceUrls,       // NOT NULL — MCP Resource URIs that fired the alert
    String       niche,            // NOT NULL — matches agent niche for routing
    Instant      detectedAt,       // NOT NULL
    Instant      expiresAt         // NOT NULL — end of the 4-hour trend window
) {
    public TrendAlert {
        Objects.requireNonNull(trendId,     "trendId must not be null");
        Objects.requireNonNull(topic,       "topic must not be null");
        Objects.requireNonNull(sourceUrls,  "sourceUrls must not be null");
        Objects.requireNonNull(niche,       "niche must not be null");
        Objects.requireNonNull(detectedAt,  "detectedAt must not be null");
        Objects.requireNonNull(expiresAt,   "expiresAt must not be null");
        if (relevanceScore < 0.75 || relevanceScore > 1.0)
            throw new IllegalArgumentException(
                "relevanceScore must be >= 0.75 (FR 2.1 threshold), got: " + relevanceScore);
        if (!expiresAt.isAfter(detectedAt))
            throw new IllegalArgumentException("expiresAt must be after detectedAt");
    }
}
```

---

## 3. Database Schema

### 3.1 PostgreSQL — System of Record

Target version: PostgreSQL 16+. OCC is enforced via `state_version` columns managed by
Spring Data JPA `@Version`; no manual version arithmetic in application code.

#### `agents`

```sql
CREATE TABLE agents (
    agent_id               VARCHAR(64)   PRIMARY KEY,
    display_name           VARCHAR(255)  NOT NULL,
    niche                  VARCHAR(128)  NOT NULL,
    wallet_address         VARCHAR(128)  NOT NULL UNIQUE,
    soul_md_hash           VARCHAR(64)   NOT NULL,        -- SHA-256; verified at startup
    character_reference_id VARCHAR(128),                  -- null until provisioned
    token_contract_address VARCHAR(128),                  -- null until fan token deployed
    status                 VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PAUSED | DECOMMISSIONED
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
```

#### `campaigns`

```sql
CREATE TABLE campaigns (
    campaign_id      VARCHAR(64)    PRIMARY KEY,
    agent_id         VARCHAR(64)    NOT NULL REFERENCES agents(agent_id),
    name             VARCHAR(255)   NOT NULL,
    goal             TEXT           NOT NULL,
    status           VARCHAR(32)    NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PAUSED | COMPLETED
    max_daily_spend  NUMERIC(18, 8) NOT NULL DEFAULT 0,         -- in USDC; 0 = no spending allowed
    fan_token_enabled BOOLEAN       NOT NULL DEFAULT FALSE,
    campaign_milestone_quota INT    NOT NULL DEFAULT 1,          -- max GENERATE_VIDEO_TIER2 per 24h
    start_at         TIMESTAMPTZ    NOT NULL,
    end_at           TIMESTAMPTZ,                                -- null = open-ended
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
```

#### `global_state`

```sql
CREATE TABLE global_state (
    agent_id        VARCHAR(64)  PRIMARY KEY REFERENCES agents(agent_id),
    state_json      JSONB        NOT NULL DEFAULT '{}',
    state_version   BIGINT       NOT NULL DEFAULT 0,   -- Spring @Version; incremented on every Judge commit
    last_updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- Two Judge instances committing against the same state_version: exactly one succeeds.
-- The loser receives OptimisticLockException -> task is re-queued, not failed.
```

#### `audit_log`

```sql
CREATE TABLE audit_log (
    log_id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id          UUID         NOT NULL,
    agent_id         VARCHAR(64)  NOT NULL,
    action           VARCHAR(64)  NOT NULL,
      -- APPROVED | REJECTED | ESCALATED | MANDATORY_HITL |
      -- INJECTION_BLOCKED | AI_DISCLOSURE | STATUS_BROADCAST |
      -- TRANSACTION_EXECUTED | PERSONA_VIOLATION
    actor            VARCHAR(128) NOT NULL,   -- 'JUDGE_AUTO' | 'JUDGE_PERSONA' | 'CFO_JUDGE' | 'HITL:<reviewer_id>'
    confidence_score NUMERIC(4,3),            -- null for mandatory escalations and system actions
    sensitive_flags  VARCHAR(64)[],           -- postgres array; empty if none
    payload_hash     VARCHAR(64)  NOT NULL,   -- SHA-256 of AgentResult JSON
    notes            TEXT,                    -- violation directive, rejection reason, etc.
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- Retained minimum 90 days (NFR 2.x).
-- Recommended: partition by month (PARTITION BY RANGE (created_at)) for query performance at scale.
```

#### `hitl_queue`

```sql
CREATE TABLE hitl_queue (
    queue_id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id          UUID         NOT NULL UNIQUE,
    agent_id         VARCHAR(64)  NOT NULL,
    queue_type       VARCHAR(32)  NOT NULL,
      -- ASYNC_REVIEW | MANDATORY_ESCALATION | CFO_REVIEW
    content_preview  TEXT         NOT NULL,
    confidence_score NUMERIC(4,3),            -- null for mandatory escalations
    sensitive_flags  VARCHAR(64)[],
    spend_context    JSONB,                   -- CFO_REVIEW only: {current_total, proposed_amount, ceiling}
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    reviewer_id      VARCHAR(128),            -- null until claimed by an Operator
    queued_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMPTZ             -- null until reviewed
);
```

---

### 3.2 Redis — Operational Bus

Target version: Redis 7+ in Cluster mode for HA at 1,000-agent scale.
Key naming convention: `chimera:{namespace}:{identifier}`.

#### `task_queue`

```
Key:   chimera:queue:tasks
Type:  List
Write: LPUSH by Planner (one LPUSH per AgentTask; atomic per task)
Read:  RPOP by Workers (one RPOP claims one task; BRPOP for blocking pop)
Value: JSON-serialised AgentTask
TTL:   None — tasks persist until consumed; stale IN_PROGRESS tasks cleaned up by Planner on restart via dag_id
```

#### `review_queue`

```
Key:   chimera:queue:review
Type:  List
Write: LPUSH by Workers (always written, SUCCESS or FAILURE)
Read:  RPOP by Judge (one RPOP per evaluation cycle)
Value: JSON-serialised AgentResult
TTL:   None — results persist until Judge consumes them
```

#### `daily_spend`

```
Key:    chimera:spend:{agent_id}:{YYYYMMDD}
Type:   String (integer counter; unit = USDC micro-units, i.e. 1 USDC = 1_000_000)
Write:  INCRBY — atomic; concurrent CFO Sub-Judge instances cannot race
Read:   GET — to verify current total before escalation message
TTL:    86400 seconds — auto-expires at key creation time + 24h; no manual reset required

Example operations:
  INCRBY chimera:spend:agent-zara-001:20260309 500000   -- propose $0.50 spend
  GET    chimera:spend:agent-zara-001:20260309          -- current total: "500000"
  TTL    chimera:spend:agent-zara-001:20260309          -- seconds remaining today
```

#### `agent_cache` — Short-Term Episodic Memory

```
Key:    chimera:cache:{agent_id}
Type:   Hash (field-value pairs)
Write:  HSET by Judge on AUTO-APPROVE; TTL reset to 3600 on every write
Read:   HGETALL by Worker during context assembly
TTL:    3600 seconds (1-hour rolling window, FR 1.x)

Fields:
  last_post_id       STRING   -- ID of most recently published post
  last_mention_id    STRING   -- ID of last processed mention (dedup key for US-4.2)
  active_trend_id    STRING   -- current TrendAlert.trendId, if any
  last_broadcast_at  STRING   -- ISO 8601; dedup key for BROADCAST_STATUS (US-6.4)
  context_snapshot   STRING   -- SHA-256 of last assembled context window
```

---

### 3.3 Weaviate — Semantic Memory

Target version: Weaviate 1.24+ (self-hosted on Kubernetes; per-query cloud pricing
unsustainable at 1,000-agent scale). Collection uses hybrid vector + BM25 search.

#### `AgentMemory` collection

```json
{
  "class": "AgentMemory",
  "vectorizer": "text2vec-openai",
  "vectorIndexConfig": {
    "distance": "cosine"
  },
  "moduleConfig": {
    "text2vec-openai": {
      "model": "text-embedding-3-small",
      "vectorizeClassName": false
    }
  },
  "properties": [
    {
      "name": "agentId",
      "dataType": ["text"],
      "description": "Owner agent identifier — mandatory filter on all queries"
    },
    {
      "name": "memoryType",
      "dataType": ["text"],
      "description": "INTERACTION | TREND | CONTENT_ARCHIVE"
    },
    {
      "name": "content",
      "dataType": ["text"],
      "description": "Raw text content — this field is vectorised"
    },
    {
      "name": "engagementScore",
      "dataType": ["number"],
      "description": "Platform engagement metric at storage time; 0 for non-interaction types"
    },
    {
      "name": "platform",
      "dataType": ["text"],
      "description": "instagram | twitter | moltbook | internal"
    },
    {
      "name": "taskId",
      "dataType": ["text"],
      "description": "Originating AgentTask UUID — audit linkage"
    },
    {
      "name": "storedAt",
      "dataType": ["date"],
      "description": "ISO 8601 — when the memory was written"
    },
    {
      "name": "expiresAt",
      "dataType": ["date"],
      "description": "ISO 8601 — null for permanent retention; set for TREND type (4h window)"
    }
  ]
}
```

**Standard query pattern** (hybrid search with mandatory `agentId` filter):
```graphql
{
  Get {
    AgentMemory(
      hybrid: { query: "fashion week audience reactions", alpha: 0.75 }
      where: {
        operator: And
        operands: [
          { path: ["agentId"], operator: Equal, valueText: "agent-zara-001" },
          { path: ["memoryType"], operator: Equal, valueText: "INTERACTION" }
        ]
      }
      limit: 5
    ) {
      content engagementScore platform taskId storedAt _additional { score }
    }
  }
}
```

`alpha: 0.75` weights vector similarity above BM25; lower for trend detection queries
where exact keyword matching matters.

---

## 4. Confidence Score Routing

The Judge evaluates every `AgentResult` in two sequential phases. Phase 1 always runs
first; it can short-circuit routing before the confidence score is ever evaluated.

### Phase 1 — Sensitive Topic Detection

```
IF result.sensitiveTopicFlags is not empty:
    → Insert into hitl_queue (queue_type = MANDATORY_ESCALATION)
    → Write to audit_log (action = MANDATORY_HITL, actor = JUDGE_AUTO)
    → STOP — do not evaluate confidence score
    → Agent continues other tasks in parallel
```

Detection mechanism: keyword pattern matching on `output.content` followed by secondary
semantic classification. Pattern matching catches known terms; semantic classification
catches paraphrased or implicit sensitive content.

```
IF result.taskType IN [TRANSFER_FUNDS, DEPLOY_TOKEN]:
    → Route to CFO Sub-Judge (regardless of confidence score)
    → INCRBY chimera:spend:{agent_id}:{date} by transaction_amount (atomic Lua script)
    → GET current total
    → IF total > campaign.max_daily_spend:
          Insert into hitl_queue (queue_type = CFO_REVIEW, spend_context = {...})
          Write to audit_log (action = ESCALATED, actor = CFO_JUDGE)
          STOP
    → IF within budget: continue to Phase 2
```

### Phase 2 — Confidence Score Tiers

```
Tier 1 — score > 0.90  (AUTO-APPROVE)
    Execute MCPToolCall immediately
    Commit result to global_state via JPA @Version save (OCC check)
    Update episodic cache: HSET chimera:cache:{agent_id} {fields} + reset TTL
    Store to Weaviate if high-engagement signal present (US-1.4)
    Write to audit_log (action = APPROVED, actor = JUDGE_AUTO)

Tier 2 — score 0.70–0.90  (ASYNC HITL)
    Insert into hitl_queue (queue_type = ASYNC_REVIEW)
    Write to audit_log (action = ESCALATED, actor = JUDGE_AUTO)
    Agent continues other tasks — this path is non-blocking
    Await Operator decision: APPROVED → execute MCP + commit; REJECTED → re-queue

Tier 3 — score < 0.70  (AUTO-REJECT + RETRY)
    Signal Planner: create new AgentTask with retry_count + 1
    Append rejection reason + output hash to new TaskContext as negative example
    Write to audit_log (action = REJECTED, actor = JUDGE_AUTO)
    IF retry_count >= max_retries: mark FAILED, insert Operator notification into hitl_queue
```

### Routing Summary

| Condition | Route | Blocks Agent? | Human Reviews? |
|---|---|---|---|
| Sensitive topic flag present | MANDATORY_HITL | No | Always |
| Financial task type | CFO Sub-Judge → then below | Yes for CFO check | Only if limit exceeded |
| score > 0.90, no flags | AUTO-APPROVE | No | No |
| 0.70 ≤ score ≤ 0.90, no flags | ASYNC HITL | No | Yes, async |
| score < 0.70 | AUTO-REJECT + retry | No | No |
| retry_count ≥ max_retries | FAILED + Operator alert | No | Yes, async |

---

## 5. Technology Stack

All versions are minimum required. A feature deviating from a listed version must document
the justification in the feature `plan.md` Complexity Tracking table (Constitution Principle I).

### 5.1 Runtime & Framework

| Dependency | Min Version | Purpose |
|---|---|---|
| OpenJDK | 21 LTS | Platform; Virtual Threads (Project Loom), Records, Pattern Matching for switch |
| Spring Boot | 3.3 | Application framework; dependency injection, web layer, JPA, actuator |
| Spring Data JPA | 3.3 | ORM for PostgreSQL; `@Version` annotation enforces OCC on `global_state` |
| Maven | 3.9 | Build tooling; consistent across all modules (or Gradle 8.5+; must not mix) |
| Docker | 25 | Container packaging; every deployable service ships as a Docker image |

### 5.2 Data Layer

| Dependency | Min Version | Purpose |
|---|---|---|
| Jedis | 5.x | Redis client; `LPUSH`, `RPOP`, `BRPOP`, `INCRBY`, Lua scripting for atomic spend check |
| PostgreSQL JDBC Driver | 42.7 | Database driver for PostgreSQL 16+ |
| Weaviate Java Client | 4.x | Semantic memory; hybrid vector + BM25 queries; collection schema management |
| Jackson Databind | 2.17 | JSON serialisation/deserialisation for all inter-service payloads |
| Jackson Datatype JSR310 | 2.17 | `Instant`, `ZonedDateTime` support in JSON (required for all timestamp fields) |

### 5.3 MCP & Agent Integration

| Dependency | Min Version | Purpose |
|---|---|---|
| Anthropic Java SDK | Latest stable | MCP client implementation; `callTool()` interface for Worker → MCP server calls |
| Claude API (claude-opus-4-6) | — | LLM inference for Judge reasoning, Planner DAG generation, sensitive topic classification |
| Claude API (claude-haiku-4-5) | — | High-volume, low-latency Workers; relevance scoring; injection classification |
| Coinbase AgentKit (Python MCP bridge) | Latest stable | Non-custodial wallet; `native_transfer`, `deploy_token` via Python MCP server |

### 5.4 Testing & Quality

| Dependency | Min Version | Purpose |
|---|---|---|
| JUnit 5 (JUnit Jupiter) | 5.11 | All tests; `@Test`, `@ParameterizedTest`, `@ExtendWith`; no JUnit 4 in new code |
| Mockito | 5.x | Mock MCP servers and external dependencies in unit tests |
| Testcontainers | 1.20 | Ephemeral PostgreSQL, Redis, and Weaviate instances for integration tests |
| AssertJ | 3.26 | Fluent assertion library; preferred over JUnit `assertEquals` for readability |
| Checkstyle | 10.x | Enforces: no direct SDK imports in Worker business logic; Records for all DTOs |
| JaCoCo | 0.8 | Code coverage; baseline per feature in `plan.md`; must not regress |

### 5.5 Infrastructure

| Component | Technology | Notes |
|---|---|---|
| Container orchestration | Kubernetes (AWS EKS / GCP GKE) | Worker pool scales horizontally; stateless Workers → add pods, not threads |
| Secret management | AWS Secrets Manager / HashiCorp Vault | Private key path: `chimera/agent/{agent_id}/wallet_key`; never in env vars or config files |
| Redis deployment | Redis 7+ Cluster mode | Cluster mode required for HA at 1,000-agent scale; single-node Redis is dev-only |
| Vector database | Weaviate 1.24+ self-hosted | Self-hosted on Kubernetes; per-query cloud pricing unsustainable at fleet scale |
| Relational database | PostgreSQL 16+ | Primary system of record; connection pooling via PgBouncer at 1,000-agent scale |
| Blockchain | Base (EVM-compatible L2) | Primary ledger; low gas fees vs. Ethereum mainnet; Ethereum as fallback for high-value transactions |
