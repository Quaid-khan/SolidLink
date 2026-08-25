package com.solidlink.files

import com.solidlink.domain.DomainError
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile

public class PrivateStagingStore(
    private val rootDirectory: File,
) : StagingStore {
    init {
        require(rootDirectory.isDirectory || rootDirectory.mkdirs()) { "Staging directory could not be created" }
    }

    override fun create(objectId: String, expectedLength: Long?): FileOperationResult<StagingHandle> = try {
        validateObjectId(objectId)
        require(expectedLength == null || expectedLength >= 0) { "expectedLength must be non-negative" }
        val path = dataFile(objectId)
        if (!path.exists() && !path.createNewFile()) {
            return failure("create_staging", "Staging file could not be created")
        }
        FileOperationResult.Success(FileStagingHandle(objectId, path, markerFile(objectId), expectedLength))
    } catch (_: Exception) {
        failure("create_staging", "Private staging could not be created")
    }

    override fun reopen(objectId: String): FileOperationResult<StagingHandle> = try {
        validateObjectId(objectId)
        val path = dataFile(objectId)
        if (!path.isFile) failure("reopen_staging", "Staging file is unavailable")
        else FileOperationResult.Success(FileStagingHandle(objectId, path, markerFile(objectId), null))
    } catch (_: Exception) {
        failure("reopen_staging", "Private staging could not be reopened")
    }

    override fun delete(objectId: String): FileOperationResult<Unit> = try {
        validateObjectId(objectId)
        val dataDeleted = !dataFile(objectId).exists() || dataFile(objectId).delete()
        val markerDeleted = !markerFile(objectId).exists() || markerFile(objectId).delete()
        if (dataDeleted && markerDeleted) FileOperationResult.Success(Unit)
        else failure("delete_staging", "Private staging could not be deleted")
    } catch (_: Exception) {
        failure("delete_staging", "Private staging could not be deleted")
    }

    private fun dataFile(objectId: String): File = File(rootDirectory, "$objectId.part")

    private fun markerFile(objectId: String): File = File(rootDirectory, "$objectId.complete")

    private companion object {
        private val SAFE_ID = Regex("[A-Za-z0-9._-]{1,128}")

        fun validateObjectId(objectId: String) {
            require(SAFE_ID.matches(objectId)) { "objectId is not a safe staging identifier" }
        }

        fun <T> failure(operation: String, message: String): FileOperationResult<T> =
            FileOperationResult.Failure(DomainError.FileAccessFailure(operation, message))
    }

    private class FileStagingHandle(
        override val objectId: String,
        override val path: File,
        private val completionMarker: File,
        override val expectedLength: Long?,
    ) : StagingHandle {
        override fun writeAt(offset: Long, bytes: ByteArray, length: Int): FileOperationResult<Unit> = try {
            require(offset >= 0) { "offset must be non-negative" }
            require(length in 0..bytes.size) { "length must be within the supplied buffer" }
            RandomAccessFile(path, "rw").use { file ->
                file.seek(offset)
                file.write(bytes, 0, length)
                file.fd.sync()
            }
            FileOperationResult.Success(Unit)
        } catch (_: Exception) {
            FileOperationResult.Failure(DomainError.FileAccessFailure("write_staging", "Staging write failed"))
        }

        override fun markVerified(): FileOperationResult<Unit> = try {
            if (expectedLength != null && path.length() != expectedLength) {
                return FileOperationResult.Failure(
                    DomainError.FileAccessFailure("verify_staging", "Staging length does not match the expected length"),
                )
            }
            if (!completionMarker.createNewFile() && !completionMarker.isFile) {
                return FileOperationResult.Failure(
                    DomainError.FileAccessFailure("verify_staging", "Completion marker could not be created"),
                )
            }
            FileOperationResult.Success(Unit)
        } catch (_: Exception) {
            FileOperationResult.Failure(DomainError.FileAccessFailure("verify_staging", "Staging could not be marked complete"))
        }

        override fun isVerified(): Boolean = completionMarker.isFile

        override fun openVerifiedInput(): FileOperationResult<InputStream> = if (isVerified()) {
            try {
                FileOperationResult.Success(FileInputStream(path))
            } catch (_: Exception) {
                FileOperationResult.Failure(DomainError.FileAccessFailure("open_staging", "Verified staging could not be opened"))
            }
        } else {
            FileOperationResult.Failure(DomainError.FileAccessFailure("open_staging", "Staging is not verified"))
        }
    }
}
