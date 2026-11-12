package com.spendplan.app.data

import androidx.room.withTransaction
import com.spendplan.app.data.db.AppDatabase
import com.spendplan.app.data.db.CategoryEntity
import com.spendplan.app.data.db.PlanBudgetEntity
import com.spendplan.app.data.db.PlanEntity
import com.spendplan.app.data.db.PlannedItemEntity
import com.spendplan.app.data.db.RecurringRuleEntity
import com.spendplan.app.data.db.TransactionEntity
import com.spendplan.app.data.db.TransactionType
import com.spendplan.app.domain.AutoFill
import com.spendplan.app.domain.AutoFillResult
import com.spendplan.app.domain.AutoFillSource
import com.spendplan.app.domain.BudgetPeriod
import com.spendplan.app.domain.DateRange
import com.spendplan.app.domain.Frequency
import com.spendplan.app.domain.PeriodActuals
import com.spendplan.app.domain.RecurrenceScheduler
import com.spendplan.app.domain.RecurrenceSpec
import com.spendplan.app.domain.RecurringGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// ---------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------

sealed interface CategoryDeleteResult {
    data object Deleted : CategoryDeleteResult
    data class InUse(val usageCount: Int) : CategoryDeleteResult
}

class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categoryDao()

    val categories: Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun getAll(): List<CategoryEntity> = dao.getAll()

    /** Returns an error message, or null when the name is valid and unique for its type. */
    suspend fun validateName(name: String, type: TransactionType, exceptId: Long? = null): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name is required"
        if (trimmed.length > 40) return "Name is too long"
        val clash = dao.getAll().any { it.type == type && it.id != exceptId && it.name.equals(trimmed, ignoreCase = true) }
        return if (clash) "A category with this name already exists" else null
    }

    suspend fun add(name: String, type: TransactionType, emoji: String, color: Int): Long =
        dao.insert(
            CategoryEntity(
                name = name.trim(),
                type = type,
                emoji = emoji,
                color = color,
                sortOrder = dao.maxSortOrder(type) + 1,
            ),
        )

    suspend fun update(category: CategoryEntity) = dao.update(category.copy(name = category.name.trim()))

    suspend fun delete(category: CategoryEntity): CategoryDeleteResult {
        val usage = dao.usageCount(category.id)
        if (usage > 0) return CategoryDeleteResult.InUse(usage)
        dao.delete(category)
        return CategoryDeleteResult.Deleted
    }

    suspend fun reassignAndDelete(category: CategoryEntity, targetId: Long) {
        require(targetId != category.id) { "Target must be a different category" }
        dao.reassignAndDelete(category, targetId)
    }
}

// ---------------------------------------------------------------------------
// Transactions
// ---------------------------------------------------------------------------

/** Snapshot kept so a delete can be undone (including planned-item "paid" links). */
data class DeletedTransaction(val transaction: TransactionEntity, val paidPlannedItems: List<PlannedItemEntity>)

class TransactionRepository(private val db: AppDatabase) {
    private val txDao = db.transactionDao()
    private val ruleDao = db.recurringDao()
    private val planDao = db.planDao()

    fun observeRange(range: DateRange): Flow<List<TransactionEntity>> =
        txDao.observeInRange(range.startEpochDay, range.endEpochDay)

    suspend fun getInRange(range: DateRange): List<TransactionEntity> =
        txDao.getInRange(range.startEpochDay, range.endEpochDay)

    suspend fun getAll(): List<TransactionEntity> = txDao.getAll()

    suspend fun get(id: Long): TransactionEntity? = txDao.getById(id)

    suspend fun getRule(id: Long?): RecurringRuleEntity? = id?.let { ruleDao.getById(it) }

    /**
     * Saves a transaction. [repeat] = null means "not recurring". When a repeat is set,
     * the transaction itself is the first occurrence (rule.lastGenerated = its date), so
     * it is never duplicated by the worker. Returns the transaction id.
     */
    suspend fun save(transaction: TransactionEntity, repeat: Frequency?): Long = db.withTransaction {
        val date = LocalDate.ofEpochDay(transaction.date)
        val existingRule = transaction.recurringRuleId?.let { ruleDao.getById(it) }
        var ruleId: Long? = transaction.recurringRuleId

        if (repeat == null) {
            if (existingRule != null) {
                // Stop the series; past transactions keep their data (FK -> SET NULL).
                ruleDao.delete(existingRule)
                ruleId = null
            }
        } else if (existingRule != null) {
            ruleDao.update(
                existingRule.copy(
                    amountMinor = transaction.amountMinor,
                    type = transaction.type,
                    categoryId = transaction.categoryId,
                    note = transaction.note,
                    frequency = repeat,
                    dayOfMonth = date.dayOfMonth,
                    dayOfWeek = date.dayOfWeek.value,
                    active = true,
                ),
            )
        } else {
            ruleId = ruleDao.insert(
                RecurringRuleEntity(
                    amountMinor = transaction.amountMinor,
                    type = transaction.type,
                    categoryId = transaction.categoryId,
                    note = transaction.note,
                    frequency = repeat,
                    dayOfMonth = date.dayOfMonth,
                    dayOfWeek = date.dayOfWeek.value,
                    startDate = transaction.date,
                    lastGenerated = transaction.date,
                ),
            )
        }

        val toSave = transaction.copy(recurringRuleId = ruleId)
        if (toSave.id == 0L) {
            txDao.insert(toSave)
        } else {
            txDao.update(toSave)
            toSave.id
        }
    }

    suspend fun delete(transaction: TransactionEntity): DeletedTransaction = db.withTransaction {
        val links = planDao.getPlannedItemsPaidBy(transaction.id)
        txDao.delete(transaction)
        DeletedTransaction(transaction, links)
    }

    suspend fun restore(deleted: DeletedTransaction) = db.withTransaction {
        // The rule may have been deleted in the meantime; drop the link rather than fail.
        val ruleId = deleted.transaction.recurringRuleId?.takeIf { ruleDao.getById(it) != null }
        txDao.insertIgnore(deleted.transaction.copy(recurringRuleId = ruleId))
        deleted.paidPlannedItems.forEach { item ->
            planDao.getPlannedItem(item.id)?.let { planDao.upsertPlannedItem(it.copy(paidTransactionId = deleted.transaction.id)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Plans
// ---------------------------------------------------------------------------

data class MonthPlan(
    val month: YearMonth,
    val expectedIncome: Long,
    val budgets: Map<Long, Long>,
    val plannedItems: List<PlannedItemEntity>,
    val exists: Boolean,
)

class PlanRepository(
    private val db: AppDatabase,
    private val transactions: TransactionRepository,
) {
    private val dao = db.planDao()

    fun observe(month: YearMonth): Flow<MonthPlan> {
        val key = BudgetPeriod.key(month)
        return combine(dao.observePlan(key), dao.observeBudgets(key), dao.observePlannedItems(key)) { plan, budgets, items ->
            MonthPlan(
                month = month,
                expectedIncome = plan?.expectedIncomeMinor ?: 0L,
                budgets = budgets.associate { it.categoryId to it.amountMinor },
                plannedItems = items,
                exists = plan != null || budgets.isNotEmpty(),
            )
        }
    }

    suspend fun budgets(month: YearMonth): Map<Long, Long> =
        dao.getBudgets(BudgetPeriod.key(month)).associate { it.categoryId to it.amountMinor }

    suspend fun save(month: YearMonth, expectedIncome: Long, budgets: Map<Long, Long>) {
        val key = BudgetPeriod.key(month)
        dao.savePlan(
            PlanEntity(key, expectedIncome),
            budgets.map { (cat, amount) -> PlanBudgetEntity(key, cat, amount) },
        )
    }

    /** Actual totals for the [count] budget months before [month], newest first. */
    suspend fun previousActuals(month: YearMonth, startDay: Int, count: Int = 3): List<PeriodActuals> =
        (1..count).map { offset ->
            val list = transactions.getInRange(BudgetPeriod.range(month.minusMonths(offset.toLong()), startDay))
            PeriodActuals(
                income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor },
                expenseByCategory = list.filter { it.type == TransactionType.EXPENSE }
                    .groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amountMinor } },
                transactionCount = list.size,
            )
        }

    suspend fun autoFill(month: YearMonth, startDay: Int, source: AutoFillSource): AutoFillResult =
        AutoFill.compute(source, previousActuals(month, startDay))

    /** Copies last month's planned items (unpaid) into [month]; skips ones already present. */
    suspend fun copyPlannedItemsFromPrevious(month: YearMonth): Int = db.withTransaction {
        val key = BudgetPeriod.key(month)
        val existing = dao.getPlannedItems(key).map { it.title.lowercase() to it.dayOfMonth }.toSet()
        val toCopy = dao.getPlannedItems(BudgetPeriod.key(month.minusMonths(1)))
            .filter { (it.title.lowercase() to it.dayOfMonth) !in existing }
        toCopy.forEach { dao.upsertPlannedItem(it.copy(id = 0, month = key, paidTransactionId = null)) }
        toCopy.size
    }

    suspend fun savePlannedItem(item: PlannedItemEntity) {
        dao.upsertPlannedItem(item)
    }

    suspend fun deletePlannedItem(item: PlannedItemEntity) = db.withTransaction {
        dao.deletePlannedItem(item)
    }

    /**
     * Marks a planned item as paid by creating a real expense transaction. The date is the
     * planned day in that budget month, or today if that day is still in the future.
     */
    suspend fun markPaid(item: PlannedItemEntity, today: LocalDate, startDay: Int): Long = db.withTransaction {
        val fresh = dao.getPlannedItem(item.id) ?: return@withTransaction -1L
        fresh.paidTransactionId?.let { return@withTransaction it }
        val month = BudgetPeriod.parseKey(fresh.month)
        val range = BudgetPeriod.range(month, startDay)
        var planned = RecurrenceScheduler.monthlyDate(month, fresh.dayOfMonth)
        if (planned.isBefore(range.start)) planned = RecurrenceScheduler.monthlyDate(month.plusMonths(1), fresh.dayOfMonth)
        val date = if (planned.isAfter(today)) today else planned
        val txId = db.transactionDao().insert(
            TransactionEntity(
                amountMinor = fresh.amountMinor,
                type = TransactionType.EXPENSE,
                categoryId = fresh.categoryId,
                date = date.toEpochDay(),
                note = fresh.title,
            ),
        )
        dao.upsertPlannedItem(fresh.copy(paidTransactionId = txId))
        txId
    }

    /** Undo "paid": removes the generated transaction. */
    suspend fun markUnpaid(item: PlannedItemEntity) = db.withTransaction {
        val fresh = dao.getPlannedItem(item.id) ?: return@withTransaction
        fresh.paidTransactionId?.let { id -> db.transactionDao().getById(id)?.let { db.transactionDao().delete(it) } }
        dao.upsertPlannedItem(fresh.copy(paidTransactionId = null))
    }
}

// ---------------------------------------------------------------------------
// Recurring rules
// ---------------------------------------------------------------------------

fun RecurringRuleEntity.toSpec(): RecurrenceSpec = RecurrenceSpec(
    frequency = frequency,
    dayOfMonth = dayOfMonth,
    dayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
    startDate = LocalDate.ofEpochDay(startDate),
    lastGenerated = lastGenerated?.let(LocalDate::ofEpochDay),
)

class RecurringRepository(private val db: AppDatabase) {
    private val ruleDao = db.recurringDao()
    private val txDao = db.transactionDao()

    val rules: Flow<List<RecurringRuleEntity>> = ruleDao.observeAll()

    suspend fun setActive(rule: RecurringRuleEntity, active: Boolean) = ruleDao.update(rule.copy(active = active))

    suspend fun delete(rule: RecurringRuleEntity) = ruleDao.delete(rule)

    /**
     * Creates every due-but-missing transaction for all active rules, catching up across
     * any number of missed days. Idempotent: the high-water mark (lastGenerated) and the
     * unique (rule, date) index both prevent duplicates, even if two runs overlap.
     * Returns the dates (per created transaction) so callers can re-check budgets.
     */
    suspend fun generateDue(today: LocalDate): List<LocalDate> {
        val created = mutableListOf<LocalDate>()
        for (rule in ruleDao.getActive()) {
            val spec = rule.toSpec()
            val due = RecurrenceScheduler.dueDates(spec, today)
            if (due.isEmpty()) continue
            db.withTransaction {
                val existing = txDao.ruleDatesInRange(rule.id, due.first().toEpochDay(), due.last().toEpochDay())
                    .map(LocalDate::ofEpochDay).toSet()
                val plan = RecurringGenerator.plan(spec, today, existing)
                plan.datesToCreate.forEach { date ->
                    val id = txDao.insertIgnore(
                        TransactionEntity(
                            amountMinor = rule.amountMinor,
                            type = rule.type,
                            categoryId = rule.categoryId,
                            date = date.toEpochDay(),
                            note = rule.note,
                            recurringRuleId = rule.id,
                        ),
                    )
                    if (id != -1L) created += date
                }
                plan.newLastGenerated?.let { ruleDao.setLastGenerated(rule.id, it.toEpochDay()) }
            }
        }
        return created
    }
}
