package com.example.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.OutboxKind
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object OutboxWorkScheduler {
    fun enqueue(
        context: Context,
        kind: OutboxKind,
        itemId: String,
        ownerId: String,
        tenantUrl: String,
        replace: Boolean = false
    ) {
        val request = OneTimeWorkRequestBuilder<OutboxDeliveryWorker>()
            .setInputData(
                Data.Builder()
                    .putString(OutboxDeliveryWorker.KEY_ITEM_ID, itemId)
                    .putString(OutboxDeliveryWorker.KEY_KIND, kind.value)
                    .putString(OutboxDeliveryWorker.KEY_OWNER_ID, ownerId)
                    .putString(OutboxDeliveryWorker.KEY_TENANT_URL, tenantUrl)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(kind, itemId, ownerId, tenantUrl),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, kind: OutboxKind, itemId: String, ownerId: String, tenantUrl: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(workName(kind, itemId, ownerId, tenantUrl))
    }

    fun workName(kind: OutboxKind, itemId: String, ownerId: String, tenantUrl: String): String {
        val scope = "$ownerId|$tenantUrl|${kind.value}|$itemId"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "outbox-$digest"
    }
}
