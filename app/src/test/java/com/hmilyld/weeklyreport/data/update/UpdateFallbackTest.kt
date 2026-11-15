package com.hmilyld.weeklyreport.data.update

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFallbackTest {
    private val manifestInfo = UpdateInfo(
        versionName = "1.1.0",
        tagName = "v1.1.0",
        title = "周记 v1.1.0",
        notes = "",
        pageUrl = "https://example.test/v1.1.0",
        apkUrl = "https://example.test/app.apk",
        apkSize = 1L,
    )

    private val apiInfo = manifestInfo.copy(versionName = "1.0.9", tagName = "v1.0.9")

    @Test
    fun `a usable manifest wins and the api is never called`() =
        runTest {
            var apiCalls = 0
            val result = UpdateFallback.resolve(
                manifest = UpdateFallback.ManifestOutcome.Found(manifestInfo),
                api = {
                    apiCalls += 1
                    apiInfo
                },
            )
            assertSame(manifestInfo, result)
            assertEquals(0, apiCalls)
        }

    @Test
    fun `a missing manifest falls back to the api`() =
        runTest {
            var apiCalls = 0
            val result = UpdateFallback.resolve(
                manifest = UpdateFallback.ManifestOutcome.Unusable,
                api = {
                    apiCalls += 1
                    apiInfo
                },
            )
            assertSame(apiInfo, result)
            assertEquals(1, apiCalls)
        }

    @Test
    fun `a corrupted manifest also falls back, so an old release is still offered`() =
        runTest {
            // UpdateManifest.parse returns null for an unusable body, which the
            // checker maps to Unusable; this pins the intended consequence.
            var apiCalls = 0
            UpdateFallback.resolve(
                manifest = UpdateFallback.ManifestOutcome.Unusable,
                api = {
                    apiCalls += 1
                    apiInfo
                },
            )
            assertEquals(1, apiCalls)
        }

    @Test
    fun `a network failure is not retried against the api`() =
        runTest {
            var apiCalls = 0
            val failure = IOException("unreachable")
            val thrown = runCatching {
                UpdateFallback.resolve(
                    manifest = UpdateFallback.ManifestOutcome.Failed(failure),
                    api = {
                        apiCalls += 1
                        apiInfo
                    },
                )
            }.exceptionOrNull()
            assertSame(failure, thrown)
            assertEquals(0, apiCalls)
        }

    @Test
    fun `both sources empty means no release at all`() =
        runTest {
            val result = UpdateFallback.resolve(
                manifest = UpdateFallback.ManifestOutcome.Unusable,
                api = { null },
            )
            assertNull(result)
        }

    @Test
    fun `the resolved value keeps the manifest's extra fields`() =
        runTest {
            val withHash = manifestInfo.copy(versionCode = 7, apkSha256 = "abc")
            val result = UpdateFallback.resolve(
                manifest = UpdateFallback.ManifestOutcome.Found(withHash),
                api = { apiInfo },
            )
            requireNotNull(result)
            assertEquals(7, result.versionCode)
            assertEquals("abc", result.apkSha256)
            assertTrue(result.apkSha256 != null)
        }
}
