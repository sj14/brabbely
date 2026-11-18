package io.github.sj14.lauscher.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.sj14.lauscher.LauscherApp

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LauscherApp).container
        container.repository.refreshAll()
        return Result.success()
    }

    companion object {
        const val NAME = "refresh"
        const val NAME_NOW = "refresh-now"
    }
}
