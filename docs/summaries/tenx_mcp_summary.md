# Summary: Tenx MCP Analysis & IDE Connection Guide (PDF)

**Source**: `docs/pdf/Tenx MCP Analysis & IDE Connection Guide.pdf`
**Type**: Internal Tool Documentation / Setup Guide
**Summarized**: 2026-03-08

---

## Key Concepts

### What Tenx MCP Analysis Is
- A specialized MCP server built by the Tenacious Intelligence team to **gather structured data about developer interactions with a Coding Agent** in real-time
- Acts as a **non-invasive "Black Box" flight recorder** — captures LLM reasoning and developer behavior without interrupting the workflow
- The LLM acts as an evaluator: it uses a predefined rubric to rate and summarize the quality of its own interaction with the developer
- Data is used to measure competencies: clarity of instructions, context provision, demonstrated skills

### Core Idea: LLM-as-Evaluator
- As the LLM processes a developer prompt, it **simultaneously formulates a log interaction request** to the Tenx Analysis MCP
- This background tool call is populated with the LLM's own analysis of the interaction
- The IDE receives both: the user-facing response AND the background logging tool call
- The Tenx MCP server adds the developer's GitHub ID and writes the structured record to a database
- Result: **high-fidelity, structured data on human-AI collaboration** captured automatically

### Two Log Types

#### 1. Passage of Time Log
Periodically captures a snapshot of the developer's current task:
- Primary intent of the task
- Summary of the conversation so far
- Scores for: instruction clarity, context provided
- Number of turns, context changes, demonstrated competencies

#### 2. Performance Outlier Log
Triggered when exceptionally good or poor performance is detected:
- Performance category: `efficient` / `inefficient` / `stalled`
- Performance rating summary
- User feedback captured
- Task intent, prompt clarity, context provided, turn count at time of outlier

### Connection Guide — VS Code
1. Update VS Code to latest version
2. Install **GitHub Copilot** + **GitHub Copilot Chat** extensions (both required)
3. Create `.github/copilot-instructions.md` in working directory
4. Create `.vscode/mcp.json` with the server configuration
5. Add the Tenx MCP server via "Add Server..." button
6. Server URL: `https://mcppulse.10academy.org/proxy`
7. Server name: `tenxfeedbackanalytics`
8. Authenticate via GitHub OAuth (TenxMCPPulse by get10aclous)

**VS Code `mcp.json` config:**
```json
{
  "servers": {
    "tenxfeedbackanalytics": {
      "url": "https://mcppulse.10academy.org/proxy",
      "type": "http",
      "headers": {
        "X-Device": "<mac|linux|windows>",
        "X-Coding-Tool": "vscode"
      }
    }
  },
  "inputs": {}
}
```

### Connection Guide — Cursor
1. Update Cursor to latest version
2. Create `.cursor/rules/agent.mdc` in working directory
3. Create `.cursor/mcp.json` with server config (same structure as VS Code but `"X-Coding-Tool": "cursor"`)
4. Enable the server toggle → "Connect" button → GitHub OAuth authentication
5. After connection: "3 tools, 1 prompts enabled" visible in Cursor's Tools & MCP panel

**Cursor `mcp.json` config:**
```json
{
  "mcpServers": {
    "tenxfeedbackanalytics": {
      "name": "tenxanalysismcp",
      "url": "https://mcppulse.10academy.org/proxy",
      "headers": {
        "X-Device": "<mac|linux|windows>",
        "X-Coding-Tool": "cursor"
      }
    }
  }
}
```

### Connection Guide — Claude Code (CLI)
```bash
claude mcp add --transport http tenxfeedbackanalytics \
  https://mcppulse.10academy.org/proxy \
  -H "X-Device: linux" \
  -H "X-Coding-Tool: claude"
```
Then run `claude` → `/mcp` → navigate to `tenxfeedbackanalytics` → authenticate via GitHub OAuth.

### Tools Exposed by Tenx MCP Server
- `log_passage_time_trigger` — triggers a periodic snapshot log
- `log_performance_outlier_trigger` — triggers an outlier performance log
- `list_managed_servers` — lists connected managed servers
- 1 prompt template enabled (visible in IDE tool panel)

### Authentication Model
- GitHub OAuth via `TenxMCPPulse` by `get10aclous`
- Associates all telemetry with the developer's **GitHub account** (same account used for project submission)
- Public data only — limited access scope

---

## Relevance to Project Chimera

- **Mandatory requirement**: The challenge brief explicitly states "Tenx MCP Sense must be connected to your IDE at all times" — it is a **graded assessment criterion**, not optional tooling
- **The MCP server IS the observability layer for Day 1-2 work**: Every meaningful interaction with the coding agent (Claude Code) is logged and tied to the developer's GitHub identity — assessors can verify the "thinking" process
- **Validates MCP as the universal integration pattern**: Tenx MCP itself is an example of exactly what Chimera builds — a specialized MCP server that wraps a service (the Tenx analytics backend) and exposes structured tools to an LLM agent
- **The LLM-as-evaluator pattern** is architecturally identical to Chimera's **Judge** role — both use an LLM to evaluate the output of another LLM process against a predefined rubric

---

## Architectural Decisions It Forces

1. **Claude Code (`claude` CLI) must have the `tenxfeedbackanalytics` MCP server connected** throughout the development session — set up via `claude mcp add` command before starting any work
2. The `X-Coding-Tool: claude` and `X-Device: linux` headers MUST be set correctly — incorrect headers will result in misattributed or missing telemetry
3. **The `.github/copilot-instructions.md` (VS Code) or `.cursor/rules/agent.mdc` (Cursor) files are prerequisites** for the MCP server to function correctly — these must exist before the MCP server is started
4. The Tenx MCP's `list_managed_servers`, `log_passage_time_trigger`, and `log_performance_outlier_trigger` tools will appear in the agent's tool palette — the agent (Claude Code) will call these automatically as part of its interaction logging; **developers should not suppress or block these calls**

---

## Open Questions for the Specs

- Should the Chimera project's own internal **observability MCP server** (for monitoring agent performance, confidence scores, and HITL queue depth) be architecturally modeled after the Tenx MCP pattern — i.e., an LLM evaluator that logs structured data about other agent interactions?
- The Tenx MCP uses GitHub identity as the developer ID. Should Chimera agents use a similar **identity anchor** (wallet address, GitHub account, or DID) to maintain consistent identity across MCP server interactions?
- The `log_performance_outlier_trigger` tool — which fires on exceptionally good or poor performance — is structurally identical to Chimera's **Judge escalation logic** (high-confidence auto-approve, low-confidence escalate). Should Chimera's Judge service expose a similar MCP tool that external monitoring systems can subscribe to?
- The Tenx MCP server is accessed via an HTTP proxy (`https://mcppulse.10academy.org/proxy`) with SSE transport for remote servers. For Chimera's own MCP servers that will be deployed remotely (not locally), is this the correct transport choice over Stdio?
