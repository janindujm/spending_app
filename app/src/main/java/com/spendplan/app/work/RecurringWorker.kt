package com.spendplan.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendplan.app.SpendPlanApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Creates all due recurring transactions (catching up any days the app missed), then
 * re-checks budget alerts. Safe to run any number of times: generation is idempotent.
 */
class RecurringWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendPlanApp).container
        return try {
            container.recurringRepository.generateDue(LocalDate.now())
            container.budgetAlertManager.checkCurrentMonth()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object WorkScheduler {
    private const val DAILY = "recurring-daily"
    private const val NOW = "recurring-now"

    /** Daily background run. KEEP so app restarts don't reset the schedule. */
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One-off run (app start, after saving a recurring transaction). */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RecurringWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
