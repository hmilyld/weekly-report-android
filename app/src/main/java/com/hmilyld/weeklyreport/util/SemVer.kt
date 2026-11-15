package com.hmilyld.weeklyreport.util

/**
 * Version comparison for the GitHub release channel.
 *
 * Tags look like `v1.2.3`; `BuildConfig.VERSION_NAME` looks like `1.2.3` or
 * `1.2.3-debug`. Only the numeric `major.minor.patch` triple decides which
 * build is newer: a self-hosted app has no need to order `1.0.0-rc1` against
 * `1.0.0`, and the release workflow already refuses tags that disagree with
 * `app.versionName`.
 */
object SemVer {
    data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    /**
     * Reads the numeric triple out of [raw], ignoring a leading `v`, a
     * pre-release suffix (`-debug`, `-rc1`) and build metadata (`+4`).
     * Returns null when there is no usable number.
     */
    fun parse(raw: String?): Version? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val withoutPrefix = trimmed.removePrefix("v").removePrefix("V")
        val numeric = withoutPrefix.substringBefore('-').substringBefore('+').trim()
        if (numeric.isEmpty()) return null
        val parts = numeric.split('.')
        if (parts.size > 3) return null
        val numbers = parts.map { part ->
            if (part.isEmpty() || !part.all { it.isDigit() }) return null
            part.toIntOrNull() ?: return null
        }
        return Version(
            major = numbers.getOrElse(0) { 0 },
            minor = numbers.getOrElse(1) { 0 },
            patch = numbers.getOrElse(2) { 0 },
        )
    }

    /** True when [candidate] names a version strictly newer than [current]. */
    fun isNewer(
        candidate: String?,
        current: String?,
    ): Boolean {
        val next = parse(candidate) ?: return false
        val installed = parse(current) ?: return false
        return next > installed
    }
}
