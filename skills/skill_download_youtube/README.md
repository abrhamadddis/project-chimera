# skill_download_youtube

**Version**: 1.0.0 | **Status**: Spec-complete, pending implementation
**Traces to**: `specs/functional.md` US-5.2 (Agentic Commerce), `specs/technical.md` §1.3 (MCPToolCall)
**Test contract**: `src/test/java/org/chimera/SkillsInterfaceTest.java`

---

## Description

Downloads a YouTube video to a local output directory using `yt-dlp` and normalises the
container format via `ffmpeg`. Returns the resolved file path, detected MIME type, and
file size for downstream skills (e.g. `skill_transcribe_audio`).

This skill is invoked by Workers assigned `task_type = DOWNLOAD_VIDEO` and is routed
exclusively through the MCP Tool layer — no direct yt-dlp subprocess call is permitted
in Worker business logic (Constitution Principle IV). The skill validates the URL before
any network activity and enforces a configurable maximum duration ceiling to prevent
runaway storage costs.

Budget enforcement applies: the CFO Sub-Judge charges the download's estimated compute
cost against `chimera:spend:{agent_id}:{YYYYMMDD}` before execution begins. If the daily
ceiling is exceeded, `BudgetExceededException` is raised and no download is attempted
(specs/functional.md US-5.3).

---

## Input Contract

```json
{
  "video_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "output_dir": "/tmp/chimera/downloads/agent-zara-001",
  "max_duration_seconds": 600
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `video_url` | string | Yes | Must be a valid `https://` YouTube URL containing a `v=` video ID; `ftp://`, non-YouTube domains, blank strings, and open-redirect patterns are rejected before any network call |
| `output_dir` | string | Yes | Must be an absolute path to a writable directory; the skill does not create parent directories |
| `max_duration_seconds` | integer | Yes | Must be > 0 and ≤ 3600 (1 hour ceiling); videos exceeding this duration are rejected after metadata fetch, before download begins |

---

## Output Contract

```json
{
  "file_path": "/tmp/chimera/downloads/agent-zara-001/dQw4w9WgXcQ.mp4",
  "duration_seconds": 212,
  "file_size_bytes": 31457280
}
```

| Field | Type | Guarantees |
|---|---|---|
| `file_path` | string | Absolute path to the downloaded file; always ends in `.mp4`, `.webm`, or `.mkv`; file exists and is readable at return time |
| `duration_seconds` | integer | Extracted from video metadata by `yt-dlp`; matches the actual media duration within ±1 second |
| `file_size_bytes` | long | Exact byte count of the file at `file_path`; always > 0 |

---

## Errors

| Exception | Thrown When |
|---|---|
| `IllegalArgumentException` | `video_url` is null, blank, uses a non-`https://` scheme, points to a non-YouTube domain, lacks a `v=` video ID, or is a known injection pattern (`javascript:`, open-redirect) |
| `DurationExceededException` | Video metadata reports a duration greater than `max_duration_seconds`; no download is attempted |
| `DownloadFailedException` | `yt-dlp` process exits non-zero, network timeout, or `ffmpeg` normalisation step fails; wraps the underlying process exit code and stderr |
| `BudgetExceededException` | CFO Sub-Judge's daily spend counter would exceed `campaign.max_daily_spend` after charging this download's compute cost; carries `agentId`, `currentSpendMicroUnits`, `dailyCeilingMicroUnits` (specs/functional.md US-5.3) |

All exceptions surface to the Worker, which writes a FAILURE `AgentResult` to
`chimera:queue:review`; the task is never silently dropped.

---

## Example Usage

### Happy path — valid URL within budget

```json
{
  "request": {
    "video_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "output_dir": "/tmp/chimera/downloads/agent-zara-001",
    "max_duration_seconds": 600
  },
  "response": {
    "file_path": "/tmp/chimera/downloads/agent-zara-001/dQw4w9WgXcQ.mp4",
    "duration_seconds": 212,
    "file_size_bytes": 31457280
  }
}
```

### Error — invalid URL

```json
{
  "request": {
    "video_url": "https://www.vimeo.com/123456",
    "output_dir": "/tmp/chimera/downloads/agent-zara-001",
    "max_duration_seconds": 600
  },
  "error": {
    "type": "IllegalArgumentException",
    "message": "URL must be a valid YouTube URL containing a video ID, got: https://www.vimeo.com/123456"
  }
}
```

### Error — budget exhausted

```json
{
  "request": {
    "video_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "output_dir": "/tmp/chimera/downloads/agent-zara-001",
    "max_duration_seconds": 600
  },
  "error": {
    "type": "BudgetExceededException",
    "agentId": "agent-zara-001",
    "currentSpendMicroUnits": 5000000,
    "dailyCeilingMicroUnits": 5000000
  }
}
```

### Error — video too long

```json
{
  "request": {
    "video_url": "https://www.youtube.com/watch?v=longVideoId",
    "output_dir": "/tmp/chimera/downloads/agent-zara-001",
    "max_duration_seconds": 60
  },
  "error": {
    "type": "DurationExceededException",
    "message": "Video duration 3842s exceeds max_duration_seconds limit of 60s"
  }
}
```

---

## Dependencies

| Dependency | Version | Role |
|---|---|---|
| `yt-dlp` | 2024.x+ | Video metadata fetch and download; must be on `PATH` or configured via `YTDLP_BIN` env var |
| `ffmpeg` | 6.x+ | Container normalisation to MP4/WebM; must be on `PATH` or configured via `FFMPEG_BIN` env var |

### Installation

```bash
# yt-dlp
pip install -U yt-dlp

# ffmpeg (Ubuntu/Debian)
apt-get install -y ffmpeg

# ffmpeg (macOS)
brew install ffmpeg
```

### Runtime environment variables

| Variable | Default | Description |
|---|---|---|
| `YTDLP_BIN` | `yt-dlp` | Absolute path to yt-dlp binary; overrides PATH lookup |
| `FFMPEG_BIN` | `ffmpeg` | Absolute path to ffmpeg binary; overrides PATH lookup |
| `CHIMERA_DOWNLOAD_TMPDIR` | `/tmp/chimera/downloads` | Default output root if `output_dir` is not supplied by caller |

---

## Implementation Notes

- URL validation runs entirely in-process before any subprocess is spawned; the YouTube
  domain check uses allowlist matching, not regex on the full URL, to prevent bypass via
  crafted URLs (specs/functional.md US-2.4, injection defence Rule 5).
- The skill calls `yt-dlp --dump-json` first to fetch metadata without downloading;
  duration enforcement happens at this stage.
- `yt-dlp` is invoked with `--format bestvideo[ext=mp4]+bestaudio[ext=m4a]/mp4` to
  prefer MP4 containers; `ffmpeg` merges streams if yt-dlp returns separate tracks.
- Temporary files are written to `{output_dir}/.tmp/` and atomically renamed to the
  final path on completion; a partial download never appears at the output path.
- All subprocess invocations use `ProcessBuilder` with explicit argument lists — no
  shell interpolation of user-supplied values (OWASP command injection prevention).
