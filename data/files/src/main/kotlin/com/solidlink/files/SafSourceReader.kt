package com.solidlink.files

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.InputStream

public class SafSourceReader(
    private val contentResolver: ContentResolver,
) : SourceReader {
    override fun describe(uri: Uri): FileOperationResult<SourceDescriptor> = try {
        val metadata = contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS,
            ),
            null,
            null,
            null,
        )?.use { cursor -> readSourceMetadata(cursor) }

        if (metadata == null) {
            failure("describe", "Document metadata is unavailable")
        } else {
            FileOperationResult.Success(
                SourceDescriptor(
                    uri = uri,
                    displayName = DisplayNameSanitizer.forDisplay(metadata.displayName),
                    mimeType = contentResolver.getType(uri),
                    sizeBytes = metadata.sizeBytes,
                    lastModifiedEpochMs = metadata.lastModifiedEpochMs,
                    persistableReadGrant = contentResolver.persistedUriPermissions.any { permission ->
                        permission.uri == uri && permission.isReadPermission
                    },
                ),
            )
        }
    } catch (_: SecurityException) {
        failure("describe", "The selected document is no longer accessible")
    } catch (_: Exception) {
        failure("describe", "Document metadata could not be read")
    }

    override fun open(uri: Uri): FileOperationResult<InputStream> = try {
        val input = contentResolver.openInputStream(uri)
        if (input == null) failure("open", "The selected document could not be opened")
        else FileOperationResult.Success(input)
    } catch (_: SecurityException) {
        failure("open", "The selected document is no longer accessible")
    } catch (_: Exception) {
        failure("open", "The selected document could not be opened")
    }

    private data class SourceMetadata(
        val displayName: String,
        val sizeBytes: Long?,
        val lastModifiedEpochMs: Long?,
    )

    private companion object {
        fun readSourceMetadata(cursor: Cursor): SourceMetadata? {
            if (!cursor.moveToFirst()) return null
            val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val displayName = if (displayNameIndex >= 0 && !cursor.isNull(displayNameIndex)) cursor.getString(displayNameIndex) else "Unnamed file"
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
            val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else null
            return SourceMetadata(displayName, size, modified)
        }

        fun <T> failure(operation: String, message: String): FileOperationResult<T> =
            FileOperationResult.Failure(
                com.solidlink.domain.DomainError.FileAccessFailure(operation, message),
            )
    }
}

public object DisplayNameSanitizer {
    public fun forDisplay(raw: String?): String {
        val normalized = raw.orEmpty()
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_")
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
        return normalized.ifEmpty { "Unnamed file" }.take(240)
    }

    public fun forOutput(raw: String?): String {
        val display = forDisplay(raw)
        return if (display == "." || display == "..") "Unnamed file" else display
    }
}
