package com.hmilyld.weeklyreport.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayRulesTest {
    @Test
    fun `missing server row can be created`() {
        assertEquals(ReplayRules.Decision.Send, ReplayRules.dailyDecision(null, null, "1. a"))
    }

    @Test
    fun `identical content is already synced`() {
        assertEquals(
            ReplayRules.Decision.AlreadySynced,
            ReplayRules.dailyDecision("1. a", "1. old", "1. a"),
        )
    }

    @Test
    fun `unchanged remote baseline can be overwritten`() {
        assertEquals(
            ReplayRules.Decision.Send,
            ReplayRules.dailyDecision("1. server", "1. server", "1. local"),
        )
    }

    @Test
    fun `remote change since baseline is a conflict`() {
        assertEquals(
            ReplayRules.Decision.Conflict,
            ReplayRules.dailyDecision("1. other device", "1. server", "1. local"),
        )
    }

    @Test
    fun `first local draft against an existing remote row is a conflict`() {
        assertEquals(
            ReplayRules.Decision.Conflict,
            ReplayRules.dailyDecision("1. remote", null, "1. local"),
        )
    }

    @Test
    fun `blank remote row is treated as absent for a first draft`() {
        assertEquals(
            ReplayRules.Decision.Send,
            ReplayRules.dailyDecision("", null, "1. local"),
        )
    }
}
