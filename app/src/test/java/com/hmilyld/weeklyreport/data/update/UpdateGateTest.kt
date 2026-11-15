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
}
