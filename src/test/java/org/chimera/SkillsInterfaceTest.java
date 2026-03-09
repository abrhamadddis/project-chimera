// TDD: These tests are intentionally failing - they define the implementation target

package org.chimera;

import org.chimera.commerce.BudgetExceededException;
import org.chimera.skills.DownloadYoutubeSkill;
import org.chimera.skills.TranscribeAudioSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SkillsInterfaceTest — defines the contract for OpenClaw-compatible skill invocations.
 *
 * Spec references:
 *   - specs/functional.md  US-5.2, US-5.3 (Agentic Commerce — budget enforcement)
 *   - specs/openclaw_integration.md §2 (Integration Paths)
 *   - task_breakdown.md    M5-2, M5-3 (CFO Sub-Judge, Spend Enforcement)
 *   - specs/_meta.md §6 Rule 4 (never execute financial tx without CFO approval)
 *
 * Skills map to OpenClaw's modular capability packages (download_video, transcribe_audio).
 * Each skill must be independently testable and MCP-routable (Constitution Principle IV).
 *
 * FAILING because DownloadYoutubeSkill, TranscribeAudioSkill, and BudgetExceededException
 * do not exist yet.
 *
 * Implement:
 *   src/main/java/org/chimera/skills/DownloadYoutubeSkill.java
 *   src/main/java/org/chimera/skills/TranscribeAudioSkill.java
 *   src/main/java/org/chimera/commerce/BudgetExceededException.java
 */
@DisplayName("Skills Interface — OpenClaw-compatible skill contracts")
class SkillsInterfaceTest {

    // -------------------------------------------------------------------------
    // DTOs — defined as Java 21 Records per Constitution Principle I.
    // These are inline to keep the test self-contained; they will migrate to
    // src/main/java/org/chimera/skills/ once implementation begins.
    // -------------------------------------------------------------------------

    /** Wraps the result of a completed download. */
    record DownloadResult(Path filePath, String mimeType, long fileSizeBytes) {
        public DownloadResult {
            if (filePath == null)    throw new IllegalArgumentException("filePath must not be null");
            if (mimeType == null || mimeType.isBlank())
                throw new IllegalArgumentException("mimeType must not be blank");
            if (fileSizeBytes < 0)   throw new IllegalArgumentException("fileSizeBytes must be >= 0");
        }
    }

    /** Wraps the result of an audio transcription. */
    record TranscriptionResult(String transcript, String language, double confidenceScore) {
        public TranscriptionResult {
            if (transcript == null)   throw new IllegalArgumentException("transcript must not be null");
            if (language == null || language.isBlank())
                throw new IllegalArgumentException("language must not be blank");
            if (confidenceScore < 0.0 || confidenceScore > 1.0)
                throw new IllegalArgumentException("confidenceScore must be in [0.0, 1.0]");
        }
    }

    private DownloadYoutubeSkill downloadSkill;
    private TranscribeAudioSkill transcribeSkill;

    @BeforeEach
    void setUp() {
        // Fails at COMPILE TIME — neither skill class exists yet.
        downloadSkill  = new DownloadYoutubeSkill();
        transcribeSkill = new TranscribeAudioSkill();
    }

    // =========================================================================
    // 1. skill_download_youtube — valid URL returns a file path
    // =========================================================================

    @Test
    @DisplayName("download_youtube accepts a valid YouTube URL and returns a non-null file path")
    void downloadYoutube_validUrl_returnsFilePath() {
        // Fails at COMPILE TIME — DownloadYoutubeSkill does not exist yet.
        // Spec: openclaw_integration.md — skills map to MCP Tools; every tool call
        // must include caller_task_id for audit linkage (technical.md §1.3).
        String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        DownloadResult result = downloadSkill.execute(validUrl);

        assertThat(result).isNotNull();
        assertThat(result.filePath()).isNotNull();
        assertThat(result.filePath().toString()).isNotBlank();
        assertThat(result.mimeType()).isNotBlank();
        assertThat(result.fileSizeBytes()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("download_youtube returns a Path that points to a file with a recognised video extension")
    void downloadYoutube_validUrl_returnsMp4OrWebm() {
        String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        DownloadResult result = downloadSkill.execute(validUrl);

        String fileName = result.filePath().getFileName().toString().toLowerCase();
        assertThat(fileName)
                .as("downloaded file must be a recognised video format")
                .satisfiesAnyOf(
                        name -> assertThat(name).endsWith(".mp4"),
                        name -> assertThat(name).endsWith(".webm"),
                        name -> assertThat(name).endsWith(".mkv")
                );
    }

    @Test
    @DisplayName("download_youtube result mimeType is a video/* content type")
    void downloadYoutube_validUrl_returnsVideoMimeType() {
        String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        DownloadResult result = downloadSkill.execute(validUrl);

        assertThat(result.mimeType())
                .startsWith("video/");
    }

    // =========================================================================
    // 2. skill_download_youtube — invalid URL throws IllegalArgumentException
    // =========================================================================

    @ParameterizedTest(name = "invalid URL [{0}] must throw IllegalArgumentException")
    @ValueSource(strings = {
            "",                                         // empty string
            "   ",                                      // blank
            "not-a-url",                                // no scheme
            "ftp://www.youtube.com/watch?v=abc",        // wrong scheme
            "https://www.vimeo.com/123456",             // non-YouTube domain
            "https://www.youtube.com/",                 // missing video ID
            "javascript:alert(1)",                      // injection attempt
            "https://evil.com?redirect=https://youtube.com" // open redirect
    })
    @DisplayName("download_youtube throws IllegalArgumentException for invalid or non-YouTube URLs")
    void downloadYoutube_invalidUrl_throwsIllegalArgumentException(String invalidUrl) {
        // Spec: specs/_meta.md §6 Rule 5 — all external content (including URLs provided
        // by peer agents) must be validated before execution. Invalid URLs are rejected
        // before any network call is made.
        assertThatThrownBy(() -> downloadSkill.execute(invalidUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");
    }

    @Test
    @DisplayName("download_youtube throws IllegalArgumentException for null URL")
    void downloadYoutube_nullUrl_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> downloadSkill.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // =========================================================================
    // 3. skill_transcribe_audio — accepts audio file and returns a String
    // =========================================================================

    @Test
    @DisplayName("transcribe_audio accepts a valid audio file path and returns a non-blank transcript")
    void transcribeAudio_validFile_returnsTranscript() {
        // Fails at COMPILE TIME — TranscribeAudioSkill does not exist yet.
        // The skill maps to the OpenClaw 'transcribe_audio' capability package.
        // Spec: openclaw_integration.md §2 — skills are independently testable capability packages.
        Path audioFile = Path.of("src/test/resources/fixtures/sample-audio.mp3");

        TranscriptionResult result = transcribeSkill.execute(audioFile);

        assertThat(result).isNotNull();
        assertThat(result.transcript())
                .isNotNull()
                .isNotBlank();
    }

    @Test
    @DisplayName("transcribe_audio result has a detected language code")
    void transcribeAudio_validFile_returnsLanguage() {
        Path audioFile = Path.of("src/test/resources/fixtures/sample-audio.mp3");

        TranscriptionResult result = transcribeSkill.execute(audioFile);

        // Language must be a non-blank ISO 639-1 or BCP-47 code (e.g. "en", "en-US").
        assertThat(result.language())
                .isNotBlank()
                .hasSizeLessThanOrEqualTo(10); // reasonable bound for language codes
    }

    @Test
    @DisplayName("transcribe_audio result confidenceScore is between 0.0 and 1.0")
    void transcribeAudio_validFile_confidenceScoreInRange() {
        Path audioFile = Path.of("src/test/resources/fixtures/sample-audio.mp3");

        TranscriptionResult result = transcribeSkill.execute(audioFile);

        assertThat(result.confidenceScore())
                .isGreaterThanOrEqualTo(0.0)
                .isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("transcribe_audio throws IllegalArgumentException for null file path")
    void transcribeAudio_nullPath_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> transcribeSkill.execute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transcribe_audio throws IllegalArgumentException for non-audio file extension")
    void transcribeAudio_nonAudioFile_throwsIllegalArgumentException() {
        // Skill must validate extension before attempting expensive transcription call.
        Path nonAudioFile = Path.of("src/test/resources/fixtures/sample-image.jpg");

        assertThatThrownBy(() -> transcribeSkill.execute(nonAudioFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audio");
    }

    // =========================================================================
    // 4. BudgetExceededException is thrown when daily limit is exceeded
    // =========================================================================

    @Test
    @DisplayName("download_youtube throws BudgetExceededException when agent daily spend limit is reached")
    void downloadYoutube_budgetExceeded_throwsBudgetExceededException() {
        // Fails at COMPILE TIME — BudgetExceededException does not exist yet.
        //
        // Spec: specs/functional.md US-5.3 — atomic spend enforcement; transaction rejected
        // and never silently dropped when daily ceiling is exceeded.
        // Spec: specs/_meta.md §6 Rule 4 — no financial execution without CFO Sub-Judge approval.
        //
        // DownloadYoutubeSkill incurs compute cost charged against the agent's daily budget.
        // When the CFO Sub-Judge's Redis INCRBY counter exceeds max_daily_spend,
        // the skill must surface BudgetExceededException — not a generic RuntimeException.
        String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        // Use a skill instance configured with an exhausted budget (0 remaining).
        DownloadYoutubeSkill budgetExhaustedSkill = DownloadYoutubeSkill.withExhaustedBudget();

        assertThatThrownBy(() -> budgetExhaustedSkill.execute(validUrl))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    @DisplayName("BudgetExceededException carries agentId, currentSpend, and dailyCeiling")
    void budgetExceededException_hasStructuredFields() {
        // Spec: functional.md US-5.3 — rejection message must include current spend total,
        // proposed transaction amount, and ceiling value (technical.md §3.1 hitl_queue.spend_context).
        String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        DownloadYoutubeSkill budgetExhaustedSkill = DownloadYoutubeSkill.withExhaustedBudget();

        assertThatThrownBy(() -> budgetExhaustedSkill.execute(validUrl))
                .isInstanceOfSatisfying(BudgetExceededException.class, ex -> {
                    assertThat(ex.agentId()).isNotBlank();
                    assertThat(ex.currentSpendMicroUnits()).isGreaterThanOrEqualTo(0L);
                    assertThat(ex.dailyCeilingMicroUnits()).isGreaterThan(0L);
                    assertThat(ex.currentSpendMicroUnits())
                            .as("current spend must be at or above ceiling to trigger exception")
                            .isGreaterThanOrEqualTo(ex.dailyCeilingMicroUnits());
                });
    }

    @Test
    @DisplayName("transcribe_audio throws BudgetExceededException when agent daily spend limit is reached")
    void transcribeAudio_budgetExceeded_throwsBudgetExceededException() {
        // Both compute-cost skills share the same CFO spend counter per agent per day.
        // Exhausting the budget via downloads also blocks transcriptions.
        Path audioFile = Path.of("src/test/resources/fixtures/sample-audio.mp3");
        TranscribeAudioSkill budgetExhaustedSkill = TranscribeAudioSkill.withExhaustedBudget();

        assertThatThrownBy(() -> budgetExhaustedSkill.execute(audioFile))
                .isInstanceOf(BudgetExceededException.class);
    }

    @Test
    @DisplayName("BudgetExceededException is NOT thrown when spend is below daily ceiling")
    void skills_withinBudget_doNotThrowBudgetException() {
        // Verify the happy path — no budget exception when there is remaining capacity.
        // Uses the default skill instances (configured with ample budget in setUp()).
        String validUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

        // Must not throw BudgetExceededException (may still fail on other grounds
        // in a unit-test environment without real network, but budget is not the cause).
        assertThatThrownBy(() -> downloadSkill.execute(validUrl))
                .isNotInstanceOf(BudgetExceededException.class);
    }
}
