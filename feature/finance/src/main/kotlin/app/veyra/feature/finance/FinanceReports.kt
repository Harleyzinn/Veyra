package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate

enum class ReportBasis(val label: String) {
    PURCHASE("Gastos registrados"), CASH("Fluxo de caixa")
}

data class FinanceReport(val entries: List<Item>, val incomeMinor: Long, val expenseMinor: Long) {
    val netMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}

/** Reports share the existing cash semantics, including historical card purchases. */
object FinanceReports {
    fun select(items: List<Item>, from: LocalDate, through: LocalDate, currency: String,
        basis: ReportBasis = ReportBasis.PURCHASE, filter: FinancialFilter = FinancialFilter(),
        query: String = "", today: LocalDate = LocalDate.now()): FinanceReport {
        require(!through.isBefore(from) && !through.isAfter(from.plusYears(10))) { "Confira o período do relatório." }
        FinancialDomain.scale(currency)
        val candidates = if (basis == ReportBasis.PURCHASE) FinanceEngine.transactions(items, from, through, currency)
            else items.filter { it.type in setOf("income", "expense") && FinancialDomain.currency(it) == currency &&
                FinancialDomain.active(it) && it.value("virtual") != "yes" && FinancialDomain.cashDelta(it) != 0L &&
                FinancialDomain.bookedDate(it) in from..minOf(through, today) }
        val entries = FinanceEngine.search(FinanceEngine.filter(candidates.filter {
            it.type in setOf("income", "expense") && (basis == ReportBasis.CASH || it.value("paymentType") != "card_payment")
        }, filter.copy(from = null, through = null), today), query, items)
            .sortedByDescending { if (basis == ReportBasis.CASH) FinancialDomain.bookedDate(it) else FinancialDomain.transactionDate(it) }
        val realized = entries.filter { it.value("virtual") != "yes" && (basis == ReportBasis.CASH ||
            if (it.type == "expense") FinanceEngine.recognizedExpense(it, today)
            else FinancialDomain.settled(it) && !FinancialDomain.bookedDate(it).isAfter(minOf(through, today))) }
        fun sum(type: String) = realized.filter { it.type == type }.fold(0L) { total, item -> Math.addExact(total, FinancialDomain.amount(item)) }
        return FinanceReport(entries, sum("income"), sum("expense"))
    }
    fun totals(entries: List<Item>, today: LocalDate = LocalDate.now()): FinanceReport {
        fun sum(type: String) = entries.filter { it.type == type && FinancialDomain.active(it) && it.value("virtual") != "yes" &&
            if (type == "expense") FinanceEngine.recognizedExpense(it, today) else FinancialDomain.settled(it) && !FinancialDomain.bookedDate(it).isAfter(today) }
            .fold(0L) { total, item -> Math.addExact(total, FinancialDomain.amount(item)) }
        return FinanceReport(entries, sum("income"), sum("expense"))
    }
}
