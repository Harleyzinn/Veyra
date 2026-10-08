package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class FinanceReportsTest {
    private val today = LocalDate.of(2026, 10, 8)
    private fun expense(id: String, date: String, vararg fields: Pair<String, String>) =
        Item(id = id, type = "expense", title = id, date = date, fields = mapOf("amountMinor" to "4200", "currency" to "BRL", "status" to "paid", "financialVersion" to "3") + fields)

    @Test fun actualPaymentDateMovesCashWithoutMovingThePurchase() {
        val expense = expense("Internet", "2026-09-28", "settledDate" to "2026-10-02", "account" to "a", "category" to "Casa")
        val from = today.withDayOfMonth(1)
        assertEquals(0, FinanceReports.select(listOf(expense), from, today, "BRL").entries.size)
        val cash = FinanceReports.select(listOf(expense), from, today, "BRL", ReportBasis.CASH)
        assertEquals(listOf(expense), cash.entries)
        assertEquals(4200L, cash.expenseMinor)
        assertEquals(-4200L, cash.netMinor)
        assertEquals(0, FinanceReports.select(listOf(expense), from, today, "BRL", ReportBasis.CASH, FinancialFilter(account = "b")).entries.size)
    }

    @Test fun invoicePaymentEntersCashOnceWhilePurchaseEntersRecognizedSpending() {
        val purchase = expense("Compra", today.toString(), "card" to "c")
        val payment = expense("Fatura", today.toString(), "card" to "c", "paymentType" to "card_payment", "invoiceId" to "invoice:c:2026-10-10")
        val transfer = purchase.copy(id = "transfer", type = "transfer")
        val rows = listOf(purchase, payment, transfer)
        assertEquals(listOf(purchase), FinanceReports.select(rows, today, today, "BRL").entries)
        val cash = FinanceReports.select(rows, today, today, "BRL", ReportBasis.CASH)
        assertEquals(listOf(payment), cash.entries)
        assertEquals(4200L, cash.expenseMinor)
        val legacy = purchase.copy(id = "legacy", fields = purchase.fields + ("legacyCardCash" to "yes"))
        assertEquals(4200L, FinanceReports.select(listOf(legacy), today, today, "BRL", ReportBasis.CASH).expenseMinor)
    }

    @Test fun cashRejectsPendingFutureDeletedVirtualAndOtherCurrencies() {
        val ordinary = expense("Hoje", today.toString())
        val rows = listOf(ordinary,
            expense("Pendente", today.toString(), "status" to "pending"),
            expense("Futuro", today.plusDays(1).toString()),
            expense("Virtual", today.toString(), "virtual" to "yes"),
            expense("Dólar", today.toString(), "currency" to "USD"),
            ordinary.copy(id = "deleted", deletedAt = 1),
            expense("Cancelado", today.toString(), "status" to "cancelled"))
        assertEquals(listOf(ordinary), FinanceReports.select(rows, today, today.plusDays(30), "BRL", ReportBasis.CASH).entries)
        assertFailsWith<IllegalArgumentException> { FinanceReports.select(rows, today, today.minusDays(1), "BRL") }
        assertFailsWith<IllegalArgumentException> { FinanceReports.select(rows, today, today.plusYears(11), "BRL") }
    }

    @Test fun selectedTotalsAreNotTheWholeMonthAndForecastIsNotRealized() {
        val selected = expense("Selecionado", today.toString())
        val pending = expense("Previsto", today.toString(), "status" to "pending")
        val report = FinanceReports.select(listOf(selected, pending), today, today, "BRL", query = "Selecionado")
        assertEquals(4200L, report.expenseMinor)
        assertEquals(4200L, FinanceReports.totals(listOf(selected, pending), today).expenseMinor)
    }

    @Test fun agendaIncludesOverdueAndRemainingInvoiceButExcludesPurchasesAndSettledBills() {
        val overdue = expense("Atraso", today.minusDays(5).toString(), "status" to "pending")
        val invoice = expense("Saldo da fatura", today.plusDays(2).toString(), "card" to "c", "cardInvoice" to "yes", "status" to "pending", "amountMinor" to "20000")
        val rows = listOf(overdue, overdue, invoice, expense("Compra", today.toString(), "card" to "c", "status" to "pending"),
            expense("Pago", today.toString()), expense("Distante", today.plusDays(8).toString(), "status" to "pending"),
            expense("USD", today.toString(), "currency" to "USD", "status" to "overdue"))
        assertEquals(listOf(overdue, invoice), FinanceAgenda.upcoming(rows, today))
    }
}
