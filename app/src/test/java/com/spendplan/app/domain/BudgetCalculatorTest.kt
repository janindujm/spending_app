package com.spendplan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BudgetCalculatorTest {

    @Test
    fun `status boundaries - green under 80, amber 80 to 100 inclusive, red over 100`() {
        assertEquals(BudgetStatus.OK, BudgetCalculator.status(spent = 0, budget = 10_000))
        assertEquals(BudgetStatus.OK, BudgetCalculator.status(spent = 7_999, budget = 10_000))
        assertEquals(BudgetStatus.WARNING, BudgetCalculator.status(spent = 8_000, budget = 10_000))
        assertEquals(BudgetStatus.WARNING, BudgetCalculator.status(spent = 10_000, budget = 10_000))
        assertEquals(BudgetStatus.OVER, BudgetCalculator.status(spent = 10_001, budget = 10_000))
    }

    @Test
    fun `no budget - spending is over, nothing is none`() {
        assertEquals(BudgetStatus.OVER, BudgetCalculator.status(spent = 1, budget = 0))
        assertEquals(BudgetStatus.NONE, BudgetCalculator.status(spent = 0, budget = 0))
    }

    @Test
    fun `alert level thresholds`() {
        assertEquals(0, BudgetCalculator.alertLevel(7_999, 10_000))
        assertEquals(80, BudgetCalculator.alertLevel(8_000, 10_000))
        assertEquals(80, BudgetCalculator.alertLevel(9_999, 10_000))
        assertEquals(100, BudgetCalculator.alertLevel(10_000, 10_000))
        assertEquals(100, BudgetCalculator.alertLevel(50_000, 10_000))
        assertEquals(0, BudgetCalculator.alertLevel(50_000, 0))
    }

    @Test
    fun `unallocated is income minus planned and can be negative`() {
        assertEquals(30_000L, BudgetCalculator.unallocated(100_000, listOf(50_000, 20_000)))
        assertEquals(-5_000L, BudgetCalculator.unallocated(100_000, listOf(60_000, 45_000)))
        assertEquals(100_000L, BudgetCalculator.unallocated(100_000, emptyList()))
    }

    @Test
    fun `percent used`() {
        assertEquals(50f, BudgetCalculator.percentUsed(5_000, 10_000)!!, 0.001f)
        assertEquals(125f, BudgetCalculator.percentUsed(12_500, 10_000)!!, 0.001f)
        assertNull(BudgetCalculator.percentUsed(5_000, 0))
    }

    @Test
    fun `progress includes unbudgeted spending and sorts by ratio`() {
        val progress = BudgetCalculator.progress(
            budgets = mapOf(1L to 10_000L, 2L to 10_000L, 3L to 5_000L),
            spent = mapOf(1L to 2_000L, 2L to 9_000L, 4L to 100L),
        )
        // 4 has no budget (infinite ratio) -> first; 3 has budget but no spend -> last.
        assertEquals(listOf(4L, 2L, 1L, 3L), progress.map { it.categoryId })
        assertEquals(8_000L, progress.first { it.categoryId == 1L }.remaining)
    }

    @Test
    fun `month summary totals over under and savings`() {
        val summary = BudgetCalculator.summarize(
            planned = mapOf(1L to 20_000L, 2L to 10_000L, 3L to 5_000L),
            actual = mapOf(1L to 25_000L, 2L to 4_000L, 4L to 1_000L),
            plannedIncome = 50_000L,
            actualIncome = 52_000L,
        )
        assertEquals(35_000L, summary.totalPlanned)
        assertEquals(30_000L, summary.totalActual)
        assertEquals(6_000L, summary.totalOver) // 5,000 (cat 1) + 1,000 (cat 4, unplanned)
        assertEquals(11_000L, summary.totalUnder) // 6,000 (cat 2) + 5,000 (cat 3)
        assertEquals(5_000L, summary.netDifference)
        assertEquals(22_000L, summary.savings)
        assertEquals(15_000L, summary.plannedSavings)
        assertEquals(listOf(1L, 2L, 4L, 3L), summary.rows.map { it.categoryId })
        assertEquals(-5_000L, summary.rows.first { it.categoryId == 1L }.difference)
    }

    @Test
    fun `top categories returns at most five, largest first`() {
        val spent = (1L..8L).associateWith { it * 1_000 } + (9L to 0L)
        val top = BudgetCalculator.topCategories(spent)
        assertEquals(listOf(8L, 7L, 6L, 5L, 4L), top.map { it.first })
    }
}
