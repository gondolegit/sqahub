package org.sqahub.backend.model;

/**
 * Status siklus hidup sebuah Bug, urut dari dilaporkan sampai deployed. Transisi yang diizinkan
 * antar status ditegakkan oleh BugService (lihat ALLOWED_TRANSITIONS di sana) — enum ini hanya
 * mendaftar nilai yang sah, bukan aturan urutannya.
 */
public enum BugStatus {
    NEW,
    IN_ANALYSIS,
    READY_FOR_DEVELOPMENT,
    IN_DEVELOPMENT,
    READY_FOR_TESTING,
    IN_TESTING,
    READY_FOR_UAT,
    IN_UAT,
    READY_FOR_DEPLOYMENT,
    DEPLOYED
}
