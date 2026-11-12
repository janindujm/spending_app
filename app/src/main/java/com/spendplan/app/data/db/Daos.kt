package com.spendplan.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sort_order, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sort_order, name COLLATE NOCASE")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM categories WHERE type = :type")
    suspend fun maxSortOrder(type: TransactionType): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query(
        """SELECT (SELECT COUNT(*) FROM transactions WHERE category_id = :id)
           + (SELECT COUNT(*) FROM recurring_rules WHERE category_id = :id)
           + (SELECT COUNT(*) FROM planned_items WHERE category_id = :id)""",
    )
    suspend fun usageCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id")
    suspend fun transactionCount(id: Long): Int

    @Query("UPDATE transactions SET category_id = :to WHERE category_id = :from")
    suspend fun moveTransactions(from: Long, to: Long)

    @Query("UPDATE recurring_rules SET category_id = :to WHERE category_id = :from")
    suspend fun moveRules(from: Long, to: Long)

    @Query("UPDATE planned_items SET category_id = :to WHERE category_id = :from")
    suspend fun movePlannedItems(from: Long, to: Long)

    @Query(
        """INSERT OR IGNORE INTO plan_budgets(month, category_id, amount_minor)
           SELECT month, :to, 0 FROM plan_budgets WHERE category_id = :from""",
    )
    suspend fun ensureTargetBudgets(from: Long, to: Long)

    @Query(
        """UPDATE plan_budgets SET amount_minor = amount_minor +
             (SELECT b.amount_minor FROM plan_budgets b
              WHERE b.month = plan_budgets.month AND b.category_id = :from)
           WHERE category_id = :to
             AND month IN (SELECT month FROM plan_budgets WHERE category_id = :from)""",
    )
    suspend fun mergeBudgets(from: Long, to: Long)

    /** Moves everything that references [from] onto [to], then deletes [from]. */
    @Transaction
    suspend fun reassignAndDelete(from: CategoryEntity, to: Long) {
        moveTransactions(from.id, to)
        moveRules(from.id, to)
        movePlannedItems(from.id, to)
        ensureTargetBudgets(from.id, to)
        mergeBudgets(from.id, to)
        delete(from) // cascades remaining budgets + alerts of the old category
    }
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, created_at DESC")
    fun observeInRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end")
    suspend fun getInRange(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY date, created_at")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /** Returns -1 when the (rule, date) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT date FROM transactions WHERE recurring_rule_id = :ruleId AND date BETWEEN :start AND :end")
    suspend fun ruleDatesInRange(ruleId: Long, start: Long, end: Long): List<Long>
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, id DESC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules")
    suspend fun getAll(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE active = 1")
    suspend fun getActive(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Delete
    suspend fun delete(rule: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET last_generated = :epochDay WHERE id = :id")
    suspend fun setLastGenerated(id: Long, epochDay: Long)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE month = :month")
    fun observePlan(month: String): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE month = :month")
    suspend fun getPlan(month: String): PlanEntity?

    @Query("SELECT * FROM plan_budgets WHERE month = :month")
    fun observeBudgets(month: String): Flow<List<PlanBudgetEntity>>

    @Query("SELECT * FROM plan_budgets WHERE month = :month")
    suspend fun getBudgets(month: String): List<PlanBudgetEntity>

    @Upsert
    suspend fun upsertPlan(plan: PlanEntity)

    @Query("DELETE FROM plan_budgets WHERE month = :month")
    suspend fun deleteBudgets(month: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<PlanBudgetEntity>)

    @Transaction
    suspend fun savePlan(plan: PlanEntity, budgets: List<PlanBudgetEntity>) {
        upsertPlan(plan)
        deleteBudgets(plan.month)
        insertBudgets(budgets.filter { it.amountMinor > 0 })
    }

    @Query("SELECT * FROM planned_items WHERE month = :month ORDER BY day_of_month, title")
    fun observePlannedItems(month: String): Flow<List<PlannedItemEntity>>

    @Query("SELECT * FROM planned_items WHERE month = :month ORDER BY day_of_month, title")
    suspend fun getPlannedItems(month: String): List<PlannedItemEntity>

    @Query("SELECT * FROM planned_items WHERE id = :id")
    suspend fun getPlannedItem(id: Long): PlannedItemEntity?

    @Query("SELECT * FROM planned_items WHERE paid_transaction_id = :transactionId")
    suspend fun getPlannedItemsPaidBy(transactionId: Long): List<PlannedItemEntity>

    @Upsert
    suspend fun upsertPlannedItem(item: PlannedItemEntity): Long

    @Delete
    suspend fun deletePlannedItem(item: PlannedItemEntity)

    @Query("SELECT * FROM budget_alerts WHERE month = :month")
    suspend fun getAlerts(month: String): List<BudgetAlertEntity>

    @Upsert
    suspend fun upsertAlert(alert: BudgetAlertEntity)
}

/** Whole-database operations used by JSON backup/restore. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM recurring_rules") suspend fun rules(): List<RecurringRuleEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM plans") suspend fun plans(): List<PlanEntity>
    @Query("SELECT * FROM plan_budgets") suspend fun budgets(): List<PlanBudgetEntity>
    @Query("SELECT * FROM planned_items") suspend fun plannedItems(): List<PlannedItemEntity>
    @Query("SELECT * FROM budget_alerts") suspend fun alerts(): List<BudgetAlertEntity>

    // Children first so foreign keys are never violated.
    @Query("DELETE FROM budget_alerts") suspend fun clearAlerts()
    @Query("DELETE FROM planned_items") suspend fun clearPlannedItems()
    @Query("DELETE FROM plan_budgets") suspend fun clearBudgets()
    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM recurring_rules") suspend fun clearRules()
    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Insert suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert suspend fun insertRules(items: List<RecurringRuleEntity>)
    @Insert suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert suspend fun insertPlans(items: List<PlanEntity>)
    @Insert suspend fun insertBudgets(items: List<PlanBudgetEntity>)
    @Insert suspend fun insertPlannedItems(items: List<PlannedItemEntity>)
    @Insert suspend fun insertAlerts(items: List<BudgetAlertEntity>)

    /** Atomically replaces the whole database; rolls back entirely on any error. */
    @Transaction
    suspend fun replaceAll(
        categories: List<CategoryEntity>,
        rules: List<RecurringRuleEntity>,
        transactions: List<TransactionEntity>,
        plans: List<PlanEntity>,
        budgets: List<PlanBudgetEntity>,
        plannedItems: List<PlannedItemEntity>,
        alerts: List<BudgetAlertEntity>,
    ) {
        clearAlerts()
        clearPlannedItems()
        clearBudgets()
        clearPlans()
        clearTransactions()
        clearRules()
        clearCategories()
        insertCategories(categories)
        insertRules(rules)
        insertTransactions(transactions)
        insertPlans(plans)
        insertBudgets(budgets)
        insertPlannedItems(plannedItems)
        insertAlerts(alerts)
    }
}
