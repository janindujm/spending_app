package com.spendplan.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.spendplan.app.domain.Frequency
import kotlinx.serialization.Serializable

enum class TransactionType { INCOME, EXPENSE }

@Serializable
@Entity(
    tableName = "categories",
    indices = [Index("type")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val emoji: String,
    @ColumnInfo(name = "color") val color: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

@Serializable
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("category_id")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val type: TransactionType,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val note: String,
    val frequency: Frequency,
    /** 1–31, used when [frequency] is MONTHLY. */
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int,
    /** ISO day of week 1 (Mon) – 7 (Sun), used when WEEKLY. */
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    /** Epoch day of the first occurrence. */
    @ColumnInfo(name = "start_date") val startDate: Long,
    /** Epoch day of the last occurrence turned into a transaction. */
    @ColumnInfo(name = "last_generated") val lastGenerated: Long?,
    val active: Boolean = true,
)

@Serializable
@Entity(
    tableName = "transactions",
    foreignKeys = [
        // A category in use cannot be deleted; transactions must be reassigned first.
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurring_rule_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("date"),
        Index("category_id"),
        // One generated transaction per rule per date: makes catch-up idempotent.
        Index(value = ["recurring_rule_id", "date"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val type: TransactionType,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    /** Epoch day (LocalDate.toEpochDay()). */
    val date: Long,
    val note: String = "",
    @ColumnInfo(name = "recurring_rule_id") val recurringRuleId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Plan header for a budget month ("yyyy-MM"). */
@Serializable
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val month: String,
    @ColumnInfo(name = "expected_income_minor") val expectedIncomeMinor: Long,
)

@Serializable
@Entity(
    tableName = "plan_budgets",
    primaryKeys = ["month", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class PlanBudgetEntity(
    val month: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
)

/** A planned (usually recurring) payment such as "Lease Rs. 45,000 on the 5th". */
@Serializable
@Entity(
    tableName = "planned_items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["paid_transaction_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("month"), Index("category_id"), Index("paid_transaction_id")],
)
data class PlannedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    val title: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int,
    @ColumnInfo(name = "paid_transaction_id") val paidTransactionId: Long? = null,
)

/** Highest budget alert level (80/100) already notified for a category in a month. */
@Serializable
@Entity(
    tableName = "budget_alerts",
    primaryKeys = ["month", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("category_id")],
)
data class BudgetAlertEntity(
    val month: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val level: Int,
)
