package com.solidlink.platform.transferservice

public enum class TransferServiceStatus {
    IDLE,
    RUNNING,
    CANCEL_REQUESTED,
    COMPLETED,
    FAILED,
}

public data class TransferServiceState(
    val status: TransferServiceStatus = TransferServiceStatus.IDLE,
    val transferId: String? = null,
    val bytesCompleted: Long = 0,
    val totalBytes: Long? = null,
    val safeError: String? = null,
)

public class TransferServiceCoordinator {
    private var state: TransferServiceState = TransferServiceState()

    @Synchronized
    public fun snapshot(): TransferServiceState = state

    @Synchronized
    public fun start(transferId: String): TransferServiceState {
        require(transferId.isNotBlank()) { "transferId must not be blank" }
        if (state.status == TransferServiceStatus.RUNNING || state.status == TransferServiceStatus.CANCEL_REQUESTED) {
            if (state.transferId == transferId) return state
            throw IllegalStateException("another transfer is already running")
        }
        state = TransferServiceState(status = TransferServiceStatus.RUNNING, transferId = transferId)
        return state
    }

    @Synchronized
    public fun requestCancel(): TransferServiceState {
        if (state.status == TransferServiceStatus.RUNNING) {
            state = state.copy(status = TransferServiceStatus.CANCEL_REQUESTED)
        }
        return state
    }

    @Synchronized
    public fun updateProgress(bytesCompleted: Long, totalBytes: Long?): TransferServiceState {
        require(bytesCompleted >= 0) { "bytesCompleted must be non-negative" }
        require(totalBytes == null || totalBytes >= bytesCompleted) { "totalBytes must cover progress" }
        if (state.status == TransferServiceStatus.RUNNING || state.status == TransferServiceStatus.CANCEL_REQUESTED) {
            state = state.copy(bytesCompleted = bytesCompleted, totalBytes = totalBytes)
        }
        return state
    }

    @Synchronized
    public fun complete(): TransferServiceState {
        if (state.status != TransferServiceStatus.CANCEL_REQUESTED) {
            state = state.copy(status = TransferServiceStatus.COMPLETED)
        }
        return state
    }

    @Synchronized
    public fun fail(safeError: String): TransferServiceState {
        state = state.copy(status = TransferServiceStatus.FAILED, safeError = safeError.take(240))
        return state
    }
}
