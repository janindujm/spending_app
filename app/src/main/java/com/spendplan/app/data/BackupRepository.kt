package com.spendplan.app.data

import android.content.Context
import android.net.Uri
import com.spendplan.app.data.db.AppDatabase
import com.spendplan.app.data.db.BudgetAlertEntity
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.PlanBudgetEntity
import com.spendplan.app.data.db.PlanEntity
import com.spendplan.app.data.db.PlannedItemEntity
import com.spendplan.app.data.db.RecurringRuleEntity
import com.spendplan.app.data.db.TransactionEntity
import com.spendplan.app.domain.CsvFormatter
import com.spendplan.app.domain.CsvRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

@Serializable
data class BackupSettings(val currencyCode: String, val monthStartDay: Int)

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val formatVersion: Int = FORMAT_VERSION,
    val schemaVersion: Int = AppDatabase.VERSION,
    val exportedAt: String,
    val settings: BackupSettings? = null,
    val categories: List<CategoryEntity>,
    val recurringRules: List<RecurringRuleEntity> = emptyList(),
    val transactions: List<TransactionEntity>,
    val plans: List<PlanEntity> = emptyList(),
    val planBudgets: List<PlanBudgetEntity> = emptyList(),
    val plannedItems: List<PlannedItemEntity> = emptyList(),
    val budgetAlerts: List<BudgetAlertEntity> = emptyList(),
) {
    companion object {
        const val APP_ID = "SpendPlan"
        const val FORMAT_VERSION = 1
    }
}

data class RestoreSummary(val categories: Int, val transactions: Int, val plans: Int)

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Writes all transactions as CSV (UTF-8 with BOM so Excel detects the encoding). */
    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val categories = db.categoryDao().getAll().associateBy { it.id }
        val transactions = db.transactionDao().getAll()
        val rows = transactions.map { t ->
            CsvRow(
                date = LocalDate.ofEpochDay(t.date),
                type = t.type.name.lowercase().replaceFirstChar { it.uppercase() },
                category = categories[t.categoryId]?.name ?: "",
                amountMinor = t.amountMinor,
                note = t.note,
            )
        }
        val csv = CsvFormatter.format(rows, settings.current().currencyCode)
        writeText(uri, "﻿" + csv)
        rows.size
    }

    suspend fun backup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = db.backupDao()
        val s = settings.current()
        val file = BackupFile(
            exportedAt = Instant.now().toString(),
            settings = BackupSettings(s.currencyCode, s.monthStartDay),
            categories = dao.categories(),
            recurringRules = dao.rules(),
            transactions = dao.transactions(),
            plans = dao.plans(),
            planBudgets = dao.budgets(),
            plannedItems = dao.plannedItems(),
            budgetAlerts = dao.alerts(),
        )
        writeText(uri, json.encodeToString(BackupFile.serializer(), file))
        file.transactions.size
    }

    /** Replaces ALL local data with the backup. Throws [IllegalArgumentException] if invalid. */
    suspend fun restore(uri: Uri): RestoreSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Could not open the selected file")
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text.removePrefix("﻿"))
        } catch (e: Exception) {
            throw IllegalArgumentException("This is not a valid SpendPlan backup file", e)
        }
        require(file.app == BackupFile.APP_ID) { "This is not a SpendPlan backup file" }
        require(file.formatVersion <= BackupFile.FORMAT_VERSION) {
            "This backup was made by a newer version of SpendPlan. Please update the app."
        }

        db.backupDao().replaceAll(
            categories = file.categories,
            rules = file.recurringRules,
            transactions = file.transactions,
            plans = file.plans,
            budgets = file.planBudgets,
            plannedItems = file.plannedItems,
            alerts = file.budgetAlerts,
        )
        file.settings?.let {
            settings.setCurrency(it.currencyCode)
            settings.setMonthStartDay(it.monthStartDay)
        }
        RestoreSummary(file.categories.size, file.transactions.size, file.plans.size)
    }

    private fun writeText(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Could not open the selected file for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
