package com.hmilyld.weeklyreport.data.update

import com.hmilyld.weeklyreport.network.ApiPayloads
import com.hmilyld.weeklyreport.util.SemVer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Turns a GitHub "latest release" payload into [UpdateInfo].
 *
 * Kept free of Android types so the mapping can be unit tested against a real
 * captured response; the network call around it lives in [UpdateChecker].
 */
object ReleaseParser {
    fun parse(raw: String): UpdateInfo? {
        val release = runCatching { ApiPayloads.json.parseToJsonElement(raw) }.getOrNull()
            as? JsonObject ?: return null
        val tag = release.string("tag_name")?.takeIf { it.isNotBlank() } ?: return null
        val version = SemVer.parse(tag)?.toString() ?: return null
        val apk = (release["assets"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
        return UpdateInfo(
            versionName = version,
            tagName = tag,
            title = release.string("name")?.takeIf { it.isNotBlank() } ?: tag,
            notes = release.string("body").orEmpty().trim(),
            pageUrl = release.string("html_url")?.takeIf { it.isNotBlank() }
                ?: UpdateChecker.RELEASES_PAGE_URL,
            apkUrl = apk?.string("browser_download_url")?.takeIf { it.isNotBlank() },
            apkSize = apk?.string("size")?.toLongOrNull() ?: 0L,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
