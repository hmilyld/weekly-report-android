package com.hmilyld.weeklyreport.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DatesTest {
    @Test
    fun `monday of a midweek day is that week's monday`() {
        val thursday = LocalDate.of(2026, 11, 12)
        assertEquals(LocalDate.of(2026, 11, 9), Dates.mondayOf(thursday))
    }

    @Test
    fun `sunday belongs to the week that started six days earlier`() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(LocalDate.of(2026, 11, 9), Dates.mondayOf(sunday))
    }

    @Test
    fun `monday maps to itself`() {
        val monday = LocalDate.of(2026, 11, 9)
        assertEquals(monday, Dates.mondayOf(monday))
    }

    @Test
    fun `week always contains seven ordered days`() {
        val days = Dates.weekDates(LocalDate.of(2026, 11, 9))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 11, 9), days.first())
        assertEquals(LocalDate.of(2026, 11, 15), days.last())
    }

    @Test
    fun `deadline states follow overdue and three day window`() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals(DeadlineState.NONE, Dates.deadlineState(null, today))
        assertEquals(DeadlineState.OVERDUE, Dates.deadlineState(today.minusDays(1), today))
        assertEquals(DeadlineState.URGENT, Dates.deadlineState(today, today))
        assertEquals(DeadlineState.URGENT, Dates.deadlineState(today.plusDays(2), today))
        assertEquals(DeadlineState.LATER, Dates.deadlineState(today.plusDays(3), today))
    }

    @Test
    fun `zone-less server timestamps are treated as UTC`() {
        val instant = Dates.parseServerInstant("2026-11-14T09:15:02")
        assertEquals("2026-11-14T09:15:02Z", instant.toString())
    }

    @Test
    fun `iso round trip`() {
        val date = LocalDate.of(2026, 1, 2)
        assertEquals("2026-01-02", Dates.iso(date))
        assertEquals(date, Dates.parseIso("2026-01-02"))
        assertNull(Dates.parseIso("not-a-date"))
    }

    @Test
    fun `labels are chinese and monday first`() {
        val monday = LocalDate.of(2026, 11, 9)
        assertTrue(Dates.weekRangeLabel(monday).contains("11月9日"))
        assertEquals("周一", Dates.weekdayShort(monday))
        assertEquals("周日", Dates.weekdayShort(monday.plusDays(6)))
        assertEquals("2026年11月", Dates.monthLabel(2026, 11))
    }

    @Test
    fun `server time comparison only flags a real advance`() {
        assertEquals(false, Dates.serverTimeChanged("2026-11-14T09:00:00", "2026-11-14T09:00:00"))
        assertEquals(true, Dates.serverTimeChanged("2026-11-14T09:00:00", "2026-11-14T09:00:01"))
        assertEquals(false, Dates.serverTimeChanged("2026-11-14T09:00:00", null))
    }
}
