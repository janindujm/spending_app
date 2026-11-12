package com.spendplan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetPeriodTest {

    @Test
    fun `start day 1 is the calendar month`() {
        val r = BudgetPeriod.range(YearMonth.of(2026, 2), 1)
        assertEquals(LocalDate.of(2026, 2, 1), r.start)
        assertEquals(LocalDate.of(2026, 2, 28), r.endInclusive)
        assertEquals(28, r.days.size)
    }

    @Test
    fun `leap year february`() {
        val r = BudgetPeriod.range(YearMonth.of(2028, 2), 1)
        assertEquals(LocalDate.of(2028, 2, 29), r.endInclusive)
    }

    @Test
    fun `salary cycle starting on the 25th`() {
        val r = BudgetPeriod.range(YearMonth.of(2026, 10), 25)
        assertEquals(LocalDate.of(2026, 10, 25), r.start)
        assertEquals(LocalDate.of(2026, 11, 24), r.endInclusive)
    }

    @Test
    fun `period crossing the year boundary`() {
        val r = BudgetPeriod.range(YearMonth.of(2026, 12), 15)
        assertEquals(LocalDate.of(2026, 12, 15), r.start)
        assertEquals(LocalDate.of(2027, 1, 14), r.endInclusive)
        assertEquals(YearMonth.of(2026, 12), BudgetPeriod.monthOf(LocalDate.of(2027, 1, 14), 15))
        assertEquals(YearMonth.of(2027, 1), BudgetPeriod.monthOf(LocalDate.of(2027, 1, 15), 15))
    }

    @Test
    fun `monthOf uses the start day`() {
        assertEquals(YearMonth.of(2026, 10), BudgetPeriod.monthOf(LocalDate.of(2026, 11, 24), 25))
        assertEquals(YearMonth.of(2026, 11), BudgetPeriod.monthOf(LocalDate.of(2026, 11, 25), 25))
        assertEquals(YearMonth.of(2026, 11), BudgetPeriod.monthOf(LocalDate.of(2026, 11, 30), 1))
    }

    @Test
    fun `consecutive periods have no gaps or overlaps for every start day`() {
        for (day in 1..28) {
            var month = YearMonth.of(2025, 1)
            repeat(30) {
                val current = BudgetPeriod.range(month, day)
                val next = BudgetPeriod.range(month.plusMonths(1), day)
                assertEquals("day=$day month=$month", current.endInclusive.plusDays(1), next.start)
                // every date maps back to its own period
                assertEquals(month, BudgetPeriod.monthOf(current.start, day))
                assertEquals(month, BudgetPeriod.monthOf(current.endInclusive, day))
                month = month.plusMonths(1)
            }
        }
    }

    @Test
    fun `start day is clamped to 1 to 28`() {
        assertEquals(28, BudgetPeriod.clampStartDay(31))
        assertEquals(1, BudgetPeriod.clampStartDay(0))
        assertEquals(LocalDate.of(2026, 2, 28), BudgetPeriod.range(YearMonth.of(2026, 2), 31).start)
    }

    @Test
    fun `range contains`() {
        val r = BudgetPeriod.range(YearMonth.of(2026, 3), 10)
        assertTrue(LocalDate.of(2026, 3, 10) in r)
        assertTrue(LocalDate.of(2026, 4, 9) in r)
        assertFalse(LocalDate.of(2026, 4, 10) in r)
        assertFalse(LocalDate.of(2026, 3, 9) in r)
    }

    @Test
    fun `month key round trip`() {
        val ym = YearMonth.of(2026, 7)
        assertEquals("2026-07", BudgetPeriod.key(ym))
        assertEquals(ym, BudgetPeriod.parseKey("2026-07"))
    }
}
