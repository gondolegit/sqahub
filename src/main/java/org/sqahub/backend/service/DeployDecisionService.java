package org.sqahub.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sqahub.backend.dto.DeployDecisionResponse;

/**
 * Logika murni untuk menentukan kelayakan deploy dari hasil sebuah Test Suite Run,
 * berdasarkan pass rate dibanding ambang batas yang bisa dikonfigurasi
 * (app.deploy-decision.pass-rate-threshold, default 95%).
 */
@Service
public class DeployDecisionService {

    @Value("${app.deploy-decision.pass-rate-threshold:95.0}")
    private double passRateThreshold;

    public DeployDecisionResponse evaluate(Long testSuiteId, String testSuiteName,
                                            int passed, int failed, int error, int skipped) {
        int total = passed + failed + error + skipped;
        double passRate = total == 0 ? 0.0 : (passed * 100.0) / total;
        // Dibulatkan 2 desimal agar rapi ditampilkan
        double roundedPassRate = Math.round(passRate * 100.0) / 100.0;

        boolean recommended = total > 0 && passRate >= passRateThreshold;
        String decision = recommended ? "LAYAK_DEPLOY" : "TIDAK_LAYAK_DEPLOY";

        String reason = total == 0
                ? "Belum ada hasil eksekusi Test Case pada Test Suite ini, kelayakan deploy tidak dapat dievaluasi."
                : String.format(
                    "Pass rate %.2f%% (%d dari %d test case lulus) %s ambang batas %.2f%%.",
                    roundedPassRate, passed, total,
                    recommended ? ">=" : "<", passRateThreshold);

        return DeployDecisionResponse.builder()
                .testSuiteId(testSuiteId)
                .testSuiteName(testSuiteName)
                .totalPassed(passed)
                .totalFailed(failed)
                .totalError(error)
                .totalSkipped(skipped)
                .totalTests(total)
                .passRatePercent(roundedPassRate)
                .thresholdPercent(passRateThreshold)
                .deployRecommended(recommended)
                .decision(decision)
                .reason(reason)
                .build();
    }
}
