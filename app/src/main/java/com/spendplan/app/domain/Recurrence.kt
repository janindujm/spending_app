package com.spendplan.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class Frequency { MONTHLY, WEEKLY }

/**
 * Describes when a recurring transaction happens.
 * - MONTHLY on [dayOfMonth] (1–31; clamped to the last day in shorter months).
 * - WEEKLY on [dayOfWeek].
 * Occurrences never happen before [startDate]. [lastGenerated] is the last occurrence
 * already turned into a real transaction (null = none yet).
 */
data class RecurrenceSpec(
    val frequency: Frequency,
    val dayOfMonth: Int,
    val dayOfWeek: DayOfWeek,
    val startDate: LocalDate,
    val lastGenerated: LocalDate?,
)

object RecurrenceScheduler {
    /** Safety cap so a corrupted rule can't create an unbounded number of rows. */
    const val MAX_CATCH_UP = 400

    /** All occurrence dates in [from, to] (both inclusive) that are on/after the start date. */
    fun occurrences(spec: RecurrenceSpec, from: LocalDate, to: LocalDate): List<LocalDate> {
        val begin = maxOf(from, spec.startDate)
        if (begin.isAfter(to)) return emptyList()
        val result = mutableListOf<LocalDate>()
        when (spec.frequency) {
            Frequency.MONTHLY -> {
                var ym = YearMonth.from(begin)
                val endYm = YearMonth.from(to)
                while (!ym.isAfter(endYm)) {
                    val date = monthlyDate(ym, spec.dayOfMonth)
                    if (!date.isBefore(begin) && !date.isAfter(to)) result += date
                    ym = ym.plusMonths(1)
                }
            }
            Frequency.WEEKLY -> {
                var date = begin.with(TemporalAdjusters.nextOrSame(spec.dayOfWeek))
                while (!date.isAfter(to)) {
                    result += date
                    date = date.plusWeeks(1)
                }
            }
        }
        return result
    }

    /**
     * Occurrences that are due (on or before [today]) and not generated yet.
     * Running this again after [lastGenerated] is advanced returns nothing new, which
     * together with the DB unique index guarantees no duplicates on catch-up.
     */
    fun dueDates(spec: RecurrenceSpec, today: LocalDate): List<LocalDate> {
        val from = spec.lastGenerated?.plusDays(1) ?: spec.startDate
        return occurrences(spec, from, today).take(MAX_CATCH_UP)
    }

    /** The first occurrence strictly after [after]. */
    fun nextOccurrence(spec: RecurrenceSpec, after: LocalDate): LocalDate {
        val from = maxOf(after.plusDays(1), spec.startDate)
        return when (spec.frequency) {
            Frequency.MONTHLY -> {
                val sameMonth = monthlyDate(YearMonth.from(from), spec.dayOfMonth)
                if (!sameMonth.isBefore(from)) sameMonth else monthlyDate(YearMonth.from(from).plusMonths(1), spec.dayOfMonth)
            }
            Frequency.WEEKLY -> from.with(TemporalAdjusters.nextOrSame(spec.dayOfWeek))
        }
    }

    fun monthlyDate(ym: YearMonth, dayOfMonth: Int): LocalDate =
        ym.atDay(dayOfMonth.coerceIn(1, ym.lengthOfMonth()))
}

/** Result of processing one rule: which dates to insert and the new high-water mark. */
data class GenerationPlan(val datesToCreate: List<LocalDate>, val newLastGenerated: LocalDate?)

object RecurringGenerator {
    /**
     * Pure planning step used by the worker. [alreadyExisting] are dates for which a
     * transaction from this rule already exists (e.g. created by a run that crashed
     * before saving lastGenerated); those are skipped but still advance the mark.
     */
    fun plan(spec: RecurrenceSpec, today: LocalDate, alreadyExisting: Set<LocalDate>): GenerationPlan {
        val due = RecurrenceScheduler.dueDates(spec, today)
        if (due.isEmpty()) return GenerationPlan(emptyList(), spec.lastGenerated)
        return GenerationPlan(
            datesToCreate = due.filterNot { it in alreadyExisting },
            newLastGenerated = due.last(),
        )
    }
}
