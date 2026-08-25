package com.solidlink.platform.transferservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransferServiceStateTest {
    @Test
    fun startIsIdempotentForSameTransferAndRejectsConcurrentTransfer() {
        val coordinator = TransferServiceCoordinator()
        assertEquals(TransferServiceStatus.RUNNING, coordinator.start("transfer-1").status)
        assertEquals("transfer-1", coordinator.start("transfer-1").transferId)
        assertThrows(IllegalStateException::class.java) { coordinator.start("transfer-2") }
    }

    @Test
    fun progressRequiresValidNonNegativeValues() {
        val coordinator = TransferServiceCoordinator()
        coordinator.start("transfer-1")
        assertThrows(IllegalArgumentException::class.java) { coordinator.updateProgress(-1, 10) }
        assertThrows(IllegalArgumentException::class.java) { coordinator.updateProgress(11, 10) }
        assertEquals(4, coordinator.updateProgress(4, 10).bytesCompleted)
    }

    @Test
    fun cancellationPreventsCompletionFromBeingReported() {
        val coordinator = TransferServiceCoordinator()
        coordinator.start("transfer-1")
        assertEquals(TransferServiceStatus.CANCEL_REQUESTED, coordinator.requestCancel().status)
        assertEquals(TransferServiceStatus.CANCEL_REQUESTED, coordinator.complete().status)
    }

    @Test
    fun failureStoresOnlySafeBoundedError() {
        val coordinator = TransferServiceCoordinator()
        coordinator.start("transfer-1")
        val error = "x".repeat(400)
        val state = coordinator.fail(error)
        assertEquals(TransferServiceStatus.FAILED, state.status)
        assertEquals(240, state.safeError?.length)
    }
}
