package com.solidlink.files

import android.content.ContentResolver
import android.net.Uri
import com.solidlink.domain.DomainError
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

public class SafExportWriter(
    private val contentResolver: ContentResolver,
) {
    public fun export(staging: StagingHandle, destination: Uri): FileOperationResult<Long> {
        val inputResult = staging.openVerifiedInput()
        val input = when (inputResult) {
            is FileOperationResult.Success -> inputResult.value
            is FileOperationResult.Failure -> return FileOperationResult.Failure(inputResult.error)
        }

        return try {
            val output = contentResolver.openOutputStream(destination, "w")
                ?: return failure("export", "The destination provider could not be opened")
            var total = 0L
            BufferedInputStream(input).use { inputStream ->
                BufferedOutputStream(output).use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = inputStream.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        total += count
                    }
                    sink.flush()
                }
            }
            FileOperationResult.Success(total)
        } catch (_: SecurityException) {
            failure("export", "The destination is no longer accessible")
        } catch (_: Exception) {
            failure("export", "The verified file could not be exported")
        }
    }

    private companion object {
        fun <T> failure(operation: String, message: String): FileOperationResult<T> =
            FileOperationResult.Failure(DomainError.FileAccessFailure(operation, message))
    }
}
