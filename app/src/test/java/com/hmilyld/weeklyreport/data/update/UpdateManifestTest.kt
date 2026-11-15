package com.hmilyld.weeklyreport.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    // Shape produced by the "Build update.json" step in release.yml.
    private val realManifest =
        """
        {
          "versionName": "1.1.0",
          "versionCode": 2,
          "tag": "v1.1.0",
          "title": "周记 v1.1.0",
          "notes": "### 新增\n- 应用内更新。\n\n### 修复\n- 超时。\n",
          "pageUrl": "https://github.com/hmilyld/weekly-report-android/releases/tag/v1.1.0",
          "apkUrl": "https://github.com/hmilyld/weekly-report-android/releases/download/v1.1.0/app-release.apk",
          "apkSize": 2588889,
          "apkSha256": "E8E47187FB52F3BDB0E4E8B7C5D3401D36A09954AA501ED32A87D8E8102044A5"
        }
        """.trimIndent()

    @Test
    fun `reads every field the workflow writes`() {
        val info = UpdateManifest.parse(realManifest)
        requireNotNull(info)
        assertEquals("1.1.0", info.versionName)
        assertEquals(2, info.versionCode)
        assertEquals("v1.1.0", info.tagName)
        assertEquals("周记 v1.1.0", info.title)
        assertTrue(info.notes.startsWith("### 新增"))
        assertEquals(2588889L, info.apkSize)
        assertEquals(
            "https://github.com/hmilyld/weekly-report-android/releases/download/" +
                "v1.1.0/app-release.apk",
            info.apkUrl,
        )
    }

    @Test
    fun `the hash is normalized so comparison is case insensitive`() {
        val info = UpdateManifest.parse(realManifest)
        requireNotNull(info)
        assertEquals(info.apkSha256, info.apkSha256?.lowercase())
        assertTrue(info.apkSha256!!.endsWith("2044a5"))
    }

    @Test
    fun `missing optional fields fall back instead of failing`() {
        val info = UpdateManifest.parse(
            """{"versionName":"1.2.0","tag":"v1.2.0"}""",
        )
        requireNotNull(info)
        assertEquals("v1.2.0", info.tagName)
        assertEquals("v1.2.0", info.title)
        assertNull(info.apkUrl)
        assertNull(info.apkSha256)
        assertEquals(0, info.versionCode)
        assertEquals(0L, info.apkSize)
        assertEquals(
            "https://github.com/hmilyld/weekly-report-android/releases/tag/v1.2.0",
            info.pageUrl,
        )
    }

    @Test
    fun `a tag is derived when the manifest omits it`() {
        val info = UpdateManifest.parse("""{"versionName":"2.0.0"}""")
        requireNotNull(info)
        assertEquals("v2.0.0", info.tagName)
    }

    @Test
    fun `unusable payloads are rejected so the API fallback can run`() {
        assertNull(UpdateManifest.parse(""))
        assertNull(UpdateManifest.parse("not json"))
        assertNull(UpdateManifest.parse("[]"))
        assertNull(UpdateManifest.parse("""{"versionName":""}"""))
        assertNull(UpdateManifest.parse("""{"versionName":"latest"}"""))
        // A GitHub error body, which must not look like "no update available".
        assertNull(UpdateManifest.parse("""{"message":"Not Found"}"""))
    }

    @Test
    fun `an empty hash is treated as absent`() {
        val info = UpdateManifest.parse(
            """{"versionName":"1.3.0","apkSha256":"  "}""",
        )
        requireNotNull(info)
        assertNull(info.apkSha256)
    }
}
