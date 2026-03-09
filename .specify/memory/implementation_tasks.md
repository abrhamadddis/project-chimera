# Project Chimera — Implementation Tasks

**Generated**: 2026-03-09
**Sources**: `specs/technical.md`, `.specify/memory/task_breakdown.md`, `specs/functional.md`
**Constitution**: v1.1.0 | **Total Tasks**: 68

> **Prime Directive**: Read the relevant spec section before implementing each task.
> No implementation code without an approved spec (Constitution Principle II).
> Tests MUST be written before implementation code (Constitution Principle VI — TDD).

---

## Phase 1 — Project Setup

Project skeleton, build tooling, and quality gates. No business logic.

- [ ] T001 Initialise Maven project with `groupId=com.chimera`, `artifactId=chimera-orchestrator`, Java 21 compiler target in `pom.xml`
- [ ] T002 [P] Add Spring Boot 3.3 parent POM and core dependencies (spring-boot-starter, spring-boot-starter-data-jpa, spring-boot-starter-web) in `pom.xml`
- [ ] T003 [P] Add data layer dependencies in `pom.xml`: Jedis 5.x, Weaviate Java Client 4.x, PostgreSQL JDBC 42.7+, Jackson Databind 2.17 + JSR310 module
- [ ] T004 [P] Add test dependencies in `pom.xml`: JUnit 5 (jupiter), Mockito 5.x, Testcontainers 1.20 (postgresql, redis, generic), AssertJ 3.26, JaCoCo 0.8
- [ ] T005 [P] Add Checkstyle 10.x plugin to `pom.xml`; configure `failsOnError=true`; link rule file `config/checkstyle/chimera-rules.xml`
- [ ] T006 Create Checkstyle rule file `config/checkstyle/chimera-rules.xml`: ban `import com.twitter.*`, `import com.instagram.*`, `import com.coinbase.*` in `com.chimera.worker.*` and `com.chimera.service.*` packages; require Records for classes matching `*Task`, `*Result`, `*Persona`, `*Alert`
- [ ] T007 Create `src/main/resources/application.yml` with placeholder config blocks: `spring.datasource`, `spring.jpa`, `chimera.redis`, `chimera.weaviate`, `chimera.mcp`, `chimera.hitl`
- [ ] T008 Create `src/main/resources/application-test.yml` with Testcontainers-compatible overrides (JDBC URL via `@DynamicPropertySource`)

---

## Phase 2 — Foundational: Domain Records, Schema, Infrastructure

All foundational artefacts that every subsequent phase depends on. Tasks marked [P] can be worked in parallel.

### Java Records & Enums

- [ ] T009 [P] Write failing test `AgentTaskTest` in `src/test/java/com/chimera/domain/AgentTaskTest.java`: assert compact constructor throws `NullPointerException` for null `taskId`; throws `IllegalArgumentException` for `priority=0`, `priority=6`, `retryCount=-1`, `sanitized=false` in context
- [ ] T010 [P] Write failing test `AgentResultTest` in `src/test/java/com/chimera/domain/AgentResultTest.java`: assert compact constructor throws `IllegalArgumentException` for `confidenceScore=-0.1`, `confidenceScore=1.1`, and `null` required fields
- [ ] T011 [P] Write failing test `AgentPersonaTest` in `src/test/java/com/chimera/domain/AgentPersonaTest.java`: assert compact constructor throws `IllegalArgumentException` for empty `hardDirectives` list and null required fields
- [ ] T012 [P] Write failing test `TrendAlertTest` in `src/test/java/com/chimera/domain/TrendAlertTest.java`: assert compact constructor throws `IllegalArgumentException` for `relevanceScore=0.74`, and `expiresAt` not after `detectedAt`
- [ ] T013 Create enums in `src/main/java/com/chimera/domain/`: `TaskType.java` (POST_INSTAGRAM, POST_TWEET, REPLY_MENTION, GENERATE_IMAGE, GENERATE_VIDEO_TIER1, GENERATE_VIDEO_TIER2, BROADCAST_STATUS, TRANSFER_FUNDS, DEPLOY_TOKEN), `TaskStatus.java`, `ResultStatus.java`, `ContentType.java`, `SensitiveTopic.java`, `AgentAvailability.java`
- [ ] T014 Create `TaskContext` record in `src/main/java/com/chimera/domain/TaskContext.java`: fields `goal`, `personaConstraints`, `requiredMcpResources`, `@Nullable characterReferenceId`, `dagId`, `sanitized`; no compact constructor validation (validated by `AgentTask`)
- [ ] T015 Create `AgentTask` record in `src/main/java/com/chimera/domain/AgentTask.java`: all fields per `technical.md §2.1`; compact constructor validates non-null fields, priority range 1–5, retryCount ≥ 0, maxRetries ≥ 1, `context.sanitized() == true` — confirm T009 passes
- [ ] T016 Create `ResultOutput` record in `src/main/java/com/chimera/domain/ResultOutput.java`: fields `contentType`, `content`, `mcpToolCalled`, `@Nullable mcpToolResponse`; compact constructor validates non-null required fields
- [ ] T017 Create `AgentResult` record in `src/main/java/com/chimera/domain/AgentResult.java`: all fields per `technical.md §2.2`; compact constructor validates `confidenceScore` in [0.0, 1.0], `processingTimeMs ≥ 0` — confirm T010 passes
- [ ] T018 Create `AgentPersona` record in `src/main/java/com/chimera/domain/AgentPersona.java`: all fields per `technical.md §2.3`; compact constructor validates non-null fields, `hardDirectives` not empty — confirm T011 passes
- [ ] T019 Create `TrendAlert` record in `src/main/java/com/chimera/domain/TrendAlert.java`: all fields per `technical.md §2.4`; compact constructor validates `relevanceScore ≥ 0.75`, `expiresAt.isAfter(detectedAt)` — confirm T012 passes

### Jackson Configuration

- [ ] T020 Create `JacksonConfig.java` in `src/main/java/com/chimera/config/`: register `JavaTimeModule`, configure `MapperFeature.DEFAULT_VIEW_INCLUSION`, `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES=false`, global `@JsonNaming(SnakeCaseStrategy.class)` via `Jackson2ObjectMapperBuilderCustomizer`

### PostgreSQL Schema (Flyway)

- [ ] T021 [P] Add Flyway dependency to `pom.xml`; configure `spring.flyway.locations=classpath:db/migration` in `application.yml`
- [ ] T022 [P] Create migration `src/main/resources/db/migration/V1__create_agents.sql`: `agents` table with all columns per `technical.md §3.1`; include `UNIQUE` constraint on `wallet_address`
- [ ] T023 [P] Create migration `src/main/resources/db/migration/V2__create_campaigns.sql`: `campaigns` table with FK to `agents(agent_id)`, all columns including `max_daily_spend NUMERIC(18,8)`, `fan_token_enabled BOOLEAN`, `campaign_milestone_quota INT`
- [ ] T024 [P] Create migration `src/main/resources/db/migration/V3__create_global_state.sql`: `global_state` table with `state_version BIGINT NOT NULL DEFAULT 0` (Spring `@Version` column) and FK to `agents`
- [ ] T025 [P] Create migration `src/main/resources/db/migration/V4__create_audit_log.sql`: `audit_log` table; `action VARCHAR(64)`, `sensitive_flags VARCHAR(64)[]`, `payload_hash VARCHAR(64) NOT NULL`; add comment: `-- RETAIN 90 DAYS MINIMUM`
- [ ] T026 [P] Create migration `src/main/resources/db/migration/V5__create_hitl_queue.sql`: `hitl_queue` table with `spend_context JSONB` column for CFO_REVIEW type

### Redis Infrastructure

- [ ] T027 Write failing test `RedisQueueServiceTest` in `src/test/java/com/chimera/redis/RedisQueueServiceTest.java` using Testcontainers Redis: assert `pushTask()` results in list length 1; `popTask()` returns the same task; `popTask()` on empty queue returns `Optional.empty()`
- [ ] T028 Create `RedisConfig.java` in `src/main/java/com/chimera/config/`: configure `JedisPool` from `chimera.redis.host/port/maxTotal/maxIdle` properties; define bean `JedisPool jedisPool()`
- [ ] T029 Create `RedisQueueService.java` in `src/main/java/com/chimera/redis/`: methods `pushTask(AgentTask)` → `LPUSH chimera:queue:tasks`; `popTask()` → `BRPOP chimera:queue:tasks 5` returning `Optional<AgentTask>`; `pushResult(AgentResult)` → `LPUSH chimera:queue:review`; `popResult()` → `BRPOP chimera:queue:review 5` returning `Optional<AgentResult>`; all serialise via Jackson — confirm T027 passes
- [ ] T030 Create `EpisodicCacheService.java` in `src/main/java/com/chimera/redis/`: methods `writeCache(String agentId, Map<String,String> fields)` → `HSET` + `EXPIRE 3600`; `readCache(String agentId)` → `HGETALL` with TTL guard (return empty map if TTL < 1); `readCacheField(String agentId, String field)` → `HGET`
- [ ] T031 Create `SpendCounterService.java` in `src/main/java/com/chimera/redis/`: Lua script for atomic `INCRBY chimera:spend:{agentId}:{yyyyMMdd}`; method `attemptSpend(String agentId, long microUnits, long ceilingMicroUnits)` returning `SpendResult` record `(boolean approved, long newTotal, long ceiling)`; key TTL set to 86400s on first write

### Weaviate Infrastructure

- [ ] T032 Write failing test `WeaviateMemoryServiceTest` in `src/test/java/com/chimera/weaviate/WeaviateMemoryServiceTest.java` using Testcontainers Weaviate: assert `storeMemory()` does not throw; `retrieveMemories()` returns stored entry within result set
- [ ] T033 Create `WeaviateConfig.java` in `src/main/java/com/chimera/config/`: configure `WeaviateClient` from `chimera.weaviate.host/port/scheme`; define `AgentMemory` collection schema (all properties per `technical.md §3.3`) with `ensureCollection()` called at startup
- [ ] T034 Create `WeaviateMemoryService.java` in `src/main/java/com/chimera/weaviate/`: `storeMemory(AgentMemory memory)` → Weaviate object create; `retrieveMemories(String agentId, String memoryType, String query, int limit)` → hybrid search with `alpha=0.75` and mandatory `agentId` filter; `isDuplicate(String agentId, String memoryType, String content, double threshold)` → returns true if cosine similarity ≥ threshold — confirm T032 passes

### MCP Client

- [ ] T035 Write failing test `McpClientTest` in `src/test/java/com/chimera/mcp/McpClientTest.java`: mock `mcp-server-twitter.post_tweet`; assert `callTool()` returns non-null `McpToolResponse`; assert `MCPToolCall` includes `callerTaskId` and `callerAgentId` in the request
- [ ] T036 Create `McpToolCall` record in `src/main/java/com/chimera/mcp/McpToolCall.java`: fields `server`, `tool`, `arguments`, `disclosureLevel`, `callerTaskId`, `callerAgentId` per `technical.md §1.3`
- [ ] T037 Create `McpToolResponse` record in `src/main/java/com/chimera/mcp/McpToolResponse.java`: fields `success` (boolean), `body` (Map<String,Object>), `rawError` (@Nullable String)
- [ ] T038 Create `McpClient.java` in `src/main/java/com/chimera/mcp/`: method `callTool(McpToolCall call)` returning `McpToolResponse`; HTTP POST to MCP server using Spring's `RestClient`; throw `McpException` on non-2xx; log every call with task ID for audit trail — confirm T035 passes

---

## Phase 3 — US1: Orchestration Core (Planner-Worker-Judge Loop)

The end-to-end swarm loop. This phase produces a running PWJ cycle with real Redis queues.
**Independent test**: A synthetic `AgentTask` pushed to `chimera:queue:tasks` is consumed by a Worker, produces an `AgentResult` on `chimera:queue:review`, and is committed to `global_state` by the Judge — all within 2 seconds with no exceptions.

- [ ] T039 [US1] Write failing test `GlobalStateRepositoryTest` in `src/test/java/com/chimera/repository/GlobalStateRepositoryTest.java` (Testcontainers PG): assert `@Version` field increments on save; assert second concurrent save with stale version throws `OptimisticLockingFailureException`
- [ ] T040 [US1] Create `GlobalState` JPA entity in `src/main/java/com/chimera/entity/GlobalState.java`: fields `agentId` (PK), `stateJson` (JSONB via `@Type`), `@Version stateVersion`, `lastUpdatedAt`; annotate `@Entity`, `@Table(name="global_state")`
- [ ] T041 [US1] Create `GlobalStateRepository` interface in `src/main/java/com/chimera/repository/GlobalStateRepository.java`: extend `JpaRepository<GlobalState, String>`; add `findByAgentIdWithLock(String agentId)` with `@Lock(PESSIMISTIC_WRITE)` for safe reads before OCC write — confirm T039 passes
- [ ] T042 [US1] Write failing test `PlannerServiceTest` in `src/test/java/com/chimera/service/PlannerServiceTest.java`: mock `GlobalStateRepository` and `RedisQueueService`; assert `generateDag()` pushes N tasks to queue; assert Planner never calls `McpClient` directly; assert each task has non-null `dagId`
- [ ] T043 [US1] Create `PlannerService.java` in `src/main/java/com/chimera/service/PlannerService.java`: `generateDag(String agentId, String campaignId, String goal)` reads `GlobalState`, produces list of `AgentTask` siblings, `LPUSH` each via `RedisQueueService.pushTask()`; assigns shared `dagId = UUID.randomUUID()`; does NOT call `McpClient` — confirm T042 passes
- [ ] T044 [US1] Write failing test `WorkerServiceTest` in `src/test/java/com/chimera/service/WorkerServiceTest.java`: push one synthetic task to queue; invoke `processNext()`; assert `AgentResult` appears on review queue; assert no shared state mutation between two Worker invocations run concurrently
- [ ] T045 [US1] Create `WorkerService.java` in `src/main/java/com/chimera/service/WorkerService.java`: `processNext()` → `BRPOP chimera:queue:tasks 5`; execute task via `McpClient.callTool()`; build `AgentResult` with `confidenceScore` from LLM response; `LPUSH` result to `chimera:queue:review` via `RedisQueueService.pushResult()`; on any uncaught exception, build FAILURE result and push — confirm T044 passes
- [ ] T046 [US1] Create `WorkerExecutor.java` in `src/main/java/com/chimera/service/WorkerExecutor.java`: use `Executors.newVirtualThreadPerTaskExecutor()`; fan-out: for each task in queue depth snapshot, submit one `WorkerService.processNext()` callable; use `StructuredTaskScope.ShutdownOnFailure` for clean cancellation
- [ ] T047 [US1] Write failing test `JudgeServiceTest` in `src/test/java/com/chimera/service/JudgeServiceTest.java`: push synthetic `AgentResult` with `confidenceScore=0.95` to review queue; invoke `evaluateNext()`; assert `audit_log` row written with `action=APPROVED`; assert `global_state.state_version` incremented; assert `OptimisticLockingFailureException` triggers re-queue (not REJECTED)
- [ ] T048 [US1] Create `JudgeService.java` in `src/main/java/com/chimera/service/JudgeService.java`: `evaluateNext()` → `BRPOP chimera:queue:review 5`; run Phase 1 sensitive-topic detection; run Phase 2 confidence-score routing (>0.90 approve, 0.70–0.90 HITL, <0.70 reject); OCC commit via `GlobalStateRepository.save()` catching `OptimisticLockingFailureException` → re-queue with refreshed version; write to `audit_log` on every decision — confirm T047 passes
- [ ] T049 [US1] Create `AuditLogService.java` in `src/main/java/com/chimera/service/AuditLogService.java`: `log(UUID taskId, String agentId, String action, String actor, @Nullable Double confidenceScore, List<String> sensitiveFlags, String payloadJson)` computes SHA-256 of `payloadJson` and inserts into `audit_log`; method is synchronous and non-transactional (audit writes must not roll back with business logic)
- [ ] T050 [US1] Create `HitlQueueService.java` in `src/main/java/com/chimera/service/HitlQueueService.java`: `enqueue(AgentResult result, String queueType, @Nullable Map<String,Object> spendContext)` inserts into `hitl_queue`; `queueType` must be one of `ASYNC_REVIEW`, `MANDATORY_ESCALATION`, `CFO_REVIEW`; returns `queueId`

---

## Phase 4 — US2: Cognitive Core (Persona + Memory)

Persona loading, context assembly, and memory read/write.
**Independent test**: Load a SOUL.md fixture, assemble a context window, confirm all 3 layers present in the correct order.

- [ ] T051 [P] [US2] Write failing test `SoulMdParserTest` in `src/test/java/com/chimera/persona/SoulMdParserTest.java`: load test fixture `src/test/resources/fixtures/soul-zara.md`; assert `AgentPersona` constructed with correct field values; assert `MissingSoulMdFieldException` thrown when required field absent
- [ ] T052 [P] [US2] Write failing test `PersonaConstraintEnforcerTest` in `src/test/java/com/chimera/judge/PersonaConstraintEnforcerTest.java`: mock LLM classifier returning similarity 0.85; assert enforcer returns `PERSONA_VIOLATION` with matched directive; mock returning 0.70; assert enforcer returns `PASSED`
- [ ] T053 [US2] Create `SoulMdParser.java` in `src/main/java/com/chimera/persona/SoulMdParser.java`: reads SOUL.md file path from config; parses YAML frontmatter using SnakeYAML; constructs `AgentPersona` record; throws `MissingSoulMdFieldException` (checked) for any null required field; computes SHA-256 hash of file bytes — confirm T051 passes
- [ ] T054 [US2] Create `PersonaHashVerifier.java` in `src/main/java/com/chimera/persona/PersonaHashVerifier.java`: on startup, compares `SoulMdParser.computeHash()` against `agents.soul_md_hash` in PostgreSQL; throws `PersonaHashMismatchException` if mismatch; writes hash on first-time registration
- [ ] T055 [US2] Create `ContextWindowAssembler.java` in `src/main/java/com/chimera/service/ContextWindowAssembler.java`: `assemble(String agentId, String taskGoal)` → layer 1: serialize `AgentPersona.hardDirectives` + `voiceTone`; layer 2: `WeaviateMemoryService.retrieveMemories()` (empty = skip, no retry); layer 3: `EpisodicCacheService.readCache()` (TTL guard); combine into single structured prompt string with labelled sections
- [ ] T056 [US2] Create `PersonaConstraintEnforcer.java` in `src/main/java/com/chimera/judge/PersonaConstraintEnforcer.java`: for each `hardDirective`, call LLM classifier with directive and `output.content`; if any similarity ≥ 0.80, return `EnforcementResult.violation(directive)`; else return `EnforcementResult.passed()`; called by `JudgeService` before confidence-score routing — confirm T052 passes
- [ ] T057 [US2] Create `LongTermMemoryWriter.java` in `src/main/java/com/chimera/judge/LongTermMemoryWriter.java`: `writeIfWorthy(AgentResult result, McpToolResponse mcpResponse)` — check engagement signal in `mcpResponse.body()` (keys: `likes`, `replies`, `shares`); call `WeaviateMemoryService.isDuplicate()` at 0.97 threshold; write with `memoryType=INTERACTION` if no duplicate; log skip if duplicate

---

## Phase 5 — US3: Perception System (Polling + Filtering + Injection Defence)

External signal ingestion, relevance scoring, and security hardening.
**Independent test**: Feed a synthetic inbound message through the 4-stage pipeline; assert injection attempt is blocked at Stage 2 and logged; assert clean message is marked `sanitized=true`.

- [ ] T058 [P] [US3] Write failing test `InjectionDefencePipelineTest` in `src/test/java/com/chimera/perception/InjectionDefencePipelineTest.java`: (1) input containing ```` ```bash ``` ```` → assert blocked at Stage 1; (2) input containing "ignore previous instructions" → assert blocked at Stage 2; (3) mock classifier returning `injection_probability=0.30` → assert blocked at Stage 4; (4) clean input → assert `sanitized=true`
- [ ] T059 [P] [US3] Write failing test `SemanticRelevanceFilterTest` in `src/test/java/com/chimera/perception/SemanticRelevanceFilterTest.java`: mock LLM returning score 0.80 → assert `AgentTask` created with `priority=2`; mock returning score 0.92 → assert `priority=1`; mock returning score 0.60 → assert no task created, Redis metrics counter incremented
- [ ] T060 [US3] Create `InjectionDefencePipeline.java` in `src/main/java/com/chimera/perception/InjectionDefencePipeline.java`: stage 1 `stripExecutableContent(String)` — regex remove fenced code blocks, parameterised URLs (contains `?` + `=`), base64 patterns; stage 2 `detectInstructionOverride(String)` — case-insensitive match list of injection phrases; stage 3 `classifyIntent(String)` — call sandboxed Claude Haiku (no tools, system prompt: classifier only) returning `InjectionClassification` record `(double probability, String reason)`; stage 4 gate: reject if probability ≥ 0.25; on any rejection: write `INJECTION_BLOCKED` to `audit_log` and return `ScanResult.blocked(stage)`; on pass: return `ScanResult.safe()` — confirm T058 passes
- [ ] T061 [US3] Create `SemanticRelevanceFilter.java` in `src/main/java/com/chimera/perception/SemanticRelevanceFilter.java`: `score(String payload, String campaignGoal)` → call LLM (Claude Haiku) with fixed scoring prompt; parse float score from response; if score ≥ 0.75 derive priority (≥ 0.90 → 1, else → 2) and return `RelevanceResult.actionable(score, priority)`; else write to Redis metrics key `chimera:metrics:filtered:{agentId}:{date}` and return `RelevanceResult.discarded(score)` — confirm T059 passes
- [ ] T062 [US3] Create `McpResourcePoller.java` in `src/main/java/com/chimera/perception/McpResourcePoller.java`: runs in a background Virtual Thread; polls each URI in campaign's `requiredMcpResources` at configured interval (default 60s); on poll failure, retry once after 5s; on double failure, mark URI `DEGRADED` in campaign state and log `RESOURCE_DEGRADED` to `audit_log`; each successful payload passes through `InjectionDefencePipeline` then `SemanticRelevanceFilter` before `PlannerService` receives it
- [ ] T063 [US3] Create `TrendDetectionService.java` in `src/main/java/com/chimera/perception/TrendDetectionService.java`: `detectTrends(String agentId, String niche)` → store scored payload to Weaviate `memoryType=TREND`; query Weaviate hybrid search for cluster of ≥ 3 payloads in last 4 hours with cosine ≥ 0.80 and avg relevance ≥ 0.75; if cluster found, call LLM to summarise topic, construct `TrendAlert`, dedup against `global_state.state_json` via `trendId` before injecting

---

## Phase 6 — US4: Creative Engine (Content Generation + Duplicate Check)

Text, image, and video content generation via MCP tools.
**Independent test**: A `POST_INSTAGRAM` task enters a Worker; the Worker returns an `AgentResult` with `contentType=TEXT`, valid `confidenceScore`, and `sensitiveTopicFlags=[]`; the Judge runs a duplicate check and archives the content.

- [ ] T064 [P] [US4] Write failing test `TextContentWorkerTest` in `src/test/java/com/chimera/worker/TextContentWorkerTest.java`: mock `McpClient` returning JSON `{content, confidenceScore: 0.91, sensitiveTopicFlags: []}`; assert `AgentResult.status=SUCCESS`; mock response missing `confidenceScore`; assert `AgentResult.status=FAILURE`
- [ ] T065 [P] [US4] Write failing test `DuplicateContentCheckerTest` in `src/test/java/com/chimera/judge/DuplicateContentCheckerTest.java`: mock `WeaviateMemoryService.isDuplicate()` returning true at 0.92; assert checker returns `DUPLICATE_CONTENT` rejection with top-3 similar records; mock returning false; assert checker returns `PASSED`
- [ ] T066 [US4] Create `TextContentWorker.java` in `src/main/java/com/chimera/worker/TextContentWorker.java`: accepts `AgentTask` where `taskType` is `POST_INSTAGRAM`, `POST_TWEET`, or `REPLY_MENTION`; assembles context via `ContextWindowAssembler`; calls `McpClient.callTool()` targeting `mcp-server-llm.generate_text`; parses JSON response for `content`, `confidenceScore`, `sensitiveTopicFlags`; on missing field: return FAILURE result; on character-limit breach: one retry with explicit length constraint in prompt — confirm T064 passes
- [ ] T067 [US4] Create `DuplicateContentChecker.java` in `src/main/java/com/chimera/judge/DuplicateContentChecker.java`: `check(AgentResult result)` → query Weaviate for `CONTENT_ARCHIVE` records at ≥ 0.92 cosine similarity; if match found, return `CheckResult.duplicate(top3Records)` which triggers AUTO-REJECT with top-3 records in retry context; else return `CheckResult.unique()`; wire into `JudgeService` before confidence-score routing — confirm T065 passes
- [ ] T068 [US4] Create `ContentArchiveWriter.java` in `src/main/java/com/chimera/judge/ContentArchiveWriter.java`: called by `JudgeService` after AUTO-APPROVE and confirmed MCP publish; writes `AgentMemory` to Weaviate with `memoryType=CONTENT_ARCHIVE`, `platform` derived from `mcpToolCalled`, `taskId` from `AgentResult`

---

## Phase 7 — US5: Action System (Publishing + Mention Loop + AI Disclosure)

Social media publication and inbound engagement processing.
**Independent test**: A `REPLY_MENTION` task produces a published reply via mock `mcp-server-twitter`; mention ID is written to episodic cache; a second identical mention ID is skipped.

- [ ] T069 [P] [US5] Write failing test `PublishingWorkerTest` in `src/test/java/com/chimera/worker/PublishingWorkerTest.java`: mock `McpClient` returning `{post_id, url}`; assert `MCPToolCall.disclosureLevel=automated`; mock returning null body; assert `AgentResult.status=FAILURE` (no silent retry)
- [ ] T070 [P] [US5] Write failing test `AiDisclosureDetectorTest` in `src/test/java/com/chimera/worker/AiDisclosureDetectorTest.java`: mock LLM returning disclosure intent confidence 0.85; assert response is templated disclosure (no persona styling); assert `confidenceScore=1.0` in `AgentResult`; assert `audit_log` entry with `action=AI_DISCLOSURE`
- [ ] T071 [US5] Create `PublishingWorker.java` in `src/main/java/com/chimera/worker/PublishingWorker.java`: routes `POST_INSTAGRAM` → `mcp-server-instagram.post_media`; `POST_TWEET` → `mcp-server-twitter.post_tweet`; `REPLY_MENTION` → appropriate server; sets `disclosureLevel` based on Judge decision (passed via `TaskContext`); records `post_id` + `url` in `mcpToolResponse`; null/error response → FAILURE result immediately — confirm T069 passes
- [ ] T072 [US5] Create `MentionIngestionPoller.java` in `src/main/java/com/chimera/worker/MentionIngestionPoller.java`: polls `twitter://mentions/recent` and `instagram://mentions/recent` via `McpClient`; for each mention: (1) check `last_mention_id` in `EpisodicCacheService` → skip if present; (2) pass through `InjectionDefencePipeline`; (3) pass through `SemanticRelevanceFilter`; (4) create `AgentTask` with `taskType=REPLY_MENTION` via `PlannerService`; (5) write `last_mention_id` to episodic cache
- [ ] T073 [US5] Create `AiDisclosureDetector.java` in `src/main/java/com/chimera/worker/AiDisclosureDetector.java`: classifies inbound message intent using LLM; at ≥ 0.80 confidence triggers disclosure path; disclosure reply is a fixed template (not persona-styled); builds `AgentResult` with `confidenceScore=1.0`; logs `AI_DISCLOSURE` to `AuditLogService`; increments Redis counter `chimera:disclosure:{agentId}:{date}` — confirm T070 passes

---

## Phase 8 — US6: Agentic Commerce (Wallet + CFO Gate + Transactions)

Non-custodial wallet provisioning and CFO-gated financial execution.
**Independent test**: A `TRANSFER_FUNDS` task is intercepted by `CfoSubJudgeService`; atomic spend check approves if within limit and rejects with `CFO_REVIEW` HITL entry if limit exceeded.

- [ ] T074 [P] [US6] Write failing test `WalletProvisioningServiceTest` in `src/test/java/com/chimera/commerce/WalletProvisioningServiceTest.java`: mock Vault client returning a private key; assert derived wallet address matches mock `AgentPersona.walletAddress`; assert `WalletAddressMismatchException` thrown on mismatch; assert key is NOT written to any log output
- [ ] T075 [P] [US6] Write failing test `CfoSubJudgeServiceTest` in `src/test/java/com/chimera/commerce/CfoSubJudgeServiceTest.java`: mock `SpendCounterService.attemptSpend()` returning approved; assert transaction proceeds to `McpClient`; mock returning rejected; assert `hitl_queue` entry created with `CFO_REVIEW` and `spend_context`; assert `McpClient` NOT called
- [ ] T076 [US6] Create `WalletProvisioningService.java` in `src/main/java/com/chimera/commerce/WalletProvisioningService.java`: retrieves private key from AWS Secrets Manager (path `chimera/agent/{agentId}/wallet_key`) using AWS SDK or Vault HTTP API; holds key as `char[]` in memory (never `String`); derives wallet address; compares against `AgentPersona.walletAddress`; throws `WalletAddressMismatchException` on mismatch; called once at agent startup — confirm T074 passes
- [ ] T077 [US6] Create `CfoSubJudgeService.java` in `src/main/java/com/chimera/commerce/CfoSubJudgeService.java`: intercepts `AgentResult` where `taskType` is `TRANSFER_FUNDS` or `DEPLOY_TOKEN`; calls `SpendCounterService.attemptSpend(agentId, amount, ceiling)`; if approved: forward `McpToolCall` to `mcp-server-coinbase.native_transfer` or `deploy_token`; if rejected: call `HitlQueueService.enqueue(result, CFO_REVIEW, spendContext)`; never swallows rejections silently — confirm T075 passes
- [ ] T078 [US6] Create `OnChainTransactionRecorder.java` in `src/main/java/com/chimera/commerce/OnChainTransactionRecorder.java`: on confirmed `mcp-server-coinbase` response containing `transaction_hash`; writes to `audit_log` with `action=TRANSACTION_EXECUTED`, `actor=CFO_JUDGE`, `payload_hash`; records hash for blockchain ledger verification

---

## Final Phase — Polish & Cross-Cutting Concerns

- [ ] T079 Create `StatusBroadcastWorker.java` in `src/main/java/com/chimera/worker/StatusBroadcastWorker.java`: builds `AgentStatusPayload` record (excludes `TRANSFER_FUNDS`, `DEPLOY_TOKEN` from capabilities list; excludes backstory, voiceTone, private key); calls `mcp-server-openclaw.publish_status`; result bypasses confidence routing in `JudgeService` (`task_type=BROADCAST_STATUS` → AUTO-APPROVE unconditionally); idempotency via `last_broadcast_at` in episodic cache
- [ ] T080 Create `FailureRePlanningService.java` in `src/main/java/com/chimera/service/FailureRePlanningService.java`: monitors review queue for FAILURE results; checks `retry_count < max_retries`; re-creates `AgentTask` with `retry_count+1`, enriched `TaskContext` (appends failure reason and output hash as negative example); on exhaustion: marks task FAILED in PostgreSQL and enqueues Operator alert via `HitlQueueService`
- [ ] T081 Create `DagOrphanCleanupService.java` in `src/main/java/com/chimera/service/DagOrphanCleanupService.java`: runs on Planner startup; scans Redis list `chimera:queue:tasks` for tasks belonging to incomplete DAGs (identified by `dag_id`); re-queues valid tasks; marks irreversibly stale tasks FAILED in PostgreSQL
- [ ] T082 Add structured logging in `WorkerService`, `JudgeService`, `PlannerService`: every decision emits JSON log event with minimum fields `agent_role`, `task_id`, `status`, `timestamp`, `payload_hash` (Constitution Principle III); use SLF4J with Logback JSON encoder
- [ ] T083 Add Spring Boot Actuator health endpoints: custom `HealthIndicator` beans for Redis connectivity, Weaviate connectivity, PostgreSQL Flyway migration status; expose at `management.endpoints.web.exposure.include=health,info,metrics`
- [ ] T084 Add JaCoCo `verify` goal to `pom.xml` with `minimum=0.80` line coverage rule on `com.chimera.domain.*`, `com.chimera.service.*`, `com.chimera.judge.*`; fail build on regression

---

## Summary

| Phase | Tasks | Parallelizable | Story |
|---|---|---|---|
| Phase 1 — Setup | T001–T008 | T002, T003, T004, T005 | — |
| Phase 2 — Foundation | T009–T038 | T009–T012, T021–T026 | — |
| Phase 3 — Orchestration Core | T039–T050 | T039 | US1 |
| Phase 4 — Cognitive Core | T051–T057 | T051, T052 | US2 |
| Phase 5 — Perception | T058–T063 | T058, T059 | US3 |
| Phase 6 — Creative Engine | T064–T068 | T064, T065 | US4 |
| Phase 7 — Action System | T069–T073 | T069, T070 | US5 |
| Phase 8 — Commerce | T074–T078 | T074, T075 | US6 |
| Final — Polish | T079–T084 | — | — |

**MVP Scope** (Phase 1 + 2 + 3 only): Delivers a running Planner-Worker-Judge loop with real Redis queues, PostgreSQL OCC commits, and HITL routing — the core correctness requirement from which all other modules extend.

**TDD sequence per task**: Write failing test → confirm RED → implement → confirm GREEN → refactor.
All test tasks are the first entry in each story phase and are marked [P] where the test file has no dependency on sibling test files.
