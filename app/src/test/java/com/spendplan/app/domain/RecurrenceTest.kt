package com.spendplan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun monthly(day: Int, start: LocalDate, last: LocalDate? = start) =
        RecurrenceSpec(Frequency.MONTHLY, day, DayOfWeek.MONDAY, start, last)

    private fun weekly(dow: DayOfWeek, start: LocalDate, last: LocalDate? = start) =
        RecurrenceSpec(Frequency.WEEKLY, 1, dow, start, last)

    @Test
    fun `monthly nothing due before next occurrence`() {
        val spec = monthly(5, d(2026, 1, 5))
        assertTrue(RecurrenceScheduler.dueDates(spec, d(2026, 2, 4)).isEmpty())
        assertEquals(listOf(d(2026, 2, 5)), RecurrenceScheduler.dueDates(spec, d(2026, 2, 5)))
    }

    @Test
    fun `monthly day 31 clamps to month end`() {
        val spec = monthly(31, d(2026, 1, 31))
        assertEquals(
            listOf(d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)),
            RecurrenceScheduler.dueDates(spec, d(2026, 4, 30)),
        )
    }

    @Test
    fun `weekly occurrences`() {
        val start = d(2026, 11, 2) // Monday
        val spec = weekly(DayOfWeek.MONDAY, start)
        assertEquals(
            listOf(d(2026, 11, 9), d(2026, 11, 16), d(2026, 11, 23)),
            RecurrenceScheduler.dueDates(spec, d(2026, 11, 28)),
        )
    }

    @Test
    fun `rule without lastGenerated includes the start date`() {
        val spec = monthly(10, d(2026, 3, 10), last = null)
        assertEquals(listOf(d(2026, 3, 10), d(2026, 4, 10)), RecurrenceScheduler.dueDates(spec, d(2026, 4, 20)))
    }

    @Test
    fun `occurrences never before start date`() {
        val spec = monthly(1, d(2026, 3, 15), last = null)
        assertEquals(listOf(d(2026, 4, 1), d(2026, 5, 1)), RecurrenceScheduler.dueDates(spec, d(2026, 5, 10)))
    }

    @Test
    fun `catch up after the app did not run for months`() {
        val spec = monthly(5, d(2026, 1, 5))
        val plan = RecurringGenerator.plan(spec, today = d(2026, 6, 10), alreadyExisting = emptySet())
        assertEquals(
            listOf(d(2026, 2, 5), d(2026, 3, 5), d(2026, 4, 5), d(2026, 5, 5), d(2026, 6, 5)),
            plan.datesToCreate,
        )
        assertEquals(d(2026, 6, 5), plan.newLastGenerated)
    }

    @Test
    fun `running twice creates no duplicates`() {
        // Simulates the worker + DB: a set of created dates and the rule's high-water mark.
        var spec = weekly(DayOfWeek.FRIDAY, d(2026, 1, 2))
        val created = mutableListOf<LocalDate>()

        fun run(today: LocalDate) {
            val plan = RecurringGenerator.plan(spec, today, created.toSet())
            created += plan.datesToCreate
            spec = spec.copy(lastGenerated = plan.newLastGenerated)
        }

        run(d(2026, 1, 31))
        run(d(2026, 1, 31)) // same day again
        run(d(2026, 2, 1))  // next day, nothing new due
        assertEquals(listOf(d(2026, 1, 9), d(2026, 1, 16), d(2026, 1, 23), d(2026, 1, 30)), created)
        run(d(2026, 2, 13))
        assertEquals(created.distinct(), created)
        assertEquals(6, created.size)
    }

    @Test
    fun `crash after insert but before saving mark does not duplicate`() {
        // Previous run inserted Feb 5 but crashed before updating lastGenerated.
        val spec = monthly(5, d(2026, 1, 5))
        val plan = RecurringGenerator.plan(spec, d(2026, 3, 6), alreadyExisting = setOf(d(2026, 2, 5)))
        assertEquals(listOf(d(2026, 3, 5)), plan.datesToCreate)
        assertEquals(d(2026, 3, 5), plan.newLastGenerated)
    }

    @Test
    fun `nothing due keeps the mark unchanged`() {
        val spec = monthly(20, d(2026, 5, 20))
        val plan = RecurringGenerator.plan(spec, d(2026, 6, 1), emptySet())
        assertTrue(plan.datesToCreate.isEmpty())
        assertEquals(d(2026, 5, 20), plan.newLastGenerated)
    }

    @Test
    fun `catch up is capped`() {
        val spec = weekly(DayOfWeek.MONDAY, d(2000, 1, 3))
        val due = RecurrenceScheduler.dueDates(spec, d(2026, 1, 1))
        assertEquals(RecurrenceScheduler.MAX_CATCH_UP, due.size)
    }

    @Test
    fun `next occurrence`() {
        assertEquals(d(2026, 2, 28), RecurrenceScheduler.nextOccurrence(monthly(31, d(2026, 1, 31)), d(2026, 1, 31)))
        assertEquals(d(2026, 3, 5), RecurrenceScheduler.nextOccurrence(monthly(5, d(2026, 1, 5)), d(2026, 2, 5)))
        assertEquals(
            d(2026, 11, 13),
            RecurrenceScheduler.nextOccurrence(weekly(DayOfWeek.FRIDAY, d(2026, 11, 6)), d(2026, 11, 6)),
        )
    }
}
