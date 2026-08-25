package org.sqahub.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqahub.backend.dto.DeployDecisionResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test untuk DeployDecisionService: memastikan keputusan LAYAK_DEPLOY / TIDAK_LAYAK_DEPLOY
 * konsisten dengan pass rate dan ambang batas yang dikonfigurasi.
 */
class DeployDecisionServiceTest {

    private final DeployDecisionService service = new DeployDecisionService();

    private void setThreshold(double threshold) {
        ReflectionTestUtils.setField(service, "passRateThreshold", threshold);
    }

    @Test
    @DisplayName("Pass rate tepat di ambang batas -> LAYAK_DEPLOY")
    void evaluate_passRateEqualsThreshold_isDeployable() {
        setThreshold(95.0);

        DeployDecisionResponse result = service.evaluate(1L, "Suite A", 95, 5, 0, 0);

        assertEquals(95.0, result.getPassRatePercent());
        assertTrue(result.isDeployRecommended());
        assertEquals("LAYAK_DEPLOY", result.getDecision());
    }

    @Test
    @DisplayName("Pass rate di bawah ambang batas -> TIDAK_LAYAK_DEPLOY")
    void evaluate_passRateBelowThreshold_isNotDeployable() {
        setThreshold(95.0);

        DeployDecisionResponse result = service.evaluate(1L, "Suite B", 90, 10, 0, 0);

        assertEquals(90.0, result.getPassRatePercent());
        assertFalse(result.isDeployRecommended());
        assertEquals("TIDAK_LAYAK_DEPLOY", result.getDecision());
    }

    @Test
    @DisplayName("Belum ada test case yang dieksekusi -> TIDAK_LAYAK_DEPLOY, pass rate 0")
    void evaluate_noTestsExecuted_isNotDeployable() {
        setThreshold(95.0);

        DeployDecisionResponse result = service.evaluate(1L, "Suite C", 0, 0, 0, 0);

        assertEquals(0, result.getTotalTests());
        assertEquals(0.0, result.getPassRatePercent());
        assertFalse(result.isDeployRecommended());
        assertEquals("TIDAK_LAYAK_DEPLOY", result.getDecision());
    }

    @Test
    @DisplayName("Error dan Skipped ikut dihitung sebagai bukan-passed dalam total")
    void evaluate_errorAndSkippedCountTowardTotal() {
        setThreshold(50.0);

        DeployDecisionResponse result = service.evaluate(1L, "Suite D", 5, 1, 2, 2);

        assertEquals(10, result.getTotalTests());
        assertEquals(50.0, result.getPassRatePercent());
        assertTrue(result.isDeployRecommended());
    }
}
