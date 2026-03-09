# skill_transcribe_audio

**Version**: 1.0.0 | **Status**: Spec-complete, pending implementation
**Traces to**: `specs/functional.md` US-5.2 (Agentic Commerce), `specs/technical.md` §1.3 (MCPToolCall)
**Test contract**: `src/test/java/org/chimera/SkillsInterfaceTest.java`

---

## Description

Transcribes an audio file to text using the OpenAI Whisper API or a locally hosted Whisper
model. Returns the full transcript, detected language code, confidence score, and audio
duration for downstream Workers (e.g. content summarisation, caption generation).

This skill is invoked by Workers that need speech-to-text as part of a content pipeline
and is routed exclusively through the MCP Tool layer — no direct Whisper API call or SDK
import is permitted in Worker business logic (Constitution Principle IV). The skill
validates the audio file extension and MIME type before submitting to the transcription
backend and rejects non-audio inputs immediately.

Budget enforcement applies: the CFO Sub-Judge charges the estimated transcription cost
(derived from audio duration) against `chimera:spend:{agent_id}:{YYYYMMDD}` before
execution begins. If the daily ceiling is exceeded, `BudgetExceededException` is raised
and no transcription is attempted (specs/functional.md US-5.3).

---

## Input Contract

```json
{
  "audio_file_path": "/tmp/chimera/downloads/agent-zara-001/dQw4w9WgXcQ.mp3",
  "language": "en",
  "model": "whisper-1"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `audio_file_path` | string | Yes | Must be an absolute path to an existing, readable file with a recognised audio extension: `.mp3`, `.mp4`, `.m4a`, `.wav`, `.ogg`, `.flac`, `.webm`; image, text, or binary files are rejected before any API call |
| `language` | string | No | ISO 639-1 code (e.g. `"en"`, `"fr"`, `"ja"`) or BCP-47 tag (e.g. `"en-US"`); if omitted or `null`, Whisper auto-detects the language |
| `model` | string | Yes | Whisper model identifier; accepted values: `whisper-1` (API), `base`, `small`, `medium`, `large-v3` (local); unknown values are rejected before dispatch |

---

## Output Contract

```json
{
  "transcript": "Never gonna give you up, never gonna let you down…",
  "confidence": 0.97,
  "duration_seconds": 212
}
```

| Field | Type | Guarantees |
|---|---|---|
| `transcript` | string | Non-null, non-blank string of the transcribed speech; may contain punctuation, paragraph breaks, and speaker-turn markers depending on model; never empty for valid audio |
| `confidence` | float | Float in `[0.0, 1.0]`; derived from Whisper's segment-level log-probabilities averaged across the full file; lower scores indicate noisy or ambiguous audio |
| `duration_seconds` | integer | Duration of the audio file in whole seconds as reported by the transcription backend; always > 0 for valid audio |

---

## Errors

| Exception | Thrown When |
|---|---|
| `IllegalArgumentException` | `audio_file_path` is null, the file does not exist, the file extension is not a recognised audio format (message contains the word `"audio"`), or `model` is an unknown identifier |
| `FileNotFoundException` | File exists at the time of validation but becomes unreadable before the transcription call (race condition during parallel pipeline execution) |
| `UnsupportedLanguageException` | `language` is provided but the selected model does not support it; carries `requestedLanguage` and `supportedLanguages` fields |
| `TranscriptionFailedException` | Whisper API returns a non-200 status, the local model process exits non-zero, or the response JSON is malformed; wraps the underlying HTTP status code or process exit code |
| `BudgetExceededException` | CFO Sub-Judge's daily spend counter would exceed `campaign.max_daily_spend` after charging this transcription's estimated cost; carries `agentId`, `currentSpendMicroUnits`, `dailyCeilingMicroUnits` (specs/functional.md US-5.3) |

All exceptions surface to the Worker, which writes a FAILURE `AgentResult` to
`chimera:queue:review`; the task is never silently dropped.

---

## Example Usage

### Happy path — API transcription

```json
{
  "request": {
    "audio_file_path": "/tmp/chimera/downloads/agent-zara-001/dQw4w9WgXcQ.mp3",
    "language": "en",
    "model": "whisper-1"
  },
  "response": {
    "transcript": "Never gonna give you up, never gonna let you down…",
    "confidence": 0.97,
    "duration_seconds": 212
  }
}
```

### Happy path — local model, auto language detection

```json
{
  "request": {
    "audio_file_path": "/tmp/chimera/audio/milan-fw-clip.wav",
    "language": null,
    "model": "large-v3"
  },
  "response": {
    "transcript": "Le collezioni di questa stagione riflettono un ritorno alle radici…",
    "confidence": 0.91,
    "duration_seconds": 47
  }
}
```

### Error — non-audio file

```json
{
  "request": {
    "audio_file_path": "/tmp/chimera/assets/thumbnail.jpg",
    "language": "en",
    "model": "whisper-1"
  },
  "error": {
    "type": "IllegalArgumentException",
    "message": "audio_file_path must point to a recognised audio file; '.jpg' is not a supported audio extension"
  }
}
```

### Error — budget exhausted

```json
{
  "request": {
    "audio_file_path": "/tmp/chimera/audio/long-interview.mp3",
    "language": "en",
    "model": "whisper-1"
  },
  "error": {
    "type": "BudgetExceededException",
    "agentId": "agent-zara-001",
    "currentSpendMicroUnits": 5000000,
    "dailyCeilingMicroUnits": 5000000
  }
}
```

### Error — unsupported language

```json
{
  "request": {
    "audio_file_path": "/tmp/chimera/audio/clip.mp3",
    "language": "xx-INVALID",
    "model": "base"
  },
  "error": {
    "type": "UnsupportedLanguageException",
    "requestedLanguage": "xx-INVALID",
    "supportedLanguages": ["en", "fr", "de", "es", "it", "ja", "zh", "pt", "nl", "ko"]
  }
}
```

---

## Dependencies

| Dependency | Version | Role |
|---|---|---|
| OpenAI Whisper API | — | Cloud transcription via `POST /v1/audio/transcriptions`; requires `OPENAI_API_KEY` |
| `openai-whisper` (local) | 20231117+ | Local Python model runner; used when `model` is `base`, `small`, `medium`, or `large-v3` |
| `ffmpeg` | 6.x+ | Audio format normalisation before submission; converts unsupported containers to WAV |

### Installation

```bash
# Whisper API (no local install required — set API key only)
export OPENAI_API_KEY=sk-...

# Local Whisper model (optional — only needed for non-API models)
pip install openai-whisper

# ffmpeg (Ubuntu/Debian)
apt-get install -y ffmpeg

# ffmpeg (macOS)
brew install ffmpeg
```

### Runtime environment variables

| Variable | Default | Description |
|---|---|---|
| `OPENAI_API_KEY` | — | Required when `model = whisper-1`; omit for local-only deployments |
| `WHISPER_BACKEND` | `api` | `api` uses the OpenAI endpoint; `local` invokes the `openai-whisper` Python package |
| `WHISPER_LOCAL_MODEL_DIR` | `~/.cache/whisper` | Directory where local model weights are cached; set to a shared volume in Kubernetes deployments |
| `FFMPEG_BIN` | `ffmpeg` | Absolute path to ffmpeg binary; overrides PATH lookup |
| `CHIMERA_TRANSCRIBE_MAX_FILE_MB` | `25` | Maximum audio file size in MB; files exceeding this limit are rejected before any API call (Whisper API hard limit is 25 MB) |

---

## Implementation Notes

- File extension validation uses an explicit allowlist (`mp3`, `mp4`, `m4a`, `wav`,
  `ogg`, `flac`, `webm`); MIME type sniffing via `Files.probeContentType()` is performed
  as a secondary check — the extension check always runs first.
- When `WHISPER_BACKEND=api`, the file is submitted as a multipart form upload to
  `POST /v1/audio/transcriptions` with `response_format=verbose_json` to obtain
  segment-level confidence data for the `confidence` field calculation.
- When `WHISPER_BACKEND=local`, the skill invokes the `whisper` CLI as a subprocess via
  `ProcessBuilder` with an explicit argument list — no shell interpolation of user-supplied
  values (OWASP command injection prevention).
- Confidence is computed as the arithmetic mean of Whisper's per-segment `avg_logprob`
  values, mapped from log-probability space to `[0.0, 1.0]` via
  `Math.exp(avgLogprob)` clamped to the unit interval.
- Files larger than `CHIMERA_TRANSCRIBE_MAX_FILE_MB` are rejected before the API call;
  for large files, callers should pre-split using `ffmpeg -segment_time` and submit
  chunks individually.
- All subprocess invocations use `ProcessBuilder` with explicit argument lists — no
  shell interpolation of user-supplied file paths (OWASP command injection prevention).
