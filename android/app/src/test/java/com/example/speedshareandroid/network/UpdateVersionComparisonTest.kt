package com.example.speedshareandroid.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionComparisonTest {

    @Test
    fun equalVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.1.1", "1.1.1"))
    }

    @Test
    fun patchBump() {
        assertTrue(UpdateChecker.isNewerVersion("1.1.2", "1.1.1"))
    }

    @Test
    fun minorBump() {
        assertTrue(UpdateChecker.isNewerVersion("1.2.0", "1.1.5"))
    }

    @Test
    fun majorBump() {
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.99.99"))
    }

    @Test
    fun olderRemoteIsNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.1.0", "1.1.1"))
    }

    @Test
    fun rcTagWithNewerMainIsStillNewer() {
        // 1.2.0-rc1 vs 1.1.1: the main 1.2.0 is newer, so the RC
        // counts as newer even without prerelease opt-in.
        assertTrue(UpdateChecker.isNewerVersion("1.2.0-rc1", "1.1.1"))
    }

    @Test
    fun rcTagWithSameMainNeedsOptIn() {
        // 1.1.1 vs 1.1.1-rc1: a release is always newer than an RC.
        assertTrue(UpdateChecker.isNewerVersion("1.1.1", "1.1.1-rc1"))

        // 1.1.1-rc2 vs 1.1.1-rc1: same main, both pre. Newer RC is not
        // newer without opt-in, but is newer with opt-in.
        assertFalse(UpdateChecker.isNewerVersion("1.1.1-rc2", "1.1.1-rc1"))
        assertTrue(UpdateChecker.isNewerVersion("1.1.1-rc2", "1.1.1-rc1", allowPrerelease = true))
    }

    @Test
    fun versionParsingToleratesMissingParts() {
        // Equal-length parts: easy
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0.0"))
        // Remote has more parts: 1.1 vs 1.0.1 -> 1.1 wins
        assertTrue(UpdateChecker.isNewerVersion("1.1", "1.0.5"))
        // Local has more parts: 1.0.0 vs 1 -> equal
        assertFalse(UpdateChecker.isNewerVersion("1", "1.0.0"))
    }
}
