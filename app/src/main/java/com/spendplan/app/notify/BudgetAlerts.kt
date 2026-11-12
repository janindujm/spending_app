package com.spendplan.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spendplan.app.MainActivity
import com.spendplan.app.R
import com.spendplan.app.data.CategoryRepository
import com.spendplan.app.data.PlanRepository
import com.spendplan.app.data.SettingsRepository
import com.spendplan.app.data.TransactionRepository
import com.spendplan.app.data.db.AppDatabase
import com.spendplan.app.data.db.BudgetAlertEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.BudgetCalculator
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.MoneyFormat
import java.time.LocalDate

class BudgetNotifier(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_budget_alerts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_budget_alerts_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyBudget(categoryId: Long, title: String, text: String) {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(categoryId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}

/**
 * Raises a notification the first time a category crosses 80% and 100% of its budget in
 * the current budget month. Levels are remembered per month/category so each threshold
 * notifies once; if spending drops back (edit/delete) the level is lowered again.
 */
class BudgetAlertManager(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val transactions: TransactionRepository,
    private val plans: PlanRepository,
    private val categories: CategoryRepository,
    private val notifier: BudgetNotifier,
) {
    suspend fun checkCurrentMonth(today: LocalDate = LocalDate.now()) {
        val s = settings.current()
        if (!s.notificationsEnabled) return
        val month = BudgetPeriod.current(today, s.monthStartDay)
        val key = BudgetPeriod.key(month)
        val budgets = plans.budgets(month)
        if (budgets.isEmpty()) return

        val spent = transactions.getInRange(BudgetPeriod.range(month, s.monthStartDay))
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        val previous = db.planDao().getAlerts(key).associate { it.categoryId to it.level }
        val names = categories.getAll().associateBy { it.id }

        for ((categoryId, budget) in budgets) {
            val used = spent[categoryId] ?: 0L
            val level = BudgetCalculator.alertLevel(used, budget)
            val before = previous[categoryId] ?: 0
            if (level == before) continue
            db.planDao().upsertAlert(BudgetAlertEntity(key, categoryId, level))
            if (level > before) {
                val category = names[categoryId] ?: continue
                val usedText = MoneyFormat.format(used, s.currency)
                val budgetText = MoneyFormat.format(budget, s.currency)
                val title = if (level >= 100) {
                    "${category.emoji} ${category.name}: budget exceeded"
                } else {
                    "${category.emoji} ${category.name}: 80% of budget used"
                }
                notifier.notifyBudget(categoryId, title, "Spent $usedText of $budgetText this month.")
            }
        }
    }
}
