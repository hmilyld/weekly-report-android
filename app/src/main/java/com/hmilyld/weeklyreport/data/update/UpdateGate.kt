package com.hmilyld.weeklyreport.data.update

import com.hmilyld.weeklyreport.util.SemVer

/**
 * The decisions [UpdateChecker] makes, with no preferences or clock of their
 * own, so the throttling rules can be unit tested.
 */
object UpdateGate {
    /** How long an automatic check stays quiet after one attempt. */
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    /**
     * True when an automatic check should reach the network. A manual check
     * (`force`) always passes; otherwise the switch must be on and the last
     * attempt must be older than [CHECK_INTERVAL_MS].
     */
    fun shouldCheck(
        force: Boolean,
        autoEnabled: Boolean,
        lastCheckAt: Long,
        now: Long,
    ): Boolean {
        if (force) return true
        if (!autoEnabled) return false
        if (lastCheckAt <= 0L) return true
        // A clock that moved backwards must not silence the check forever.
        val elapsed = now - lastCheckAt
        if (elapsed < 0L) return true
        return elapsed >= CHECK_INTERVAL_MS
    }

    /**
     * True when [published] is a version the user should be told about:
     * strictly newer than [installed], and not the one they asked to skip.
     * A manual check ignores the skip so the user can find it again.
     */
    fun shouldOffer(
        force: Boolean,
        published: String?,
        installed: String?,
        skipped: String?,
    ): Boolean {
        if (!SemVer.isNewer(published, installed)) return false
        if (force) return true
        val skippedVersion = SemVer.parse(skipped)?.toString() ?: return true
        return SemVer.parse(published)?.toString() != skippedVersion
    }
}
