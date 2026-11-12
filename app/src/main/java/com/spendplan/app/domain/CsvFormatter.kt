package com.spendplan.app.domain

import java.time.LocalDate

data class CsvRow(
    val date: LocalDate,
    val type: String,
    val category: String,
    val amountMinor: Long,
    val note: String,
)

/** RFC 4180 CSV writer (CRLF line endings, quotes only when needed). */
object CsvFormatter {
    val HEADER = listOf("Date", "Type", "Category", "Amount", "Currency", "Note")

    fun format(rows: List<CsvRow>, currencyCode: String): String {
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        rows.forEach { row ->
            sb.append(
                listOf(
                    row.date.toString(),
                    row.type,
                    escape(row.category),
                    MoneyFormat.plain(row.amountMinor),
                    currencyCode,
                    escape(row.note),
                ).joinToString(","),
            ).append("\r\n")
        }
        return sb.toString()
    }

    /**
     * Quotes fields containing separators/quotes/newlines and neutralises values that
     * spreadsheets would treat as formulas (CSV injection) by prefixing a quote.
     */
    fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_PREFIXES) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            safe.startsWith(' ') || safe.endsWith(' ')
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t')
}
