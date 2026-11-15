package com.hmilyld.weeklyreport.data.update

import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.data.prefs.SettingsStore
import com.hmilyld.weeklyreport.network.ApiPayloads
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads the newest release of this repository.
 *
 * The app is self-hosted and distributed as an APK through GitHub Releases, so
 * the release channel *is* the update channel: no Play services, no extra
 * backend endpoint.
 *
 * The primary source is the `update.json` asset attached to each release and
 * fetched through `releases/latest/download/`, a plain static file that is not
 * subject to the API's 60-requests-per-hour-per-IP anonymous limit. When the
 * asset is absent — a release published before the workflow produced it — the
 * check falls back to the API.
 */
class UpdateChecker(
    private val settings: SettingsStore,
    private val client: OkHttpClient = defaultClient(),
    // A build-time seam: production always points at the real release channel,
    // while a local build can aim at a mock feed to exercise the flow offline.
    private val manifestUrl: String = BuildConfig.UPDATE_FEED_URL.ifBlank { LATEST_MANIFEST_URL },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun check(force: Boolean = false): UpdateCheckResult {
        if (!UpdateGate.shouldCheck(
                force = force,
                autoEnabled = settings.autoUpdateCheckValue(),
                lastCheckAt = settings.lastUpdateCheckAt(),
                now = now(),
            )
        ) {
            return UpdateCheckResult.Skipped
        }

        val info = try {
            fetchLatest()
        } catch (error: UpdateHttpException) {
            // GitHub answered, so the attempt counts: back off instead of
            // retrying a rate-limited or broken endpoint on every launch.
            settings.setLastUpdateCheckAt(now())
            return UpdateCheckResult.Failed(error.message)
        } catch (error: IOException) {
            // The network never got through. Recording this would hide the next
            // release for a whole day after one bad moment in the subway.
            return UpdateCheckResult.Failed(NETWORK_FAILURE)
        } catch (error: Exception) {
            return UpdateCheckResult.Failed(error.message ?: GENERIC_FAILURE)
        }

        settings.setLastUpdateCheckAt(now())
        if (info == null) return UpdateCheckResult.UpToDate
        if (!UpdateGate.shouldOffer(
                force = force,
                published = info.versionName,
                installed = BuildConfig.VERSION_NAME,
                skipped = settings.skippedUpdateVersion(),
            )
        ) {
            // Either nothing newer, or exactly the release the user postponed.
            return UpdateCheckResult.Skipped
        }
        return UpdateCheckResult.Available(info)
    }

    /** Remembers that the user postponed [version], so it is not offered again. */
    fun markSkipped(version: String) {
        settings.setSkippedUpdateVersion(version)
    }

    /**
     * The latest release, or null when the repository has none.
     *
     * The manifest is tried first so a normal check costs no API quota; only a
     * missing or unreadable manifest reaches the API.
     */
    suspend fun fetchLatest(): UpdateInfo? = fetchManifest() ?: fetchLatestFromApi()

    /** Null when the asset is absent or unreadable, so the caller can fall back. */
    private suspend fun fetchManifest(): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "weekly-report-android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                UpdateManifest.parse(response.body?.string().orEmpty())
            }
        }

    private suspend fun fetchLatestFromApi(): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                // GitHub rejects requests without a User-Agent.
                .header("User-Agent", "weekly-report-android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw UpdateHttpException(githubFailureMessage(response.code, body))
                }
                ReleaseParser.parse(body)
            }
        }

    /**
     * GitHub answers an anonymous client with 403 (not 429) once the hourly
     * quota is gone, and mentions "rate limit" in the body. Reporting that as
     * "no permission" would send the user looking for a token they do not need.
     */
    private fun githubFailureMessage(
        code: Int,
        body: String,
    ): String {
        val limited = code == 429 ||
            (code == 403 && body.contains("rate limit", ignoreCase = true))
        return if (limited) RATE_LIMITED else ApiPayloads.errorMessage(body, code)
    }

    /** GitHub answered but not with a release; distinct from a network failure. */
    class UpdateHttpException(
        override val message: String,
    ) : IOException(message)

    companion object {
        const val REPO_SLUG = "hmilyld/weekly-report-android"
        const val LATEST_MANIFEST_URL =
            "https://github.com/$REPO_SLUG/releases/latest/download/update.json"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_SLUG/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/$REPO_SLUG/releases"

        /** The page for one tag, used when a manifest omits `pageUrl`. */
        fun releasePageUrl(tag: String): String = "https://github.com/$REPO_SLUG/releases/tag/$tag"

        const val RATE_LIMITED = "GitHub 暂时限制了检查频率，请稍后再试"
        const val NETWORK_FAILURE = "无法连接 GitHub，请检查网络"
        const val GENERIC_FAILURE = "检查更新失败，请稍后重试"

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // releases/latest/download/ redirects to the CDN host.
                .followRedirects(true)
                .build()

        /**
         * A release APK is tens of megabytes; a whole-call timeout would cut a
         * slow but healthy download off midway. Only the per-read timeout is
         * bounded here, so a stalled connection still fails.
         */
        fun downloadClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build()
    }
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    /** Not attempted: switched off, checked recently, or this version was postponed. */
    data object Skipped : UpdateCheckResult

    data class Available(
        val info: UpdateInfo,
    ) : UpdateCheckResult

    data class Failed(
        val message: String,
    ) : UpdateCheckResult
}
