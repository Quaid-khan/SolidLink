package com.solidlink.files

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.solidlink.domain.DomainError
import com.solidlink.domain.TransitionResult

public data class SourceDescriptor(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModifiedEpochMs: Long?,
    val persistableReadGrant: Boolean,
)

public data class PersistedUriGrant(
    val uri: Uri,
    val flags: Int,
)

public sealed interface FileOperationResult<out T> {
    public data class Success<T>(val value: T) : FileOperationResult<T>

    public data class Failure(val error: DomainError.FileAccessFailure) : FileOperationResult<Nothing>
}

public object FilePickerIntents {
    public fun openDocuments(mimeType: String = "*/*"): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeType
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    public fun createDocument(displayName: String, mimeType: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, displayName)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    public fun openPhotoPicker(maxItems: Int = 50): ActivityResultContracts.PickMultipleVisualMedia =
        ActivityResultContracts.PickMultipleVisualMedia(maxItems)
}

public interface SourceReader {
    public fun describe(uri: Uri): FileOperationResult<SourceDescriptor>

    public fun open(uri: Uri): FileOperationResult<java.io.InputStream>
}

public interface StagingStore {
    public fun create(objectId: String, expectedLength: Long?): FileOperationResult<StagingHandle>

    public fun reopen(objectId: String): FileOperationResult<StagingHandle>

    public fun delete(objectId: String): FileOperationResult<Unit>
}

public interface StagingHandle {
    public val objectId: String
    public val path: java.io.File
    public val expectedLength: Long?

    public fun writeAt(offset: Long, bytes: ByteArray, length: Int): FileOperationResult<Unit>

    public fun markVerified(): FileOperationResult<Unit>

    public fun isVerified(): Boolean

    public fun openVerifiedInput(): FileOperationResult<java.io.InputStream>
}

public fun <T> FileOperationResult<T>.asDomainResult(): TransitionResult<T> = when (this) {
    is FileOperationResult.Success -> TransitionResult.Success(value)
    is FileOperationResult.Failure -> TransitionResult.Failure(error)
}
