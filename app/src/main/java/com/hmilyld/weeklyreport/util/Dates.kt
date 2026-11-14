package com.hmilyld.weeklyreport.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar rules shared by the daily list, reports and notifications. */
object Dates {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun today(): LocalDate = LocalDate.now()

    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun iso(date: LocalDate): String = date.format(ISO)

    fun parseIso(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value, ISO) }.getOrNull()
    }

    fun weekDates(monday: LocalDate): List<LocalDate> = (0L..6L).map { monday.plusDays(it) }

    fun weekRangeLabel(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return "${monday.monthValue}月${monday.dayOfMonth}日 - " +
            "${sunday.monthValue}月${sunday.dayOfMonth}日"
    }

    fun monthLabel(
        year: Int,
        month: Int,
    ): String = "${year}年${month}月"

    fun weekdayShort(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    fun fullDateLabel(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · ${weekdayShort(date)}"

    fun monthDay(date: LocalDate): String = "%02d.%02d".format(date.monthValue, date.dayOfMonth)

    fun daysUntil(
        deadline: LocalDate,
        today: LocalDate,
    ): Long = ChronoUnit.DAYS.between(today, deadline)

    /** A deadline today or within two days is urgent; earlier than today is overdue. */
    fun deadlineState(
        deadline: LocalDate?,
        today: LocalDate,
    ): DeadlineState {
        if (deadline == null) return DeadlineState.NONE
        val days = daysUntil(deadline, today)
        return when {
            days < 0 -> DeadlineState.OVERDUE
            days < 3 -> DeadlineState.URGENT
            else -> DeadlineState.LATER
        }
    }

    /**
     * The API returns zone-less UTC timestamps ("2026-11-01T09:15:02").
     * Treat them as UTC instead of the device zone, matching the web client.
     */
    fun parseServerInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val normalized = if (value.endsWith("Z") || value.matches(Regex(".*[+-]\\d{2}:?\\d{2}$"))) {
            value
        } else {
            "${value}Z"
        }
        return runCatching { Instant.parse(normalized) }.getOrNull()
    }

    fun formatDateTime(
        value: String?,
        fallback: String = "—",
    ): String {
        val instant = parseServerInstant(value) ?: return fallback
        val local = instant.atZone(ZoneId.systemDefault())
        return "%04d-%02d-%02d %02d:%02d".format(
            local.year,
            local.monthValue,
            local.dayOfMonth,
            local.hour,
            local.minute,
        )
    }

    /** Compare two server timestamps; null is treated as "unknown / older than anything". */
    fun serverTimeChanged(
        base: String?,
        current: String?,
    ): Boolean {
        if (current.isNullOrBlank()) return false
        if (base.isNullOrBlank()) return true
        val baseInstant = parseServerInstant(base)
        val currentInstant = parseServerInstant(current)
        if (baseInstant == null || currentInstant == null) return base != current
        return currentInstant.isAfter(baseInstant)
    }
}

enum class DeadlineState { NONE, OVERDUE, URGENT, LATER }
