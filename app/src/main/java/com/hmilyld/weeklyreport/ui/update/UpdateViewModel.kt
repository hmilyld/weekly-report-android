package com.hmilyld.weeklyreport.ui.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.weeklyreport.BuildConfig
import com.hmilyld.weeklyreport.R
import com.hmilyld.weeklyreport.data.update.UpdateCheckResult
import com.hmilyld.weeklyreport.data.update.UpdateChecker
import com.hmilyld.weeklyreport.data.update.UpdateInfo
import com.hmilyld.weeklyreport.data.update.UpdateInstaller
import com.hmilyld.weeklyreport.ui.UiMessage
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where an in-app update currently stands. */
sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data class Downloading(
        val progress: Float,
    ) : UpdatePhase

    data object ReadyToInstall : UpdatePhase
}

data class UpdateUiState(
    val checking: Boolean = false,
    val available: UpdateInfo? = null,
    val promptVisible: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val installAllowed: Boolean = true,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    /** Why the last download failed; shown inline because the prompt can open
     * on the login screen, where no Snackbar host exists. */
    val error: String? = null,
    /** A one-line notice for an action taken from the dialog itself (opening the
     * installer settings or the release page failed). Inline for the same
     * reason as [error]. */
    val notice: String? = null,
)

/**
 * Owns the release-channel prompt: one check per day on start, a dialog when a
 * newer release exists, and the download/install hand-off.
 *
 * Release only. A debug build carries the `.debug` applicationId and a
 * different signature, so it could never replace an installed release.
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    context: Context,
) : ViewModel() {
    // Held across configuration changes: keep the application context so no
    // Activity leaks.
    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow(
        UpdateUiState(installAllowed = UpdateInstaller.canInstall(appContext)),
    )
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    // The prompt is drawn above the session switch, so it can appear while the
    // login screen is showing and no Snackbar host is collecting. Messages that
    // belong to the dialog are therefore rendered inline from [UpdateUiState];
    // this flow only carries results of the manual check from Settings.
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var downloadedApk: File? = null

    /** Runs on start; the checker itself decides whether to hit the network. */
    fun checkOnStart() {
        if (!BuildConfig.DEBUG || BuildConfig.UPDATE_FEED_URL.isNotBlank()) {
            check(force = false)
        }
    }

    /** Manual check from Settings: always talks to GitHub and always reports. */
    fun checkManually() {
        check(force = true, reportResult = true)
    }

    private fun check(
        force: Boolean,
        reportResult: Boolean = false,
    ) {
        if (_state.value.checking) return
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val result = checker.check(force)
            _state.update { it.copy(checking = false) }
            when (result) {
                is UpdateCheckResult.Available ->
                    _state.update {
                        it.copy(
                            available = result.info,
                            promptVisible = true,
                            phase = UpdatePhase.Idle,
                            error = null,
                        )
                    }

                is UpdateCheckResult.Failed -> if (reportResult) {
                    _messages.emit(UiMessage.Raw(result.message))
                }

                is UpdateCheckResult.UpToDate -> if (reportResult) {
                    _messages.emit(UiMessage.Text(R.string.update_latest))
                }

                is UpdateCheckResult.Skipped -> if (reportResult) {
                    _messages.emit(UiMessage.Text(R.string.update_latest))
                }
            }
        }
    }

    /**
     * The user pressed 稍后: do not offer this exact release again.
     *
     * Dismissing the dialog another way (back, outside tap) goes through
     * [hidePrompt] instead, because that is not a decision to skip the version.
     */
    fun postponePrompt() {
        val info = _state.value.available
        if (info != null) checker.markSkipped(info.versionName)
        hidePrompt()
    }

    /** Closes the dialog without recording a skip. */
    fun hidePrompt() {
        _state.update { it.copy(promptVisible = false, phase = UpdatePhase.Idle) }
    }

    fun reopenPrompt() {
        if (_state.value.available != null) {
            _state.update { it.copy(promptVisible = true) }
        }
    }

    /** Re-reads the "install unknown apps" grant after the settings screen. */
    fun refreshInstallPermission() {
        _state.update { it.copy(installAllowed = UpdateInstaller.canInstall(appContext)) }
    }

    fun openInstallPermissionSettings() {
        if (!UpdateInstaller.openInstallPermissionSettings(appContext)) {
            _state.update {
                it.copy(notice = appContext.getString(R.string.update_open_settings_failed))
            }
        }
    }

    fun download() {
        val info = _state.value.available ?: return
        if (_state.value.phase is UpdatePhase.Downloading) return
        viewModelScope.launch {
            _state.update {
                it.copy(phase = UpdatePhase.Downloading(0f), error = null, notice = null)
            }
            try {
                val apk = UpdateInstaller.download(
                    context = appContext,
                    info = info,
                    onProgress = { progress ->
                        _state.update {
                            // -1 keeps the bar indeterminate while the size is
                            // unknown; the dialog renders 0 as "downloading".
                            it.copy(phase = UpdatePhase.Downloading(progress.coerceAtLeast(0f)))
                        }
                    },
                )
                downloadedApk = apk
                _state.update {
                    it.copy(
                        phase = UpdatePhase.ReadyToInstall,
                        installAllowed = UpdateInstaller.canInstall(appContext),
                    )
                }
            } catch (error: Exception) {
                Log.w("UpdateViewModel", "update download failed", error)
                UpdateInstaller.clearPartial(appContext)
                _state.update {
                    it.copy(
                        phase = UpdatePhase.Idle,
                        error = if (error is java.io.IOException) {
                            appContext.getString(R.string.update_download_failed_network)
                        } else {
                            error.message ?: appContext.getString(R.string.update_failed)
                        },
                    )
                }
            }
        }
    }

    /** Hands the downloaded APK to the system installer. */
    fun install() {
        val apk = downloadedApk ?: return
        if (!UpdateInstaller.canInstall(appContext)) {
            openInstallPermissionSettings()
            return
        }
        if (!UpdateInstaller.install(appContext, apk)) {
            _state.update {
                it.copy(notice = appContext.getString(R.string.update_no_installer))
            }
            return
        }
        // The installer owns the file from here; anything left over from an
        // earlier attempt (or a newer release) is dead weight in the cache.
        UpdateInstaller.clearCache(appContext)
        downloadedApk = null
        hidePrompt()
    }

    /** The release page is the fallback when the APK cannot be downloaded. */
    fun openReleasePage() {
        val url = _state.value.available?.pageUrl ?: UpdateChecker.RELEASES_PAGE_URL
        val opened = runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (!opened) {
            _state.update {
                it.copy(notice = appContext.getString(R.string.update_open_page_failed))
            }
        }
    }
}
