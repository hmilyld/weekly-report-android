package com.hmilyld.weeklyreport.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {
    // Trimmed from a real `GET /repos/{owner}/{repo}/releases/latest` response.
    private val realPayload =
        """
        {
          "url": "https://api.github.com/repos/hmilyld/weekly-report-android/releases/1",
          "html_url": "https://github.com/hmilyld/weekly-report-android/releases/tag/v1.0.0",
          "assets_url": "https://api.github.com/repos/hmilyld/weekly-report-android/releases/1/assets",
          "tag_name": "v1.0.0",
          "target_commitish": "main",
          "name": "周记 v1.0.0",
          "draft": false,
          "prerelease": false,
          "created_at": "2026-11-14T16:19:00Z",
          "published_at": "2026-11-14T16:19:30Z",
          "body": "首个正式版本。\n\n### 新增\n\n- 日报：按周浏览。",
          "assets": [
            {
              "url": "https://api.github.com/repos/hmilyld/weekly-report-android/releases/assets/1",
              "name": "app-release-unsigned.apk",
              "size": 2588889,
              "browser_download_url": "https://github.com/hmilyld/weekly-report-android/releases/download/v1.0.0/app-release-unsigned.apk"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `reads the version, notes and APK of a real release`() {
        val info = ReleaseParser.parse(realPayload)
        requireNotNull(info)
        assertEquals("1.0.0", info.versionName)
        assertEquals("v1.0.0", info.tagName)
        assertEquals("周记 v1.0.0", info.title)
        assertTrue(info.notes.startsWith("首个正式版本。"))
        assertEquals(
            "https://github.com/hmilyld/weekly-report-android/releases/download/" +
                "v1.0.0/app-release-unsigned.apk",
            info.apkUrl,
        )
        assertEquals(2588889L, info.apkSize)
        assertEquals(
            "https://github.com/hmilyld/weekly-report-android/releases/tag/v1.0.0",
            info.pageUrl,
        )
    }

    @Test
    fun `skips non-APK assets and picks the first APK`() {
        val payload =
            """
            {
              "tag_name": "v1.2.0",
              "html_url": "https://example.test/release",
              "body": "notes",
              "assets": [
                {"name": "checksums.txt", "size": 12},
                {"name": "app-release.apk", "size": 100, "browser_download_url": "https://example.test/a.apk"},
                {"name": "other.apk", "size": 200, "browser_download_url": "https://example.test/b.apk"}
              ]
            }
            """.trimIndent()
        val info = ReleaseParser.parse(payload)
        requireNotNull(info)
        assertEquals("https://example.test/a.apk", info.apkUrl)
        assertEquals(100L, info.apkSize)
    }

    @Test
    fun `a release without an APK still parses`() {
        val info = ReleaseParser.parse(
            """{"tag_name":"v2.0.0","name":"","body":"","assets":[]}""",
        )
        requireNotNull(info)
        assertEquals("2.0.0", info.versionName)
        assertEquals("v2.0.0", info.title)
        assertNull(info.apkUrl)
        assertEquals(0L, info.apkSize)
        assertEquals(UpdateChecker.RELEASES_PAGE_URL, info.pageUrl)
    }

    @Test
    fun `rejects payloads that are not a release`() {
        assertNull(ReleaseParser.parse(""))
        assertNull(ReleaseParser.parse("not json"))
        assertNull(ReleaseParser.parse("""{"message":"Not Found"}"""))
        assertNull(ReleaseParser.parse("""{"tag_name":"latest"}"""))
        assertNull(ReleaseParser.parse("[]"))
    }
}
