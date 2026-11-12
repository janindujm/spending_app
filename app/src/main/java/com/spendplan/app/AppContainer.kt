package com.spendplan.app

import android.content.Context
import com.spendplan.app.data.BackupRepository
import com.spendplan.app.data.CategoryRepository
import com.spendplan.app.data.PlanRepository
import com.spendplan.app.data.RecurringRepository
import com.spendplan.app.data.SettingsRepository
import com.spendplan.app.data.TransactionRepository
import com.spendplan.app.data.db.AppDatabase
import com.spendplan.app.notify.BudgetAlertManager
import com.spendplan.app.notify.BudgetNotifier
import com.spendplan.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container (no DI framework). One instance per process. */
class AppContainer(private val context: Context) {
    /** Lives as long as the process; used for work that must outlive a screen (e.g. alerts). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val settingsRepository by lazy { SettingsRepository(context) }
    val categoryRepository by lazy { CategoryRepository(database) }
    val transactionRepository by lazy { TransactionRepository(database) }
    val planRepository by lazy { PlanRepository(database, transactionRepository) }
    val recurringRepository by lazy { RecurringRepository(database) }
    val backupRepository by lazy { BackupRepository(context, database, settingsRepository) }
    val notifier by lazy { BudgetNotifier(context) }
    val budgetAlertManager by lazy {
        BudgetAlertManager(
            database, settingsRepository, transactionRepository, planRepository, categoryRepository, notifier,
        )
    }

    /** Called after any change that can affect spending vs budget. */
    fun onDataChanged() {
        appScope.launch { runCatching { budgetAlertManager.checkCurrentMonth() } }
    }

    fun runRecurringNow() = WorkScheduler.runNow(context)
}
