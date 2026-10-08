package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class FinanceChartDataTest {
    private val today = LocalDate.of(2026, 10, 8)
    private fun item(id: String, date: String, type: String = "expense", vararg fields: Pair<String, String>) =
        Item(id = id, type = type, title = id, date = date, fields = mapOf("amountMinor" to "10001", "currency" to "BRL", "status" to if (type == "income") "received" else "paid", "financialVersion" to "3") + fields)

    @Test fun everyGrainPreservesReportTotalsAndZeroPeriods() {
        val from = today.minusDays(9)
        val rows = listOf(item("income", from.toString(), "income"), item("expense", today.toString()),
            item("pending", today.toString(), fields = arrayOf("status" to "pending")),
            item("usd", today.toString(), fields = arrayOf("currency" to "USD")))
        val report = FinanceReports.select(rows, from, today, "BRL", today = today)
        ChartGrain.entries.forEach { grain ->
            val chart = FinanceChartData.build(report, ReportBasis.PURCHASE, from, today, grain, rows, today)
            assertEquals(report.incomeMinor, chart.points.sumOf { it.incomeMinor })
            assertEquals(report.expenseMinor, chart.points.sumOf { it.expenseMinor })
            assertEquals(report.expenseMinor, chart.slices(ChartBreakdown.CATEGORY).sumOf { it.amountMinor })
        }
        val daily = FinanceChartData.build(report, ReportBasis.PURCHASE, from, today, ChartGrain.DAY, rows, today)
        assertEquals(10, daily.points.size)
        assertEquals(0L, daily.points[1].resultMinor)
        val weekly = FinanceChartData.build(report, ReportBasis.PURCHASE, from, today, ChartGrain.WEEK, rows, today)
        assertEquals(LocalDate.of(2026, 9, 28), weekly.points.first().date)
        assertEquals(2, weekly.points.size)
    }

    @Test fun cashUsesPaymentDateAndInvoiceOnce() {
        val old = item("old", "2026-09-02", fields = arrayOf("settledDate" to today.toString(), "category" to "Casa"))
        val purchase = item("buy", today.toString(), fields = arrayOf("card" to "card"))
        val payment = item("invoice", today.toString(), fields = arrayOf("paymentType" to "card_payment", "card" to "card"))
        val rows = listOf(old, purchase, payment)
        val report = FinanceReports.select(rows, today, today, "BRL", ReportBasis.CASH, today = today)
        val chart = FinanceChartData.build(report, ReportBasis.CASH, today, today, ChartGrain.DAY, rows, today)
        assertEquals(20002L, chart.points.single().expenseMinor)
        assertEquals(today, chart.points.single().date)
        assertEquals(-20002L, chart.points.single().resultMinor)
    }

    @Test fun distributionCombinesTailWithoutLosingCentsOrMergingNamedAccounts() {
        val rows = (1..12).map { i -> item("expense$i", today.toString(), fields = arrayOf("category" to "Categoria $i", "account" to "account$i", "amountMinor" to i.toString())) }
        val accounts = (1..12).map { Item(id = "account$it", type = "account", title = "Carteira") }
        val report = FinanceReports.select(rows + accounts, today, today, "BRL", today = today)
        val chart = FinanceChartData.build(report, ReportBasis.PURCHASE, today, today, ChartGrain.DAY, rows + accounts, today)
        val slices = chart.slices(ChartBreakdown.CATEGORY)
        assertEquals(8, slices.size)
        assertEquals(78L, slices.sumOf { it.amountMinor })
        assertEquals(15L, slices.last().amountMinor)
        assertEquals(12, chart.groups[ChartBreakdown.ACCOUNT]!!.size)
        assertEquals(12, chart.groups[ChartBreakdown.ACCOUNT]!!.map { it.label }.toSet().size)
    }

    @Test fun forecastNeverBecomesRealChartDataAndRangeIsBounded() {
        val forecast = item("virtual", today.toString(), fields = arrayOf("virtual" to "yes"))
        val report = FinanceReport(listOf(forecast), 0, 0)
        val chart = FinanceChartData.build(report, ReportBasis.PURCHASE, today, today, ChartGrain.DAY, listOf(forecast), today)
        assertEquals(0L, chart.points.single().expenseMinor)
        assertTrue(chart.slices(ChartBreakdown.CATEGORY).isEmpty())
        assertFailsWith<IllegalArgumentException> { FinanceChartData.build(report, ReportBasis.PURCHASE, today, today.minusDays(1), ChartGrain.DAY, emptyList(), today) }
    }
}
