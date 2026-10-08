package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate

object FinanceAgenda {
    fun upcoming(entries: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL"): List<Item> =
        entries.distinctBy { it.id }.filter { it.type == "expense" && FinancialDomain.active(it) &&
            FinancialDomain.currency(it) == currency && !FinancialDomain.settled(it) &&
            (it.value("card").isBlank() || it.value("cardInvoice") == "yes") &&
            !FinancialDomain.dueDate(it).isAfter(today.plusDays(7)) }
            .sortedWith(compareBy<Item> { FinancialDomain.dueDate(it) }.thenBy { it.id })
}
