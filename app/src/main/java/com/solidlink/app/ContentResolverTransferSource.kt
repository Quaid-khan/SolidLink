package com.solidlink.app

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.solidlink.transfer.TransferSource
import com.solidlink.transfer.TransferSourceSnapshot
import java.io.InputStream
import java.security.MessageDigest

class ContentResolverTransferSource(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val objectId: String,
    private val batchId: String,
) : TransferSource {

    private val snapshotDetails: TransferSourceSnapshot by lazy {
        computeSnapshot()
    }

    override val snapshot: TransferSourceSnapshot
        get() = snapshotDetails

    override fun open(): InputStream {
        return contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Could not open input stream for URI: $uri")
    }

    private fun computeSnapshot(): TransferSourceSnapshot {
        var displayName = "Unnamed file"
        var sizeBytes: Long? = null

        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = contentResolver.getType(uri)
        val finalDigest = computeDigest()

        return TransferSourceSnapshot(
            objectId = objectId,
            batchId = batchId,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lastModifiedEpochMs = null, // We don't have this readily available without more complex SAF querying
            finalDigest = finalDigest,
            totalChunks = if (sizeBytes != null) {
                (sizeBytes + 256 * 1024 - 1) / (256 * 1024)
            } else null
        )
    }

    private fun computeDigest(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } ?: throw IllegalStateException("Could not read file to compute digest")
        return digest.digest()
    }
}
