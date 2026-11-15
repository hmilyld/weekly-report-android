package com.hmilyld.weeklyreport.data.update

/**
 * One published release, reduced to what the update prompt needs.
 */
data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
)
