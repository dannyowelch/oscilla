package com.dannyowelch.oscilla.release

/**
 * Release identity derived from a git tag.
 *
 * `vMAJOR.MINOR.PATCH` becomes versionName `MAJOR.MINOR.PATCH` and
 * versionCode `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`.
 * Minor and patch must be 0..999 so a higher tag always gets a higher versionCode.
 * versionCode starts at 1 (`v0.0.1`); `v0.0.0` is rejected.
 */
data class ReleaseVersion(
    val versionName: String,
    val versionCode: Int,
) {
    companion object {
        private val TAG = Regex("""^v(\d+)\.(\d+)\.(\d+)$""")
        const val MAX_COMPONENT = 999

        fun parse(tag: String): ReleaseVersion {
            val match = TAG.matchEntire(tag.trim())
                ?: throw IllegalArgumentException(
                    "Tag '$tag' must look like v1.2.3 (three numbers, no suffix).",
                )
            val major = match.groupValues[1].toInt()
            val minor = match.groupValues[2].toInt()
            val patch = match.groupValues[3].toInt()
            require(minor <= MAX_COMPONENT && patch <= MAX_COMPONENT) {
                "Minor and patch in '$tag' must be 0..$MAX_COMPONENT so versionCode keeps increasing."
            }
            val code = major.toLong() * 1_000_000L + minor.toLong() * 1_000L + patch.toLong()
            require(code in 1L..Int.MAX_VALUE.toLong()) {
                "versionCode for '$tag' is $code, which is outside 1..${Int.MAX_VALUE}."
            }
            return ReleaseVersion(
                versionName = "$major.$minor.$patch",
                versionCode = code.toInt(),
            )
        }
    }
}
