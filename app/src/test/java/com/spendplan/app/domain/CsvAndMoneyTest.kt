package com.spendplan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CsvFormatterTest {

    @Test
    fun `header and simple row with CRLF`() {
        val csv = CsvFormatter.format(
            listOf(CsvRow(LocalDate.of(2026, 11, 5), "Expense", "Food", 1_250_50, "Lunch")),
            "LKR",
        )
        assertEquals(
            "Date,Type,Category,Amount,Currency,Note\r\n2026-11-05,Expense,Food,1250.50,LKR,Lunch\r\n",
            csv,
        )
    }

    @Test
    fun `fields with commas quotes and newlines are quoted`() {
        assertEquals("\"Bills, Utilities\"", CsvFormatter.escape("Bills, Utilities"))
        assertEquals("\"He said \"\"hi\"\"\"", CsvFormatter.escape("He said \"hi\""))
        assertEquals("\"line1\nline2\"", CsvFormatter.escape("line1\nline2"))
        assertEquals("\" padded \"", CsvFormatter.escape(" padded "))
        assertEquals("Bills & Utilities", CsvFormatter.escape("Bills & Utilities"))
    }

    @Test
    fun `formula injection is neutralised`() {
        assertEquals("'=SUM(A1:A2)", CsvFormatter.escape("=SUM(A1:A2)"))
        assertEquals("'+94771234567", CsvFormatter.escape("+94771234567"))
        assertEquals("'@cmd", CsvFormatter.escape("@cmd"))
    }

    @Test
    fun `empty note and amounts without grouping`() {
        val csv = CsvFormatter.format(
            listOf(CsvRow(LocalDate.of(2026, 1, 1), "Income", "Salary", 250_000_00, "")),
            "LKR",
        )
        assertEquals("2026-01-01,Income,Salary,250000.00,LKR,\r\n", csv.lines()[1] + "\r\n")
    }

    @Test
    fun `no rows gives just the header`() {
        assertEquals("Date,Type,Category,Amount,Currency,Note\r\n", CsvFormatter.format(emptyList(), "USD"))
    }
}

class MoneyFormatTest {
    @Test
    fun `formats LKR with thousands separators`() {
        assertEquals("Rs. 1,234,567.50", MoneyFormat.format(123_456_750, Currencies.LKR))
        assertEquals("Rs. 0.05", MoneyFormat.format(5, Currencies.LKR))
        assertEquals("-Rs. 1,000.00", MoneyFormat.format(-100_000, Currencies.LKR))
        assertEquals("Rs. 1,235", MoneyFormat.format(123_450, Currencies.LKR, showDecimals = false))
    }

    @Test
    fun `symbol currencies have no space`() {
        assertEquals("$12.00", MoneyFormat.format(1_200, Currencies.byCode("USD")))
        assertEquals("AED 5.00", MoneyFormat.format(500, Currencies.byCode("AED")))
        assertEquals(Currencies.LKR, Currencies.byCode("nope"))
    }

    @Test
    fun `parse accepts grouped input and up to two decimals`() {
        assertEquals(125_000L, MoneyFormat.parse("1,250"))
        assertEquals(125_050L, MoneyFormat.parse("1250.5"))
        assertEquals(99L, MoneyFormat.parse("0.99"))
        assertEquals(50L, MoneyFormat.parse(".5"))
        assertNull(MoneyFormat.parse(""))
        assertNull(MoneyFormat.parse("abc"))
        assertNull(MoneyFormat.parse("1.234"))
        assertNull(MoneyFormat.parse("-5"))
    }

    @Test
    fun `editable and compact`() {
        assertEquals("1500", MoneyFormat.editable(150_000))
        assertEquals("1500.25", MoneyFormat.editable(150_025))
        assertEquals("", MoneyFormat.editable(0))
        assertEquals("12.5K", MoneyFormat.compact(1_250_000))
        assertEquals("2M", MoneyFormat.compact(200_000_000))
        assertEquals("950", MoneyFormat.compact(95_000))
    }
}
