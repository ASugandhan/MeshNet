package com.meshnet.app.routing

import android.content.Context
import androidx.work.*
import com.meshnet.app.data.MeshNetDatabase
import java.util.concurrent.TimeUnit

/**
 * WorkManager background job to clean expired packets.
 * Implementation of Section 12.
 */
class PacketExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = MeshNetDatabase.getDatabase(applicationContext)
        db.messageQueueDao().deleteExpiredPackets(System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<PacketExpiryWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "packet_expiry_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
