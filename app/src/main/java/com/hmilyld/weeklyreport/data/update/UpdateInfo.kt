package com.hmilyld.weeklyreport.data.update

/**
 * One published release, reduced to what the update prompt needs.
 *
 * [versionCode] and [apkSha256] only arrive with the `update.json` manifest;
 * the GitHub API fallback leaves them at their defaults.
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int = 0,
    val tagName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
    val apkSha256: String? = null,
)
