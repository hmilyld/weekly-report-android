package com.hmilyld.weeklyreport.data.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a release APK and hands it to the system installer.
 *
 * Android requires the user to allow installs from this app once
 * ([Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES]); after that the package manager
 * refuses any APK whose signing certificate differs from the installed build,
 * which is why the release workflow must be signed with the same keystore.
 */
object UpdateInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val AUTHORITY_SUFFIX = ".updates"

    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    /** True when the system will let this app launch the installer. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Opens the "install unknown apps" screen for this app. Returns false when
     * no activity answered, so the caller can explain instead of doing nothing.
     */
    fun openInstallPermissionSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Streams [info]'s APK into the app cache. [onProgress] receives 0..1 (or
     * -1 while the total size is unknown) and is called on the IO dispatcher.
     *
     * When the manifest published a SHA-256 the file is verified before it is
     * handed on: a truncated download, or one whose bytes do not match the
     * release, is deleted and rejected instead of reaching the installer. The
     * hash comes from the same manifest as the URL, so it catches transfer
     * damage and manifest/asset mismatches, not a malicious manifest.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
        client: OkHttpClient = UpdateChecker.downloadClient(),
    ): File =
        withContext(Dispatchers.IO) {
            val url = info.apkUrl ?: throw IOException("这个版本没有附带 APK")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "weekly-report-android")
                .get()
                .build()
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "weekly-report-${info.versionName}.apk")
            val partial = File(directory, target.name + ".part")
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载失败（${response.code}）")
                }
                val body = response.body ?: throw IOException("下载失败：没有内容")
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            // -1 keeps the bar indeterminate while the total is
                            // unknown; coerceIn guards against a server that
                            // sends more bytes than it announced.
                            onProgress(
                                if (total > 0) {
                                    (written.toFloat() / total).coerceIn(0f, 1f)
                                } else {
                                    -1f
                                },
                            )
                        }
                        output.flush()
                    }
                }
            }
            if (partial.length() <= 0L) {
                partial.delete()
                throw IOException("下载的安装包是空的，请重试")
            }
            val expected = info.apkSha256
            if (!expected.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    partial.delete()
                    throw ChecksumMismatchException()
                }
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IOException("无法保存安装包")
            }
            target
        }

    /** The downloaded bytes do not match the hash the release published. */
    class ChecksumMismatchException : IOException("安装包校验失败，请重新下载")

    /**
     * Launches the system installer for [apk]. Returns false when no installer
     * activity accepted the intent.
     *
     * The file must survive this call: [Intent.ACTION_VIEW] only hands the URI
     * to the installer, which reads it when the user confirms. Deleting the APK
     * here would make that confirmation fail with a system-level error.
     */
    fun install(
        context: Context,
        apk: File,
    ): Boolean {
        val uri = FileProvider.getUriForFile(context, authority(context), apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** Removes leftover APKs; called before a new download, never right after installing. */
    fun clearCache(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    /** Drops only the half-written file, keeping any completed download. */
    fun clearPartial(context: Context) {
        File(context.cacheDir, "updates").listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    /**
     * Drops every APK except [keep].
     *
     * The installed file is only released once the user leaves the installer,
     * so cleanup has to preserve the one just handed over and discard the rest.
     */
    fun clearCacheExcept(
        context: Context,
        keep: File?,
    ) {
        File(context.cacheDir, "updates").listFiles()
            ?.filter { it != keep }
            ?.forEach { it.delete() }
    }
}
