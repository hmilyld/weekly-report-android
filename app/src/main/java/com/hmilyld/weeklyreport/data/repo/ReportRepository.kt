package com.hmilyld.weeklyreport.data.repo

import com.hmilyld.weeklyreport.data.local.AppDatabase
import com.hmilyld.weeklyreport.data.local.ReportCacheEntry
import com.hmilyld.weeklyreport.data.model.MonthlyReportDto
import com.hmilyld.weeklyreport.data.model.WeeklyReportDto
import com.hmilyld.weeklyreport.network.ApiClient
import com.hmilyld.weeklyreport.network.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportRepository(
    private val db: AppDatabase,
    private val api: ApiClient
) {
    suspend fun weekly(weekStart: String): WeeklyReportDto? = withContext(Dispatchers.IO) {
        try {
            val report = api.weekly(weekStart)
            cacheWeekly(weekStart, report)
            report
        } catch (error: ApiException) {
            if (error.isNotFound) {
                db.clearReport(weeklyKey(weekStart))
                null
            } else {
                throw error
            }
        }
    }

    suspend fun generateWeekly(weekStart: String): WeeklyReportDto = withContext(Dispatchers.IO) {
        val report = api.generateWeekly(weekStart)
        cacheWeekly(weekStart, report)
        report
    }

    suspend fun updateWeekly(weekStart: String, content: String): WeeklyReportDto = withContext(Dispatchers.IO) {
        val report = api.updateWeekly(weekStart, content)
        cacheWeekly(weekStart, report)
        report
    }

    suspend fun monthly(year: Int, month: Int): MonthlyReportDto? = withContext(Dispatchers.IO) {
        try {
            val report = api.monthly(year, month)
            cacheMonthly(report)
            report
        } catch (error: ApiException) {
            if (error.isNotFound) {
                db.clearReport(monthlyKey(year, month))
                null
            } else {
                throw error
            }
        }
    }

    suspend fun generateMonthly(year: Int, month: Int): MonthlyReportDto = withContext(Dispatchers.IO) {
        val report = api.generateMonthly(year, month)
        cacheMonthly(report)
        report
    }

    suspend fun updateMonthly(year: Int, month: Int, content: String): MonthlyReportDto = withContext(Dispatchers.IO) {
        val report = api.updateMonthly(year, month, content)
        cacheMonthly(report)
        report
    }

    fun cachedWeekly(weekStart: String): ReportCacheEntry? = db.cachedReport(weeklyKey(weekStart))

    fun cachedMonthly(year: Int, month: Int): ReportCacheEntry? = db.cachedReport(monthlyKey(year, month))

    private fun cacheWeekly(weekStart: String, report: WeeklyReportDto) {
        db.cacheReport(
            ReportCacheEntry(
                cacheKey = weeklyKey(weekStart),
                content = report.content,
                modelName = report.modelName,
                generatedAt = report.generatedAt
            )
        )
    }

    private fun cacheMonthly(report: MonthlyReportDto) {
        db.cacheReport(
            ReportCacheEntry(
                cacheKey = monthlyKey(report.year, report.month),
                content = report.content,
                modelName = report.modelName,
                generatedAt = report.generatedAt
            )
        )
    }

    companion object {
        fun weeklyKey(weekStart: String) = "w:$weekStart"
        fun monthlyKey(year: Int, month: Int) = "m:$year-$month"
    }
}
