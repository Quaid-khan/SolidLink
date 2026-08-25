package com.solidlink.files

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilePickerIntentsInstrumentedTest {
    @Test
    fun openDocumentsUsesSafAndPersistableReadGrant() {
        val intent = FilePickerIntents.openDocuments("application/pdf")

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("application/pdf", intent.type)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
    }

    @Test
    fun createDocumentUsesUserSelectedDestination() {
        val intent = FilePickerIntents.createDocument("output.bin", "application/octet-stream")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("output.bin", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }
}
