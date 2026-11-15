package com.hmilyld.weeklyreport.data.update

import java.io.IOException

/**
 * How [UpdateChecker] combines the static manifest with the API fallback.
 *
 * Split out so the chain can be tested without a network or Android: the rule
 * matters because a manifest that is merely *unreadable* must not silently send
 * every client back to the rate-limited API, and a manifest that is *absent*
 * must, or older releases would never be offered.
 */
object UpdateFallback {
    /** What the manifest request produced. */
    sealed interface ManifestOutcome {
        /** A usable manifest. */
        data class Found(
            val info: UpdateInfo,
        ) : ManifestOutcome

        /**
         * The asset is not there (404), or is there but unusable. Falling back is
         * correct: an older release predates the manifest, and a corrupted one
         * should still let the user see the release.
         */
        data object Unusable : ManifestOutcome

        /** The request itself failed; retrying against the API would just fail again. */
        data class Failed(
            val error: IOException,
        ) : ManifestOutcome
    }

    /**
     * Resolves the newest release from [manifest] first, calling [api] only when
     * the manifest is [ManifestOutcome.Unusable].
     */
    suspend fun resolve(
        manifest: ManifestOutcome,
        api: suspend () -> UpdateInfo?,
    ): UpdateInfo? =
        when (manifest) {
            is ManifestOutcome.Found -> manifest.info
            is ManifestOutcome.Unusable -> api()
            is ManifestOutcome.Failed -> throw manifest.error
        }
}
