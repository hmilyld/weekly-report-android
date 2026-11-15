package com.hmilyld.weeklyreport.data.update

import com.hmilyld.weeklyreport.network.ApiPayloads
import com.hmilyld.weeklyreport.util.SemVer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The `update.json` asset the release workflow attaches to every GitHub release.
 *
 * It exists so a check costs no API quota: `releases/latest/download/update.json`
 * is served as a static file, while the GitHub API allows only 60 anonymous
 * requests per hour per IP. It also carries `versionCode` and `sha256`, which
 * the API payload cannot give the client in a usable form.
 *
 * Android-free so the mapping is unit tested; the fetch lives in [UpdateChecker].
 */
object UpdateManifest {
    fun parse(raw: String): UpdateInfo? {
        val root = runCatching { ApiPayloads.json.parseToJsonElement(raw) }.getOrNull()
            as? JsonObject ?: return null
        val versionName = root.string("versionName")?.takeIf { it.isNotBlank() } ?: return null
        // A manifest with neither a parseable name nor a code cannot be compared
        // against the installed build, so it is not a usable answer.
        val version = SemVer.parse(versionName) ?: return null
        val versionCode = root.string("versionCode")?.toIntOrNull() ?: 0
        val tag = root.string("tag")?.takeIf { it.isNotBlank() } ?: "v$version"
        return UpdateInfo(
            versionName = version.toString(),
            versionCode = versionCode,
            tagName = tag,
            title = root.string("title")?.takeIf { it.isNotBlank() } ?: tag,
            notes = root.string("notes").orEmpty().trim(),
            pageUrl = root.string("pageUrl")?.takeIf { it.isNotBlank() }
                ?: UpdateChecker.releasePageUrl(tag),
            apkUrl = root.string("apkUrl")?.takeIf { it.isNotBlank() },
            apkSize = root.string("apkSize")?.toLongOrNull() ?: 0L,
            apkSha256 = root.string("apkSha256")?.takeIf { it.isNotBlank() }?.lowercase(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
