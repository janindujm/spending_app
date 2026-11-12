package com.spendplan.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }.toList()

    val startEpochDay: Long get() = start.toEpochDay()
    val endEpochDay: Long get() = endInclusive.toEpochDay()
}

/**
 * A budget "month" can start on any day 1–28 (e.g. salary day). The period for
 * month M runs from day N of M up to day N-1 of month M+1. With N = 1 it is the
 * normal calendar month. Periods never overlap and never leave gaps.
 */
object BudgetPeriod {
    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 28

    fun clampStartDay(day: Int): Int = day.coerceIn(MIN_START_DAY, MAX_START_DAY)

    fun range(month: YearMonth, startDay: Int): DateRange {
        val day = clampStartDay(startDay)
        val start = month.atDay(day)
        val end = month.plusMonths(1).atDay(day).minusDays(1)
        return DateRange(start, end)
    }

    /** The budget month whose period contains [date]. */
    fun monthOf(date: LocalDate, startDay: Int): YearMonth {
        val day = clampStartDay(startDay)
        val ym = YearMonth.from(date)
        return if (date.dayOfMonth >= day) ym else ym.minusMonths(1)
    }

    fun current(today: LocalDate, startDay: Int): YearMonth = monthOf(today, startDay)

    fun label(month: YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    /** "25 Oct – 24 Nov" style subtitle, shown when the period isn't a calendar month. */
    fun rangeLabel(month: YearMonth, startDay: Int): String {
        val r = range(month, startDay)
        val f = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return "${r.start.format(f)} – ${r.endInclusive.format(f)}"
    }

    fun key(month: YearMonth): String = month.toString() // yyyy-MM
    fun parseKey(key: String): YearMonth = YearMonth.parse(key)
}
