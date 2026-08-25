package com.solidlink.files

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateStagingStoreTest {
    @Test
    fun sanitizerRemovesPathSeparatorsAndControlCharacters() {
        assertEquals("photo__name.jpg", DisplayNameSanitizer.forOutput("photo/\u0000name.jpg"))
        assertEquals("Unnamed file", DisplayNameSanitizer.forOutput("   "))
        assertTrue(DisplayNameSanitizer.forOutput("a".repeat(500)).length <= 240)
    }

    @Test
    fun stagingRefusesReadsUntilVerifiedAndCanReopenVerifiedFile() {
        val directory = Files.createTempDirectory("solidlink-staging").toFile()
        try {
            val store = PrivateStagingStore(directory)
            val created = store.create("object-1", expectedLength = 8)
            val handle = (created as FileOperationResult.Success).value

            assertTrue(handle.openVerifiedInput() is FileOperationResult.Failure)
            assertTrue(handle.writeAt(0, byteArrayOf(1, 2, 3, 4), 4) is FileOperationResult.Success)
            assertTrue(handle.writeAt(4, byteArrayOf(5, 6, 7, 8), 4) is FileOperationResult.Success)
            assertTrue(handle.markVerified() is FileOperationResult.Success)
            assertTrue(handle.isVerified())

            val reopened = (store.reopen("object-1") as FileOperationResult.Success).value
            val content = (reopened.openVerifiedInput() as FileOperationResult.Success).value.use { it.readBytes() }
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), content)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun stagingRejectsUnsafeObjectIds() {
        val directory = Files.createTempDirectory("solidlink-staging").toFile()
        try {
            val result = PrivateStagingStore(directory).create("../escape", null)
            assertTrue(result is FileOperationResult.Failure)
        } finally {
            directory.deleteRecursively()
        }
    }
}
