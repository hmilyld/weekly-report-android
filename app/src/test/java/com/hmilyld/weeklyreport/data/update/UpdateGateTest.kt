package com.hmilyld.weeklyreport.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateGateTest {
    private val day = UpdateGate.CHECK_INTERVAL_MS
    private val now = 1_700_000_000_000L

    @Test
    fun `the first automatic check runs`() {
        assertTrue(
            UpdateGate.shouldCheck(
                force = false,
                autoEnabled = true,
                lastCheckAt = 0L,
                now = now,
            ),
        )
    }

    @Test
    fun `a check within a day is throttled`() {
        assertFalse(
            UpdateGate.shouldCheck(
                force = false,
                autoEnabled = true,
                lastCheckAt = now - day + 1,
                now = now,
            ),
        )
        assertTrue(
            UpdateGate.shouldCheck(
                force = false,
                autoEnabled = true,
                lastCheckAt = now - day,
                now = now,
            ),
        )
    }

    @Test
    fun `the switch stops automatic checks but not manual ones`() {
        assertFalse(
            UpdateGate.shouldCheck(
                force = false,
                autoEnabled = false,
                lastCheckAt = 0L,
                now = now,
            ),
        )
        assertTrue(
            UpdateGate.shouldCheck(
                force = true,
                autoEnabled = false,
                lastCheckAt = now,
                now = now,
            ),
        )
    }

    @Test
    fun `a clock that moved backwards does not silence the check`() {
        assertTrue(
            UpdateGate.shouldCheck(
                force = false,
                autoEnabled = true,
                lastCheckAt = now + day,
                now = now,
            ),
        )
    }

    @Test
    fun `only a strictly newer release is offered`() {
        assertTrue(UpdateGate.shouldOffer(false, "1.0.1", "1.0.0", null))
        assertFalse(UpdateGate.shouldOffer(false, "1.0.0", "1.0.0", null))
        assertFalse(UpdateGate.shouldOffer(false, "0.9.0", "1.0.0", null))
        assertFalse(UpdateGate.shouldOffer(false, null, "1.0.0", null))
    }

    @Test
    fun `the postponed release stays quiet until a newer one appears`() {
        assertFalse(UpdateGate.shouldOffer(false, "1.1.0", "1.0.0", "1.1.0"))
        assertFalse(UpdateGate.shouldOffer(false, "1.1.0", "1.0.0", "v1.1.0"))
        assertTrue(UpdateGate.shouldOffer(false, "1.2.0", "1.0.0", "1.1.0"))
    }

    @Test
    fun `a manual check ignores the postponed release`() {
        assertTrue(UpdateGate.shouldOffer(true, "1.1.0", "1.0.0", "1.1.0"))
        assertFalse(UpdateGate.shouldOffer(true, "1.0.0", "1.0.0", "1.1.0"))
    }

    @Test
    fun `a malformed skip record does not hide updates`() {
        assertTrue(UpdateGate.shouldOffer(false, "1.1.0", "1.0.0", "garbage"))
        assertTrue(UpdateGate.shouldOffer(false, "1.1.0", "1.0.0", ""))
    }

    @Test
    fun `a higher build number alone counts as an update`() {
        // A hotfix that re-releases 1.1.0 with a new versionCode.
        assertTrue(
            UpdateGate.shouldOffer(
                force = false,
                published = "1.1.0",
                installed = "1.1.0",
                skipped = null,
                publishedCode = 5,
                installedCode = 4,
            ),
        )
    }

    @Test
    fun `an equal or lower build number is not an update`() {
        assertFalse(
            UpdateGate.shouldOffer(
                false,
                "1.1.0",
                "1.1.0",
                null,
                publishedCode = 4,
                installedCode = 4,
            ),
        )
        assertFalse(
            UpdateGate.shouldOffer(
                false,
                "1.1.0",
                "1.1.0",
                null,
                publishedCode = 3,
                installedCode = 4,
            ),
        )
    }

    @Test
    fun `the build number never overrides a lower version name`() {
        // 0.9.0 with a huge code is still older than 1.0.0.
        assertFalse(
            UpdateGate.shouldOffer(
                false,
                "0.9.0",
                "1.0.0",
                null,
                publishedCode = 99,
                installedCode = 1,
            ),
        )
    }

    @Test
    fun `a missing build number falls back to the version name alone`() {
        // The API fallback cannot report a versionCode, so equal names must not
        // be treated as an update just because the code is unknown.
        assertFalse(
            UpdateGate.shouldOffer(
                false,
                "1.0.0",
                "1.0.0",
                null,
                publishedCode = 0,
                installedCode = 3,
            ),
        )
        assertTrue(
            UpdateGate.shouldOffer(
                false,
                "1.0.1",
                "1.0.0",
                null,
                publishedCode = 0,
                installedCode = 3,
            ),
        )
    }
}
