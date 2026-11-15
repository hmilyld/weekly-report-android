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
     * True when [published] is a version the user should be told about: newer
     * than what is installed, and not the one they asked to skip. A manual check
     * ignores the skip so the user can find it again.
     *
     * "Newer" is the semantic version, with the build number as a tie-breaker:
     * a hotfix that only bumps `versionCode` (same `versionName`) is still an
     * update, while a build number is never used to override a lower version
     * name. A published build with no code at all (the API fallback cannot see
     * one) falls back to comparing version names alone.
     */
    fun shouldOffer(
        force: Boolean,
        published: String?,
        installed: String?,
        skipped: String?,
        publishedCode: Int = 0,
        installedCode: Int = 0,
    ): Boolean {
        if (!isNewer(published, installed, publishedCode, installedCode)) return false
        if (force) return true
        val skippedVersion = SemVer.parse(skipped)?.toString() ?: return true
        return SemVer.parse(published)?.toString() != skippedVersion
    }

    private fun isNewer(
        published: String?,
        installed: String?,
        publishedCode: Int,
        installedCode: Int,
    ): Boolean {
        val next = SemVer.parse(published) ?: return false
        val current = SemVer.parse(installed) ?: return false
        if (next != current) return next > current
        // Equal version names: only a strictly higher build number is an update.
        return publishedCode > 0 && installedCode > 0 && publishedCode > installedCode
    }
}
