# CLAUDE.md — Project Chimera Agent Instructions

This file is the runtime directive for any AI agent (Claude Code or otherwise) working on
Project Chimera. Read it in full before touching any file. It supersedes any general coding
intuition you have about how to structure a Java project.

---

## 1. Project Context

### What Is Project Chimera

Project Chimera is a platform for building and operating **autonomous AI influencer agents**
at scale. Each agent maintains a persistent persona (defined in `SOUL.md`), long-term
semantic memory, and the ability to perceive trends, generate multimodal content, and
interact with social media audiences — entirely without human intervention on routine tasks.

The system is designed to run **1,000+ concurrent agents** simultaneously. The central
engineering challenge: give each agent genuine autonomy while keeping a human meaningfully
in control of what matters. Every design decision in the codebase exists to serve that goal.

Full vision: `specs/_meta.md §1`

### Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 LTS (minimum) |
| Framework | Spring Boot | 3.3+ |
| ORM | Spring Data JPA | 3.3+ |
| Build | Maven | 3.9+ |
| Message queue | Redis (Cluster mode) | 7+ |
| Redis client | Jedis | 5.x |
| Vector memory | Weaviate (self-hosted) | 1.24+ |
| Relational DB | PostgreSQL | 16+ |
| JSON | Jackson Databind + JSR310 | 2.17+ |
| Testing | JUnit 5 (Jupiter) | 5.11+ |
| Mocking | Mockito | 5.x |
| Integration tests | Testcontainers | 1.20+ |
| Assertions | AssertJ | 3.26+ |
| Code quality | Checkstyle | 10.x |
| Coverage | JaCoCo | 0.8+ |
| Containers | Docker | 25+ |

All versions are minimums. Deviating from any listed version requires a written justification
in the feature `plan.md` Complexity Tracking table.

### Architecture Pattern: Planner-Worker-Judge (FastRender Swarm)

Every unit of agent work flows through exactly three roles:

```
Planner  ──LPUSH──▶  chimera:queue:tasks  ──RPOP──▶  Worker  ──LPUSH──▶  chimera:queue:review  ──RPOP──▶  Judge
   ▲                                                                                                          │
   └──────────────────────── re-queue on OCC conflict or retry ─────────────────────────────────────────────┘
```

- **Planner** (`PlannerService`): reads `GlobalState`, decomposes campaign goals into a DAG
  of `AgentTask` records, pushes them to Redis. Never executes actions directly.
- **Worker** (`WorkerService`): stateless, ephemeral. Pops one task, executes it via an MCP
  Tool call, pushes `AgentResult` to the review queue. One Virtual Thread per task.
- **Judge** (`JudgeService`): evaluates every result against persona constraints, safety
  rules, and confidence thresholds before anything commits to `GlobalState`.

Full pattern rationale: `research/architectural_decisions.md §Q1`

---

## 2. The Prime Directive

```
NEVER generate implementation code without first reading the relevant spec file.
```

This is not a suggestion. It is enforced by constitution (`constitution.md Principle II`)
and by Checkstyle rules that will fail the build.

### Before writing any code, you must:

1. **State which spec you are referencing.** Name the file and section explicitly.
   Example: *"I am implementing AgentTask based on `specs/technical.md §2.1`."*

2. **Explain your plan in prose before writing the first line.** Describe what you are
   building, which acceptance criteria it satisfies, and what it depends on.

3. **Write the failing test first.** Red → Green → Refactor. A commit that introduces
   implementation code without a preceding failing test is a constitution violation.

4. **Cite the spec in a comment on every non-trivial decision.** See Section 5.

### Spec files and what they govern

| File | Governs |
|---|---|
| `specs/_meta.md` | Project vision, constraints, system boundaries, non-negotiable rules |
| `specs/functional.md` | User stories (US-1.1–US-6.5) and acceptance criteria |
| `specs/technical.md` | JSON schemas, Java Records, DB schema, confidence routing, tech stack |
| `specs/openclaw_integration.md` | Moltbook integration paths, injection defence, agent identity |
| `.specify/memory/constitution.md` | Seven engineering principles; governs all implementation decisions |
| `.specify/memory/task_breakdown.md` | 48 tasks organized by module with dependencies |
| `.specify/memory/implementation_tasks.md` | 68 numbered implementation tasks in TDD order |

---

## 3. Java-Specific Directives

### 3.1 Use Java Records for ALL DTOs

Every inter-service data transfer object is a Java Record. No exceptions.

```java
// specs/technical.md §2.1 — AgentTask schema
public record AgentTask(
    UUID       taskId,
    TaskType   taskType,
    int        priority,        // 1 (highest) to 5 (lowest)
    TaskContext context,
    String     agentId,
    String     campaignId,
    @Nullable String assignedWorkerId,
    TaskStatus status,
    Instant    createdAt,
    int        retryCount,
    int        maxRetries,
    long       stateVersion    // OCC version — NEVER skip this field
) {
    public AgentTask {
        Objects.requireNonNull(taskId, "taskId must not be null");
        // ... validate all non-null fields and invariants
        if (!context.sanitized())
            throw new IllegalArgumentException("TaskContext must be sanitized");
    }
}
```

Records that MUST exist (all in `com.chimera.domain` / `org.chimera.domain`):

| Record | Spec reference |
|---|---|
| `AgentTask` | `specs/technical.md §2.1` |
| `TaskContext` | `specs/technical.md §2.1` |
| `AgentResult` | `specs/technical.md §2.2` |
| `ResultOutput` | `specs/technical.md §2.2` |
| `AgentPersona` | `specs/technical.md §2.3` |
| `TrendAlert` | `specs/technical.md §2.4` |
| `McpToolCall` | `specs/technical.md §1.3` |
| `McpToolResponse` | `specs/technical.md §1.3` |

**Rules for Records:**
- Compact constructors MUST validate all invariants (nulls, ranges, business rules).
- `@Nullable` fields (from `org.jspecify.annotations`) must be explicitly annotated.
- Records are immutable by construction — do NOT add mutable wrapper state.
- If you find yourself wanting a `Builder`, reconsider the design.

### 3.2 Use Virtual Threads for All I/O-Bound Concurrency

```java
// specs/technical.md §5.1, constitution.md Principle V
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

- Platform thread pools (`Executors.newFixedThreadPool(...)`) are **banned** for I/O work.
- Use `StructuredTaskScope.ShutdownOnFailure` for fan-out patterns to guarantee clean
  cancellation and error propagation.
- Workers are the primary consumers: one Virtual Thread per task popped from `task_queue`.

### 3.3 Use JUnit 5 for All Tests — TDD Order

```java
// constitution.md Principle VI — Red-Green-Refactor
@ExtendWith(MockitoExtension.class)
class JudgeServiceTest {

    @Test
    @DisplayName("Judge routes result with confidenceScore > 0.90 to AUTO-APPROVE")
    void judge_highConfidence_autoApproves() {
        // Write this BEFORE JudgeService exists.
    }
}
```

- `@Test`, `@ParameterizedTest`, `@ExtendWith`, `@DisplayName` — JUnit 5 only.
- No JUnit 4 (`@RunWith`, `@Before`, `org.junit.Test`). Checkstyle will fail the build.
- Integration tests use Testcontainers — one container per test class, not per test method.
- All assertions use AssertJ (`assertThat(...)`) not JUnit's `assertEquals`.

### 3.4 Immutability — No Mutable State in DTOs

- DTOs are Records → immutable by definition.
- Service classes may hold injected dependencies (Spring beans) but must not accumulate
  state across requests.
- `GlobalState` lives in PostgreSQL and is protected by OCC — see Section 4.3.
- Do not use `static` mutable fields anywhere in the codebase.

### 3.5 Jackson JSON Serialisation

```java
// specs/technical.md §1 — wire format uses snake_case; Java uses camelCase
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentTask(...) { }
```

- All JSON uses `snake_case` on the wire; Java field names use `camelCase`.
- Register `JavaTimeModule` globally — never use `@JsonSerialize` on individual `Instant` fields.
- `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` must be `false` (forward-compat).
- Nullable fields must serialise as JSON `null`, not be omitted.

---

## 4. Architecture Rules

### 4.1 Workers Are Stateless — No Shared State Between Workers

```java
// specs/functional.md US-6.2 — shared-nothing architecture
public class WorkerService {

    // OK: injected Spring beans (stateless services)
    private final McpClient mcpClient;
    private final RedisQueueService queueService;

    // BANNED: any field that accumulates state across invocations
    // private final List<AgentResult> results = new ArrayList<>();  // NO
}
```

- Workers read from Redis and Weaviate; they NEVER write to PostgreSQL directly.
- Two Worker instances must process different tasks simultaneously with zero shared
  mutable state — this is the prerequisite for scaling to 1,000 concurrent agents.
- On any unhandled exception, a Worker MUST push a FAILURE `AgentResult` to
  `chimera:queue:review` before terminating. Tasks are never silently dropped.

### 4.2 All External Calls Go Through the MCP Layer ONLY

```java
// constitution.md Principle IV — NO direct SDK calls in business logic

// WRONG — Checkstyle will fail the build:
TwitterClient.post(text);

// CORRECT — specs/technical.md §1.3 MCPToolCall schema:
McpToolCall call = new McpToolCall(
    "mcp-server-twitter",
    "post_tweet",
    Map.of("text", text),
    "automated",            // disclosureLevel — sets platform AI label
    task.taskId().toString(),
    task.agentId()
);
mcpClient.callTool(call);
```

Banned imports in `com.chimera.worker.*` and `com.chimera.service.*` (Checkstyle enforced):
`com.twitter.*`, `com.instagram.*`, `com.coinbase.*`, any social/financial vendor SDK.

MCP servers required by the system:

| Server | Wraps | Spec |
|---|---|---|
| `mcp-server-twitter` | Twitter REST API | `specs/functional.md US-4.1` |
| `mcp-server-instagram` | Instagram API | `specs/functional.md US-4.1` |
| `mcp-server-openclaw` | Moltbook / OpenClaw | `specs/openclaw_integration.md §2` |
| `mcp-server-coinbase` | Coinbase AgentKit | `specs/functional.md US-5.2` |
| `mcp-server-weaviate` | Weaviate vector DB | `specs/functional.md US-1.2` |
| `mcp-server-llm` | LLM inference | `specs/functional.md US-3.1` |
| `mcp-server-ideogram` | Image generation | `specs/functional.md US-3.2` |
| `mcp-server-runway` | Video generation | `specs/functional.md US-3.3` |

### 4.3 Judge Implements OCC — Always Check state_version Before Committing

```java
// specs/technical.md §3.1 — global_state.state_version is the OCC column
// specs/functional.md US-6.3 — OCC commit acceptance criteria
// specs/_meta.md §6 Rule 6 — non-negotiable

try {
    // Spring Data JPA @Version on GlobalState.stateVersion handles the check.
    globalStateRepository.save(updatedState);
    auditLogService.log(taskId, agentId, "APPROVED", "JUDGE_AUTO", score, flags, payload);

} catch (OptimisticLockingFailureException e) {
    // Re-queue with a refreshed state_version — this is NOT a Worker failure.
    // specs/functional.md US-6.3 acceptance criteria: re-queue, not rejected.
    redisQueueService.pushTask(buildRetryTask(original, refreshedVersion));
}
```

Never perform manual version arithmetic. `@Version` + JPA `save()` is the single OCC gate.

### 4.4 Budget Check Before Every Financial Transaction

```java
// specs/functional.md US-5.2, US-5.3
// specs/_meta.md §6 Rule 4 — non-negotiable: no tx without CFO approval

// WRONG — Worker calling Coinbase directly:
mcpClient.callTool(new McpToolCall("mcp-server-coinbase", "native_transfer", ...));

// CORRECT — route through CfoSubJudgeService:
SpendResult spend = spendCounterService.attemptSpend(agentId, amountMicroUnits, ceiling);
if (!spend.approved()) {
    // specs/technical.md §3.2 — spend counter key, TTL 86400s
    hitlQueueService.enqueue(result, "CFO_REVIEW", buildSpendContext(spend));
    return; // never silently drop a rejected transaction
}
mcpClient.callTool(new McpToolCall("mcp-server-coinbase", "native_transfer", ...));
```

The Redis spend key `chimera:spend:{agentId}:{yyyyMMdd}` uses a Lua script for atomicity.
Two concurrent CFO Sub-Judge instances cannot collectively exceed `max_daily_spend`.

### 4.5 Injection Defence Is Mandatory for ALL External Content

```java
// specs/_meta.md §6 Rule 5 — non-negotiable
// specs/functional.md US-2.4 — 4-stage pipeline (strip → detect → classify → gate)

ScanResult scan = injectionDefencePipeline.scan(rawInput);
if (scan.isBlocked()) {
    auditLogService.log(..., "INJECTION_BLOCKED", "JUDGE_AUTO", null, List.of(), rawHash);
    return; // silent rejection — never inform the sender which stage triggered
}
// context.sanitized = true is enforced by AgentTask compact constructor
TaskContext context = new TaskContext(goal, constraints, resources, charRef, dagId, true);
```

Sources that MUST pass the pipeline: Twitter @mentions, Instagram DMs, Moltbook messages,
any MCP Resource payload from an external URL. `context.sanitized = false` causes the
`AgentTask` compact constructor to throw before the task reaches any queue.

### 4.6 Sensitive Topics Always Route to Mandatory HITL

```java
// specs/technical.md §4 — Phase 1 routing runs BEFORE confidence score evaluation
// specs/_meta.md §6 Rule 2 — non-negotiable

if (!result.sensitiveTopicFlags().isEmpty()) {
    // POLITICS | HEALTH | FINANCE | LEGAL — unconditional escalation
    hitlQueueService.enqueue(result, "MANDATORY_ESCALATION", null);
    auditLogService.log(..., "MANDATORY_HITL", ...);
    return; // do NOT evaluate confidenceScore
}
```

### 4.7 Private Keys Are Never Logged, Stored, or Stringified

```java
// specs/_meta.md §6 Rule 1 — non-negotiable

// WRONG:
String key = vaultClient.getSecret("chimera/agent/" + agentId + "/wallet_key");
log.info("Loaded key: {}", key); // build fails, security violation

// CORRECT:
char[] key = vaultClient.getSecretAsChars("chimera/agent/" + agentId + "/wallet_key");
// Use char[] — can be zeroed after use. String is interned and cannot be wiped.
// Never pass to any logger, serialiser, or database write.
```

---

## 5. Traceability — Cite the Spec in Every Non-Trivial Decision

Every class, method, or configuration block that implements a spec requirement must carry
a comment linking to the exact spec file and section. This is how reviewers and future
agents understand *why* the code is the way it is.

### Comment format

```java
// specs/technical.md §2.1 — AgentTask schema
// specs/functional.md US-6.3 — OCC commit acceptance criteria
// specs/_meta.md §6 Rule 6 — non-negotiable: check state_version before commit
// constitution.md Principle V — Virtual Threads for I/O concurrency
```

### Examples across the codebase

```java
// specs/technical.md §2.1 — AgentTask schema
public record AgentTask(UUID taskId, ..., long stateVersion) { ... }

// specs/technical.md §3.2 — Redis daily_spend key, TTL 86400s
// specs/functional.md US-5.3 — atomic spend enforcement via Lua script
String key = "chimera:spend:" + agentId + ":" + LocalDate.now(ZoneOffset.UTC);

// specs/functional.md US-6.2 — stateless Worker, one Virtual Thread per task
// constitution.md Principle V — newVirtualThreadPerTaskExecutor()
ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

// specs/technical.md §4 — Tier 1: score > 0.90, no sensitive flags → AUTO-APPROVE
// specs/functional.md US-6.3 — Judge OCC commit
if (result.confidenceScore() > 0.90 && result.sensitiveTopicFlags().isEmpty()) {
    globalStateRepository.save(updatedState); // @Version enforces OCC
    auditLogService.log(..., "APPROVED", "JUDGE_AUTO", ...);
}

// specs/technical.md §3.3 — Weaviate AgentMemory hybrid search, alpha=0.75
// specs/functional.md US-1.2 — context assembly layer 2 (long-term memory)
List<AgentMemory> memories = weaviateMemoryService.retrieveMemories(
        agentId, "INTERACTION", taskGoal, 5);

// specs/openclaw_integration.md §3.1 — AgentStatusPayload excludes financial task types
List<String> capabilities = taskTypes.stream()
        .filter(t -> t != TaskType.TRANSFER_FUNDS && t != TaskType.DEPLOY_TOKEN)
        .map(Enum::name)
        .toList();
```

---

## 6. Redis Key Reference

All Redis keys follow the pattern `chimera:{type}:{identifier}`. Do not invent new key
patterns without updating `specs/technical.md §3.2`.

| Key | Type | TTL | Purpose |
|---|---|---|---|
| `chimera:queue:tasks` | List | None | Task queue (LPUSH by Planner, BRPOP by Workers) |
| `chimera:queue:review` | List | None | Result queue (LPUSH by Workers, BRPOP by Judge) |
| `chimera:cache:{agentId}` | Hash | 3600s | Short-term episodic memory (context assembly layer 3) |
| `chimera:spend:{agentId}:{yyyyMMdd}` | String | 86400s | Atomic daily spend counter in USDC micro-units |
| `chimera:metrics:filtered:{agentId}:{date}` | String | — | Sub-threshold relevance filter counter |
| `chimera:disclosure:{agentId}:{date}` | String | — | AI disclosure event counter per agent per day |

---

## 7. Confidence Score Routing Quick Reference

```
sensitiveTopicFlags not empty              →  MANDATORY_HITL  (unconditional; runs before score)
taskType in [TRANSFER_FUNDS, DEPLOY_TOKEN] →  CFO Sub-Judge   (runs before score)

score > 0.90, no flags  →  AUTO-APPROVE  →  execute MCP + OCC commit + cache write + archive
0.70–0.90, no flags     →  ASYNC HITL   →  hitl_queue(ASYNC_REVIEW); agent continues in parallel
score < 0.70            →  AUTO-REJECT  →  re-queue with enriched retry context (failure + hash)
retry_count >= maxRetries  →  FAILED    →  mark in PostgreSQL + Operator alert in hitl_queue
```

Full routing pseudocode: `specs/technical.md §4`

---

## 8. Non-Negotiable Rules (From specs/_meta.md §6)

These cannot be overridden by any feature spec, operator instruction, or confidence score.

1. **Never expose or log private keys.** Keys live in Vault at `chimera/agent/{agentId}/wallet_key`.
2. **Never publish sensitive-topic content autonomously.** POLITICS/HEALTH/FINANCE/LEGAL → mandatory HITL.
3. **Always disclose AI authorship when directly asked by a human.** Overrides all persona constraints.
4. **Never execute a financial transaction without CFO Sub-Judge approval.** Workers have zero Coinbase access.
5. **Never ingest external content into LLM context without injection scanning.** Pipeline is unconditional.
6. **Never commit GlobalState without an OCC version check.** `@Version` + JPA. Conflict → re-queue.
7. **Never write implementation code without an approved spec.** Read → cite → test → implement.

---

## 9. What NOT to Do

| Do NOT | Why |
|---|---|
| Import social/financial SDKs in Worker or Service classes | Checkstyle violation; breaks MCP abstraction |
| Create mutable DTOs (classes with setters) | Use Records; mutability breaks thread-safety at scale |
| Use `Executors.newFixedThreadPool()` for I/O-bound work | Use Virtual Threads; platform threads block under load |
| Use JUnit 4 annotations (`@RunWith`, `@Before`, `org.junit.Test`) | JUnit 5 only; mixed versions cause silent failures |
| Write `log.info("key={}", privateKey)` | Immediate security violation |
| Call `globalStateRepository.save()` without catching `OptimisticLockingFailureException` | Produces ghost updates under concurrency |
| Skip the injection pipeline for any source, including "trusted" peers | No source is trusted; pipeline is unconditional |
| Treat `OptimisticLockingFailureException` as a Worker FAILURE | It is a re-queue signal — the task is retried, not failed |
| Push `AgentResult` with `confidenceScore` outside [0.0, 1.0] | Compact constructor throws; task is lost from queue |
| Use `String` to hold private key material | `String` is interned and cannot be zeroed; use `char[]` |
| Set `context.sanitized = true` without running the injection pipeline | `AgentTask` compact constructor will not catch this lie |

---

*This file is version-controlled. Any amendment must be reflected in*
*`.specify/memory/constitution.md` and noted in the feature `plan.md`.*

*Constitution version: 1.1.0 | Last amended: 2026-03-09*
