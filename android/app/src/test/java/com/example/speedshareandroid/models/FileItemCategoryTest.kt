package com.example.speedshareandroid.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FileItemCategoryTest {

    @Test
    fun videoExtensionsAreClassified() {
        listOf("a.mp4", "a.MKV", "movie.avi", "clip.mov", "x.webm", "x.3gp").forEach {
            assertEquals("VIDEO for $it", "VIDEO", FileItem.getCategoryForFileName(it))
        }
    }

    @Test
    fun imageExtensionsAreClassified() {
        listOf("a.jpg", "a.jpeg", "a.png", "a.webp", "a.gif", "a.heic", "a.svg").forEach {
            assertEquals("IMAGE for $it", "IMAGE", FileItem.getCategoryForFileName(it))
        }
    }

    @Test
    fun audioExtensionsAreClassified() {
        listOf("a.mp3", "a.flac", "a.wav", "a.m4a", "a.aac", "a.ogg").forEach {
            assertEquals("AUDIO for $it", "AUDIO", FileItem.getCategoryForFileName(it))
        }
    }

    @Test
    fun archiveExtensionsAreClassified() {
        listOf("a.zip", "a.rar", "a.7z", "a.tar", "a.gz", "a.iso").forEach {
            assertEquals("ARCHIVE for $it", "ARCHIVE", FileItem.getCategoryForFileName(it))
        }
    }

    @Test
    fun documentExtensionsAreClassified() {
        listOf("a.pdf", "a.doc", "a.docx", "a.xls", "a.xlsx", "a.ppt", "a.pptx", "a.txt").forEach {
            assertEquals("DOCUMENT for $it", "DOCUMENT", FileItem.getCategoryForFileName(it))
        }
    }

    @Test
    fun unknownExtensionFallsBackToFile() {
        assertEquals("FILE", FileItem.getCategoryForFileName("mystery.xyz"))
        assertEquals("FILE", FileItem.getCategoryForFileName("no_extension"))
    }

    @Test
    fun caseInsensitiveMatching() {
        assertEquals("VIDEO", FileItem.getCategoryForFileName("A.MP4"))
        assertEquals("IMAGE", FileItem.getCategoryForFileName("A.PNG"))
    }

    @Test
    fun sizeFormattingIsStable() {
        // Spot-check across the units. We pin to Locale.US so the decimal
        // separator is always '.'.
        assertEquals("0 B", FileItem.formatBytes(0))
        assertEquals("512 B", FileItem.formatBytes(512))
        assertEquals("1.0 KB", FileItem.formatBytes(1024))
        assertEquals("1.5 KB", FileItem.formatBytes(1024 + 512))
        assertEquals("4.00 MB", FileItem.formatBytes(4L * 1024 * 1024))
        assertEquals("2.50 GB", FileItem.formatBytes((1024L * 1024 * 1024 * 5) / 2))
    }

    @Test
    fun sizeFormattingNeverUsesLocalizedDecimal() {
        // Independent of system locale: comma vs dot decimal must stay a dot.
        assertNotEquals(-1, FileItem.formatBytes(1024L * 1024).indexOf('.'))
    }
}
