package com.spendplan.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        RecurringRuleEntity::class,
        TransactionEntity::class,
        PlanEntity::class,
        PlanBudgetEntity::class,
        PlannedItemEntity::class,
        BudgetAlertEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    // Add AutoMigration(from = N, to = N + 1) entries here for simple schema changes.
    autoMigrations = [],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringDao(): RecurringDao
    abstract fun planDao(): PlanDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "spendplan.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(SeedCallback)
                // Intentionally NO fallbackToDestructiveMigration(): a missing migration
                // must crash in development rather than silently wipe user data.
                .build()
    }
}

/**
 * Migration strategy:
 * 1. Bump [AppDatabase.VERSION] and change the entities.
 * 2. Build once: Room exports the new schema to app/schemas/<version>.json (commit it).
 * 3. Either add an AutoMigration to @Database, or a manual Migration to [ALL].
 * Schemas of every released version stay in app/schemas so migrations can be verified.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf(
        // Example for the future:
        // object : Migration(1, 2) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        //     }
        // },
    )
}

/** Inserts the default categories when the database is first created. */
private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultCategories.all.forEachIndexed { index, c ->
            val values = ContentValues().apply {
                put("name", c.name)
                put("type", c.type.name)
                put("emoji", c.emoji)
                put("color", c.color)
                put("sort_order", index)
            }
            db.insert("categories", SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }
}

object DefaultCategories {
    val all: List<CategoryEntity> = listOf(
        expense("Food", "🍔", 0xFFE57373),
        expense("Transport", "🚌", 0xFF64B5F6),
        expense("Fuel", "⛽", 0xFFFF8A65),
        expense("Bills & Utilities", "💡", 0xFFFFD54F),
        expense("Rent", "🏠", 0xFFA1887F),
        expense("Groceries", "🛒", 0xFF81C784),
        expense("Health", "💊", 0xFFF06292),
        expense("Education", "🎓", 0xFF7986CB),
        expense("Entertainment", "🎬", 0xFFBA68C8),
        expense("Shopping", "🛍️", 0xFF4DB6AC),
        expense("Loan/Lease", "🚗", 0xFF90A4AE),
        expense("Other", "📦", 0xFFBDBDBD),
        income("Salary", "💼", 0xFF43A047),
        income("Freelance", "💻", 0xFF1E88E5),
        income("Hire Driving", "🚕", 0xFFFB8C00),
        income("Other", "💰", 0xFF8D6E63),
    )

    private fun expense(name: String, emoji: String, color: Long) =
        CategoryEntity(name = name, type = TransactionType.EXPENSE, emoji = emoji, color = color.toInt())

    private fun income(name: String, emoji: String, color: Long) =
        CategoryEntity(name = name, type = TransactionType.INCOME, emoji = emoji, color = color.toInt())
}
