// TDD: These tests are intentionally failing - they define the implementation target

package org.chimera;

import org.chimera.domain.TrendAlert;
import org.chimera.perception.TrendFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * TrendFetcherTest — defines the contract for trend detection.
 *
 * Spec references:
 *   - specs/functional.md  US-2.3 (Trend Detection)
 *   - specs/technical.md   §2.4   (TrendAlert record)
 *   - task_breakdown.md    M2-6, M2-7
 *
 * FAILING because TrendAlert record and TrendFetcher service do not exist yet.
 * Implement TrendAlert in src/main/java/org/chimera/domain/TrendAlert.java
 * Implement TrendFetcher in src/main/java/org/chimera/perception/TrendFetcher.java
 */
@DisplayName("TrendFetcher — trend detection contract")
class TrendFetcherTest {

    // -------------------------------------------------------------------------
    // Fixture: a valid TrendAlert built inline to test the record shape.
    // Fails at COMPILE TIME until TrendAlert record is created with these fields.
    // -------------------------------------------------------------------------

    private static final String SAMPLE_TREND_ID    = "trend-milan-fw-2026-001";
    private static final String SAMPLE_TOPIC       = "Milan Fashion Week AW2026 — statement sleeves trend";
    private static final double SAMPLE_SCORE       = 0.88;
    private static final Instant SAMPLE_DETECTED   = Instant.parse("2026-03-09T08:00:00Z");
    private static final Instant SAMPLE_EXPIRES    = Instant.parse("2026-03-09T12:00:00Z");

    private TrendFetcher fetcher;

    @BeforeEach
    void setUp() {
        // Fails at COMPILE TIME — TrendFetcher does not exist yet.
        fetcher = new TrendFetcher();
    }

    // =========================================================================
    // 1. TrendAlert record has correct fields
    // =========================================================================

    @Test
    @DisplayName("TrendAlert record exposes id, topic, score, and timestamp fields")
    void trendAlert_hasExpectedFields() {
        // Fails at COMPILE TIME — TrendAlert record does not exist yet.
        // Spec: technical.md §2.4 — fields trendId, topic, relevanceScore, detectedAt, expiresAt
        TrendAlert alert = new TrendAlert(
                SAMPLE_TREND_ID,
                SAMPLE_TOPIC,
                SAMPLE_SCORE,
                List.of("news://trends/fashion"),
                "fashion",
                SAMPLE_DETECTED,
                SAMPLE_EXPIRES
        );

        assertThat(alert.trendId()).isEqualTo(SAMPLE_TREND_ID);
        assertThat(alert.topic()).isEqualTo(SAMPLE_TOPIC);
        assertThat(alert.relevanceScore()).isEqualTo(SAMPLE_SCORE);
        assertThat(alert.detectedAt()).isEqualTo(SAMPLE_DETECTED);
        assertThat(alert.expiresAt()).isEqualTo(SAMPLE_EXPIRES);
        assertThat(alert.niche()).isEqualTo("fashion");
        assertThat(alert.sourceUrls()).containsExactly("news://trends/fashion");
    }

    @Test
    @DisplayName("TrendAlert record is immutable — accessor returns value, no setter exists")
    void trendAlert_isImmutable() {
        TrendAlert alert = new TrendAlert(
                SAMPLE_TREND_ID, SAMPLE_TOPIC, SAMPLE_SCORE,
                List.of("news://trends/fashion"), "fashion",
                SAMPLE_DETECTED, SAMPLE_EXPIRES
        );

        // Java Records have no setters by contract — this assertion documents the expectation.
        // Verify the record has no 'setRelevanceScore' or similar mutation method.
        assertThat(alert.getClass().getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("setTrendId", "setTopic", "setRelevanceScore",
                                "setDetectedAt", "setExpiresAt", "setNiche");
    }

    // =========================================================================
    // 2. TrendAlert score is always between 0.0 and 1.0
    // =========================================================================

    @Test
    @DisplayName("TrendAlert compact constructor rejects relevanceScore below 0.75 threshold")
    void trendAlert_rejectsScoreBelowThreshold() {
        // Spec: technical.md §2.4 — relevanceScore must be >= 0.75 (FR 2.1 threshold)
        // Fails at COMPILE TIME until TrendAlert exists; then fails at RUNTIME until
        // compact constructor validates the 0.75 floor.
        assertThatThrownBy(() -> new TrendAlert(
                SAMPLE_TREND_ID, SAMPLE_TOPIC,
                0.74,   // below 0.75 threshold
                List.of("news://trends/fashion"), "fashion",
                SAMPLE_DETECTED, SAMPLE_EXPIRES
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("0.75");
    }

    @Test
    @DisplayName("TrendAlert compact constructor rejects relevanceScore above 1.0")
    void trendAlert_rejectsScoreAboveOne() {
        assertThatThrownBy(() -> new TrendAlert(
                SAMPLE_TREND_ID, SAMPLE_TOPIC,
                1.01,   // above 1.0
                List.of("news://trends/fashion"), "fashion",
                SAMPLE_DETECTED, SAMPLE_EXPIRES
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TrendAlert compact constructor rejects expiresAt not after detectedAt")
    void trendAlert_rejectsExpiresAtBeforeDetectedAt() {
        // Spec: technical.md §2.4 — expiresAt must be after detectedAt
        assertThatThrownBy(() -> new TrendAlert(
                SAMPLE_TREND_ID, SAMPLE_TOPIC, SAMPLE_SCORE,
                List.of("news://trends/fashion"), "fashion",
                SAMPLE_DETECTED,
                SAMPLE_DETECTED   // same instant — not strictly after
        ))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "valid score {0} is accepted by TrendAlert")
    @ValueSource(doubles = {0.75, 0.80, 0.90, 0.95, 1.0})
    @DisplayName("TrendAlert accepts scores in the valid range [0.75, 1.0]")
    void trendAlert_acceptsValidScoreRange(double score) {
        // Must not throw for any score in the valid range.
        TrendAlert alert = new TrendAlert(
                SAMPLE_TREND_ID, SAMPLE_TOPIC, score,
                List.of("news://trends/fashion"), "fashion",
                SAMPLE_DETECTED, SAMPLE_EXPIRES
        );
        assertThat(alert.relevanceScore()).isCloseTo(score, within(0.0001));
    }

    // =========================================================================
    // 3. fetchTrends() returns a non-empty list
    // =========================================================================

    @Test
    @DisplayName("fetchTrends() returns a non-empty list when trends exist above threshold")
    void fetchTrends_returnsNonEmptyList() {
        // Fails at COMPILE TIME — TrendFetcher does not exist yet.
        // Spec: functional.md US-2.3 — Planner calls trend detection; result injected into GlobalState.
        List<TrendAlert> trends = fetcher.fetchTrends("fashion");

        assertThat(trends)
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @DisplayName("fetchTrends() returns only TrendAlert instances with non-null required fields")
    void fetchTrends_returnsWellFormedAlerts() {
        List<TrendAlert> trends = fetcher.fetchTrends("fashion");

        assertThat(trends).allSatisfy(alert -> {
            assertThat(alert.trendId()).isNotBlank();
            assertThat(alert.topic()).isNotBlank();
            assertThat(alert.niche()).isNotBlank();
            assertThat(alert.sourceUrls()).isNotEmpty();
            assertThat(alert.detectedAt()).isNotNull();
            assertThat(alert.expiresAt()).isAfter(alert.detectedAt());
        });
    }

    @Test
    @DisplayName("fetchTrends() for unknown niche returns empty list — no exception")
    void fetchTrends_unknownNicheReturnsEmptyList() {
        // Spec: functional.md US-2.3 — graceful empty result, not an exception
        List<TrendAlert> trends = fetcher.fetchTrends("unknownNicheXYZ99");

        assertThat(trends)
                .isNotNull()
                .isEmpty();
    }

    // =========================================================================
    // 4. Trends above relevance threshold 0.75 are flagged
    // =========================================================================

    @Test
    @DisplayName("fetchTrends() returns only trends with relevanceScore >= 0.75")
    void fetchTrends_allResultsAboveThreshold() {
        // Spec: functional.md US-2.2 — only payloads >= 0.75 trigger a Planner task.
        // TrendFetcher must not return sub-threshold alerts — the filter is internal.
        List<TrendAlert> trends = fetcher.fetchTrends("fashion");

        assertThat(trends).allSatisfy(alert ->
                assertThat(alert.relevanceScore())
                        .as("relevanceScore for trend '%s'", alert.trendId())
                        .isGreaterThanOrEqualTo(0.75)
        );
    }

    @Test
    @DisplayName("fetchTrends() result does not contain duplicate trendIds")
    void fetchTrends_noDuplicateTrendIds() {
        // Spec: functional.md US-2.3 — each unique TrendAlert injected into GlobalState exactly once.
        List<TrendAlert> trends = fetcher.fetchTrends("fashion");

        assertThat(trends)
                .extracting(TrendAlert::trendId)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("TrendAlert expiresAt is within 4 hours of detectedAt")
    void fetchTrends_alertWindowIsMaxFourHours() {
        // Spec: functional.md US-2.3 — 4-hour rolling window; alerts expire after window.
        List<TrendAlert> trends = fetcher.fetchTrends("fashion");

        assertThat(trends).allSatisfy(alert -> {
            long windowSeconds = alert.expiresAt().getEpochSecond() - alert.detectedAt().getEpochSecond();
            assertThat(windowSeconds)
                    .as("trend window for '%s' must be <= 14400s (4h)", alert.trendId())
                    .isLessThanOrEqualTo(14_400L);
        });
    }
}
