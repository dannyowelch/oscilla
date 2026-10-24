package com.dannyowelch.oscilla.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseVersionTest {
    @Test
    fun tagBecomesAnIncreasingVersionCode() {
        assertEquals(ReleaseVersion("0.1.0", 1_000), ReleaseVersion.parse("v0.1.0"))
        assertEquals(ReleaseVersion("0.1.1", 1_001), ReleaseVersion.parse("v0.1.1"))
        assertEquals(ReleaseVersion("0.2.0", 2_000), ReleaseVersion.parse("v0.2.0"))
        assertEquals(ReleaseVersion("1.2.3", 1_002_003), ReleaseVersion.parse("v1.2.3"))
        assertEquals(ReleaseVersion("1.2.3", 1_002_003), ReleaseVersion.parse("  v1.2.3\n"))
        val tags = listOf("v0.0.1", "v0.0.2", "v0.1.0", "v0.1.1", "v1.0.0", "v1.2.3", "v2.0.0")
        val codes = tags.map { ReleaseVersion.parse(it).versionCode }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.toSet().size, codes.size)
        assertTrue(ReleaseVersion.parse("v1.999.999").versionCode < ReleaseVersion.parse("v2.0.0").versionCode)
    }

    @Test
    fun rejectsTagsThatWouldNotKeepVersionCodeIncreasing() {
        assertRejected("v1.2")
        assertRejected("v1.2.3-beta")
        assertRejected("1.2.3")
        assertRejected("v0.0.0")
        assertRejected("v1.1000.0")
        assertRejected("v3000.0.0")
    }

    @Test
    fun releaseBuildDerivesVersionCodeFromReleaseVersion() {
        val gradle = repoFile("app/build.gradle.kts")
        assertTrue(gradle.contains("ReleaseVersion.parse"))
        val catalog = repoFile("gradle/libs.versions.toml")
        val root = repoFile("build.gradle.kts")
        assertTrue(catalog.contains("kotlin = \"2.4.21\""))
        assertTrue(root.contains("kotlin-gradle-plugin:2.4.21"))
    }

    private fun assertRejected(tag: String) {
        try {
            ReleaseVersion.parse(tag)
            throw AssertionError("Expected '$tag' to be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.isNotBlank() == true)
        }
    }

    private fun repoFile(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val current = dir ?: return@repeat
            if (File(current, "settings.gradle.kts").isFile) {
                return File(current, relative).readText()
            }
            dir = current.parentFile
        }
        throw AssertionError("Could not find the repo root from ${System.getProperty("user.dir")}")
    }
}
