package com.spendplan.app.domain

enum class AutoFillSource { LAST_MONTH, THREE_MONTH_AVERAGE }

/** Actual totals of one budget period. */
data class PeriodActuals(
    val income: Long,
    val expenseByCategory: Map<Long, Long>,
    val transactionCount: Int,
) {
    val hasData: Boolean get() = transactionCount > 0
}

data class AutoFillResult(val expectedIncome: Long, val budgets: Map<Long, Long>)

object AutoFill {

    /** (a) Copy last month's actual income and per-category spending. */
    fun fromLastMonth(last: PeriodActuals): AutoFillResult =
        AutoFillResult(
            expectedIncome = last.income,
            budgets = last.expenseByCategory.filterValues { it > 0 },
        )

    /**
     * (b) Average of the given periods (normally the last 3). Only periods that contain
     * at least one transaction count, so a new user with one month of history isn't
     * planned at a third of their real spending. Values are rounded to whole currency units.
     */
    fun fromAverage(periods: List<PeriodActuals>): AutoFillResult {
        val withData = periods.filter { it.hasData }
        if (withData.isEmpty()) return AutoFillResult(0L, emptyMap())
        val n = withData.size.toLong()

        val income = MoneyFormat.roundToWholeUnits(divideHalfUp(withData.sumOf { it.income }, n))
        val categoryIds = withData.flatMap { it.expenseByCategory.keys }.toSet()
        val budgets = categoryIds.associateWith { id ->
            val total = withData.sumOf { it.expenseByCategory[id] ?: 0L }
            MoneyFormat.roundToWholeUnits(divideHalfUp(total, n))
        }.filterValues { it > 0 }
        return AutoFillResult(income, budgets)
    }

    fun compute(source: AutoFillSource, lastThreeNewestFirst: List<PeriodActuals>): AutoFillResult =
        when (source) {
            AutoFillSource.LAST_MONTH ->
                lastThreeNewestFirst.firstOrNull()?.let(::fromLastMonth) ?: AutoFillResult(0L, emptyMap())
            AutoFillSource.THREE_MONTH_AVERAGE -> fromAverage(lastThreeNewestFirst.take(3))
        }

    private fun divideHalfUp(total: Long, n: Long): Long = (total * 2 + n) / (2 * n)
}
