package com.spendplan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFillTest {
    private fun period(income: Long, vararg expenses: Pair<Long, Long>, count: Int = 1) =
        PeriodActuals(income, expenses.toMap(), count)

    @Test
    fun `last month copies actual income and spending exactly`() {
        val last = period(150_000_00, 1L to 25_000_50, 2L to 8_000_00, 3L to 0L)
        val result = AutoFill.compute(AutoFillSource.LAST_MONTH, listOf(last, period(1, count = 1)))
        assertEquals(150_000_00L, result.expectedIncome)
        assertEquals(mapOf(1L to 25_000_50L, 2L to 8_000_00L), result.budgets) // zero dropped
    }

    @Test
    fun `last month with no history is empty`() {
        val result = AutoFill.compute(AutoFillSource.LAST_MONTH, emptyList())
        assertEquals(0L, result.expectedIncome)
        assertTrue(result.budgets.isEmpty())
    }

    @Test
    fun `three month average divides by three and rounds to whole units`() {
        val result = AutoFill.compute(
            AutoFillSource.THREE_MONTH_AVERAGE,
            listOf(
                period(100_000_00, 1L to 10_000_00, 2L to 3_000_00),
                period(110_000_00, 1L to 20_000_00),
                period(120_000_00, 1L to 30_000_00, 2L to 3_000_00),
            ),
        )
        assertEquals(110_000_00L, result.expectedIncome)
        assertEquals(20_000_00L, result.budgets[1L])
        // (3000 + 0 + 3000) / 3 = 2000
        assertEquals(2_000_00L, result.budgets[2L])
    }

    @Test
    fun `average rounds half up to whole currency units`() {
        // (100 + 100 + 101) / 3 = 100.333 -> 100 ; (1 + 2 + 2)/3 = 1.67 -> 2
        val result = AutoFill.fromAverage(
            listOf(
                period(0, 1L to 100_00, 2L to 1_00),
                period(0, 1L to 100_00, 2L to 2_00),
                period(0, 1L to 101_00, 2L to 2_00),
            ),
        )
        assertEquals(100_00L, result.budgets[1L])
        assertEquals(2_00L, result.budgets[2L])
    }

    @Test
    fun `average ignores months without any transactions`() {
        val result = AutoFill.compute(
            AutoFillSource.THREE_MONTH_AVERAGE,
            listOf(
                period(90_000_00, 1L to 12_000_00),
                PeriodActuals(0, emptyMap(), transactionCount = 0),
                PeriodActuals(0, emptyMap(), transactionCount = 0),
            ),
        )
        assertEquals(90_000_00L, result.expectedIncome)
        assertEquals(12_000_00L, result.budgets[1L])
    }

    @Test
    fun `average only uses the newest three periods`() {
        val result = AutoFill.compute(
            AutoFillSource.THREE_MONTH_AVERAGE,
            listOf(period(3_00), period(3_00), period(3_00), period(999_00)),
        )
        assertEquals(3_00L, result.expectedIncome)
    }

    @Test
    fun `average with no data at all is empty`() {
        val result = AutoFill.fromAverage(List(3) { PeriodActuals(0, emptyMap(), 0) })
        assertEquals(AutoFillResult(0L, emptyMap()), result)
    }
}
