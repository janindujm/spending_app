package com.spendplan.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money is stored as [Long] minor units (cents) to avoid floating point errors.
 * Every supported currency uses 2 decimal places.
 */
data class CurrencyOption(val code: String, val symbol: String, val displayName: String)

object Currencies {
    val LKR = CurrencyOption("LKR", "Rs.", "Sri Lankan Rupee")

    val all: List<CurrencyOption> = listOf(
        LKR,
        CurrencyOption("INR", "₹", "Indian Rupee"),
        CurrencyOption("USD", "$", "US Dollar"),
        CurrencyOption("EUR", "€", "Euro"),
        CurrencyOption("GBP", "£", "British Pound"),
        CurrencyOption("AUD", "A$", "Australian Dollar"),
        CurrencyOption("CAD", "C$", "Canadian Dollar"),
        CurrencyOption("SGD", "S$", "Singapore Dollar"),
        CurrencyOption("AED", "AED", "UAE Dirham"),
        CurrencyOption("JPY", "¥", "Japanese Yen"),
        CurrencyOption("PKR", "Rs", "Pakistani Rupee"),
        CurrencyOption("BDT", "৳", "Bangladeshi Taka"),
    )

    fun byCode(code: String?): CurrencyOption = all.firstOrNull { it.code == code } ?: LKR
}

object MoneyFormat {
    private const val MINOR_PER_MAJOR = 100L

    /** "Rs. 1,234,567.50", "-Rs. 12.00". Locale independent so output is stable. */
    fun format(minor: Long, currency: CurrencyOption, showDecimals: Boolean = true): String {
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val major = abs / MINOR_PER_MAJOR
        val cents = abs % MINOR_PER_MAJOR
        val number = if (showDecimals) {
            "${groupThousands(major)}.${cents.toString().padStart(2, '0')}"
        } else {
            // Round half up to whole units for compact display.
            groupThousands(if (cents >= 50) major + 1 else major)
        }
        val separator = if (currency.symbol.last().isLetter() || currency.symbol.endsWith(".")) " " else ""
        return "$sign${currency.symbol}$separator$number"
    }

    /** Compact form for chart labels: 950, 12.5K, 1.2M. */
    fun compact(minor: Long): String {
        val major = minor / MINOR_PER_MAJOR.toDouble()
        val abs = kotlin.math.abs(major)
        return when {
            abs >= 1_000_000 -> trimZero(major / 1_000_000) + "M"
            abs >= 1_000 -> trimZero(major / 1_000) + "K"
            else -> major.toLong().toString()
        }
    }

    private fun trimZero(value: Double): String {
        val rounded = BigDecimal(value).setScale(1, RoundingMode.HALF_UP)
        return rounded.stripTrailingZeros().toPlainString()
    }

    fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { index, c ->
            if (index > 0 && (digits.length - index) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Plain machine-readable decimal ("1234.50") used for CSV and editing. */
    fun plain(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = kotlin.math.abs(minor)
        return "$sign${abs / MINOR_PER_MAJOR}.${(abs % MINOR_PER_MAJOR).toString().padStart(2, '0')}"
    }

    /** Value for an editable text field: drops ".00". */
    fun editable(minor: Long): String {
        if (minor == 0L) return ""
        return if (minor % MINOR_PER_MAJOR == 0L) (minor / MINOR_PER_MAJOR).toString() else plain(minor)
    }

    /**
     * Parses user input such as "1,250", "1250.5" or "Rs. 99.99" into minor units.
     * Returns null for invalid input or more than 2 decimals.
     */
    fun parse(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (!cleaned.matches(Regex("""\d{1,13}(\.\d{0,2})?|\.\d{1,2}"""))) return null
        return try {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun roundToWholeUnits(minor: Long): Long {
        val major = BigDecimal(minor).divide(BigDecimal(MINOR_PER_MAJOR), 0, RoundingMode.HALF_UP)
        return major.toLong() * MINOR_PER_MAJOR
    }
}
