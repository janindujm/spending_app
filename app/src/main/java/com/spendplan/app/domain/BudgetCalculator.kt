package com.spendplan.app.domain

enum class BudgetStatus {
    /** No budget and nothing spent. */
    NONE,

    /** Under 80% of budget (green). */
    OK,

    /** 80%–100% of budget, inclusive (amber). */
    WARNING,

    /** Over 100% of budget, or spending with no budget (red). */
    OVER,
}

data class CategoryProgress(val categoryId: Long, val budget: Long, val spent: Long) {
    val ratio: Float get() = BudgetCalculator.ratio(spent, budget)
    val status: BudgetStatus get() = BudgetCalculator.status(spent, budget)
    val remaining: Long get() = budget - spent
}

data class SummaryRow(val categoryId: Long, val planned: Long, val actual: Long) {
    /** Positive = under budget (saved), negative = overspent. */
    val difference: Long get() = planned - actual
}

data class MonthSummary(
    val rows: List<SummaryRow>,
    val plannedIncome: Long,
    val actualIncome: Long,
) {
    val totalPlanned: Long get() = rows.sumOf { it.planned }
    val totalActual: Long get() = rows.sumOf { it.actual }
    val totalOver: Long get() = rows.sumOf { maxOf(0L, it.actual - it.planned) }
    val totalUnder: Long get() = rows.sumOf { maxOf(0L, it.planned - it.actual) }

    /** Positive = spent less than planned overall. */
    val netDifference: Long get() = totalPlanned - totalActual

    /** Money actually saved this month. */
    val savings: Long get() = actualIncome - totalActual
    val plannedSavings: Long get() = plannedIncome - totalPlanned
}

object BudgetCalculator {
    const val WARNING_THRESHOLD = 0.8f

    fun ratio(spent: Long, budget: Long): Float = when {
        budget > 0 -> spent.toFloat() / budget.toFloat()
        spent > 0 -> Float.POSITIVE_INFINITY
        else -> 0f
    }

    fun status(spent: Long, budget: Long): BudgetStatus {
        if (budget <= 0) return if (spent > 0) BudgetStatus.OVER else BudgetStatus.NONE
        // Integer arithmetic avoids float rounding at the exact boundaries.
        return when {
            spent * 10 < budget * 8 -> BudgetStatus.OK
            spent <= budget -> BudgetStatus.WARNING
            else -> BudgetStatus.OVER
        }
    }

    /**
     * Alert level used for notifications: 0 (none), 80 (reached 80%) or 100 (reached 100%).
     * Only categories with a budget can raise alerts.
     */
    fun alertLevel(spent: Long, budget: Long): Int = when {
        budget <= 0 -> 0
        spent >= budget -> 100
        spent * 10 >= budget * 8 -> 80
        else -> 0
    }

    /** Expected income minus total planned budgets. Negative means over-allocated. */
    fun unallocated(expectedIncome: Long, budgets: Collection<Long>): Long = expectedIncome - budgets.sum()

    /** Percent of the planned budget used, or null if nothing was planned. */
    fun percentUsed(totalSpent: Long, totalBudget: Long): Float? =
        if (totalBudget <= 0) null else totalSpent * 100f / totalBudget

    fun progress(budgets: Map<Long, Long>, spent: Map<Long, Long>): List<CategoryProgress> =
        (budgets.keys + spent.keys)
            .map { id -> CategoryProgress(id, budgets[id] ?: 0L, spent[id] ?: 0L) }
            .filter { it.budget > 0 || it.spent > 0 }
            .sortedWith(compareByDescending<CategoryProgress> { it.ratio }.thenByDescending { it.spent })

    fun summarize(
        planned: Map<Long, Long>,
        actual: Map<Long, Long>,
        plannedIncome: Long,
        actualIncome: Long,
    ): MonthSummary {
        val rows = (planned.keys + actual.keys)
            .map { SummaryRow(it, planned[it] ?: 0L, actual[it] ?: 0L) }
            .filter { it.planned != 0L || it.actual != 0L }
            .sortedWith(compareByDescending<SummaryRow> { it.actual }.thenByDescending { it.planned })
        return MonthSummary(rows, plannedIncome, actualIncome)
    }

    /** Top N categories by spend, largest first. */
    fun topCategories(spent: Map<Long, Long>, n: Int = 5): List<Pair<Long, Long>> =
        spent.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(n).map { it.key to it.value }
}
