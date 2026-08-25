package com.solidlink.platform.transferservice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

public fun interface TransferJob {
    public fun run(control: TransferJobControl): TransferJobOutcome
}

public interface TransferJobControl {
    public val cancellationRequested: Boolean
    public fun reportProgress(bytesCompleted: Long, totalBytes: Long?)
}

public enum class TransferJobOutcome {
    COMPLETED,
    CANCELLED,
    FAILED,
}

public object TransferJobRegistry {
    private val factory = AtomicReference<((String) -> TransferJob)?>(null)

    public fun install(factory: ((String) -> TransferJob)?) {
        this.factory.set(factory)
    }

    internal fun create(transferId: String): TransferJob? = factory.get()?.invoke(transferId)
}

public class SolidLinkTransferService : Service() {
    private val coordinator = TransferServiceCoordinator()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancellationRequested = AtomicBoolean(false)
    private var runningTransferId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancellationRequested.set(true)
                coordinator.requestCancel()
                stopSelfResult(startId)
            }
            ACTION_START, null -> startTransfer(intent?.getStringExtra(EXTRA_TRANSFER_ID))
        }
        return START_NOT_STICKY
    }

    private fun startTransfer(transferId: String?) {
        val id = transferId?.takeIf { it.isNotBlank() } ?: run {
            stopSelf()
            return
        }
        if (runningTransferId == id && coordinator.snapshot().status == TransferServiceStatus.RUNNING) return
        runningTransferId = id
        cancellationRequested.set(false)
        coordinator.start(id)
        startForegroundCompat(buildNotification(coordinator.snapshot()))
        executor.execute {
            val job = TransferJobRegistry.create(id)
            if (job == null) {
                coordinator.fail("Transfer runner is not configured")
                stopAfterTerminal()
                return@execute
            }
            val control = object : TransferJobControl {
                override val cancellationRequested: Boolean
                    get() = this@SolidLinkTransferService.cancellationRequested.get()

                override fun reportProgress(bytesCompleted: Long, totalBytes: Long?) {
                    val state = coordinator.updateProgress(bytesCompleted, totalBytes)
                    updateNotification(state)
                }
            }
            when (job.run(control)) {
                TransferJobOutcome.COMPLETED -> {
                    val state = coordinator.complete()
                    updateNotification(state)
                }
                TransferJobOutcome.CANCELLED -> {
                    coordinator.requestCancel()
                    updateNotification(coordinator.snapshot())
                }
                TransferJobOutcome.FAILED -> {
                    val state = coordinator.fail("Transfer failed; resume is available from history")
                    updateNotification(state)
                }
            }
            stopAfterTerminal()
        }
    }

    private fun stopAfterTerminal() {
        runningTransferId = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: TransferServiceState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TransferServiceState): Notification {
        val cancelIntent = Intent(this, SolidLinkTransferService::class.java).setAction(ACTION_CANCEL)
        val cancelPendingIntent = PendingIntent.getService(
            this,
            100,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progressText = when (state.status) {
            TransferServiceStatus.RUNNING, TransferServiceStatus.CANCEL_REQUESTED ->
                state.totalBytes?.let { "${state.bytesCompleted} / $it bytes" } ?: "Transferring"
            TransferServiceStatus.COMPLETED -> "Transfer completed"
            TransferServiceStatus.FAILED -> state.safeError ?: "Transfer failed"
            TransferServiceStatus.IDLE -> "Preparing transfer"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("SolidLink")
            .setContentText(progressText)
            .setOngoing(state.status == TransferServiceStatus.RUNNING || state.status == TransferServiceStatus.CANCEL_REQUESTED)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Active transfers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Visible progress for user-started local transfers"
            },
        )
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        cancellationRequested.set(true)
        executor.shutdownNow()
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    public companion object {
        public const val ACTION_START: String = "com.solidlink.action.START_TRANSFER"
        public const val ACTION_CANCEL: String = "com.solidlink.action.CANCEL_TRANSFER"
        public const val EXTRA_TRANSFER_ID: String = "com.solidlink.extra.TRANSFER_ID"
        private const val CHANNEL_ID: String = "solidlink_active_transfers"
        private const val NOTIFICATION_ID: Int = 4101
    }
}
