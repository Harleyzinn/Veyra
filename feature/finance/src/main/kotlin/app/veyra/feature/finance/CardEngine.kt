package app.veyra.feature.finance

import app.veyra.model.Item
import app.veyra.model.Workspace
import java.time.LocalDate
import java.time.YearMonth

data class CardCycle(val closingDate: LocalDate, val dueDate: LocalDate)
data class CardInvoice(val id: String, val cardId: String, val closingDate: LocalDate, val dueDate: LocalDate,
    val totalMinor: Long, val paidMinor: Long, val outstandingMinor: Long, val purchases: List<Item>, val currency: String = "BRL")

object CardEngine {
    /** A purchase on the closing day belongs to the following cycle. */
    fun cycle(purchaseDate: LocalDate, card: Item): CardCycle {
        val closing = card.value("closing").toIntOrNull() ?: 1
        val due = card.value("due").toIntOrNull() ?: 10
        require(closing in 1..31 && due in 1..31)
        var month = YearMonth.from(purchaseDate)
        if (!purchaseDate.isBefore(month.atDay(closing.coerceAtMost(month.lengthOfMonth())))) month = month.plusMonths(1)
        val closingDate = month.atDay(closing.coerceAtMost(month.lengthOfMonth()))
        val dueMonth = if (due <= closing) month.plusMonths(1) else month
        return CardCycle(closingDate, dueMonth.atDay(due.coerceAtMost(dueMonth.lengthOfMonth())))
    }
    fun cycleDueDate(purchaseDate: LocalDate, card: Item) = cycle(purchaseDate, card).dueDate
    fun invoiceId(cardId: String, dueDate: LocalDate) = "invoice:$cardId:$dueDate"
    fun invoices(items: List<Item>, card: Item, from: LocalDate, through: LocalDate): List<CardInvoice> {
        val purchases = items.filter { FinancialDomain.isExpense(it) && it.value("card") == card.id && FinancialDomain.currency(it) == FinancialDomain.currency(card) && !FinancialDomain.legacyCardCash(it) }
        val grouped = purchases.groupBy { item ->
            item.value("dueDate").takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: cycleDueDate(LocalDate.parse(item.date), card)
        }
        val carry = items.filter { it.type == "card_carry" && it.value("card") == card.id && FinancialDomain.currency(it) == FinancialDomain.currency(card) }.groupBy { FinancialDomain.dueDate(it) }
        return (grouped.keys + carry.keys).filter { !it.isBefore(from) && !it.isAfter(through) }.map { due ->
            val charges = grouped[due].orEmpty()
            val id = invoiceId(card.id, due)
            val total = (charges + carry[due].orEmpty()).fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) }
            val paid = Math.addExact(items.filter { FinancialDomain.active(it) && FinancialDomain.settled(it) && it.value("paymentType") == "card_payment" && it.value("invoiceId") == id }
                .fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) }, carry[due].orEmpty().fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item, "paid")) })
            val closingMonth = if ((card.value("due").toIntOrNull() ?: 10) <= (card.value("closing").toIntOrNull() ?: 1)) YearMonth.from(due).minusMonths(1) else YearMonth.from(due)
            val closingDay = (card.value("closing").toIntOrNull() ?: 1).coerceAtMost(closingMonth.lengthOfMonth())
            CardInvoice(id, card.id, closingMonth.atDay(closingDay), due, total, paid, (total - paid).coerceAtLeast(0), charges.sortedBy { it.date }, FinancialDomain.currency(card))
        }.sortedBy { it.dueDate }
    }
    fun usedLimit(items: List<Item>, card: Item): Long = invoices(items, card, LocalDate.of(1900, 1, 1), LocalDate.of(2200, 12, 31))
        .fold(0L) { total, invoice -> Math.addExact(total, invoice.outstandingMinor) }
    fun availableLimit(items: List<Item>, card: Item) = (FinancialDomain.amount(card, "limit") - usedLimit(items, card)).coerceAtLeast(0)
    fun installments(item: Item, count: Int, firstDate: LocalDate = LocalDate.parse(item.date), card: Item? = null): List<Item> {
        val total = FinancialDomain.amount(item)
        val portions = Workspace.installments(total, count)
        val explicitDue = item.value("firstInstallment").takeIf(String::isNotBlank)?.let(LocalDate::parse)
        val children = portions.mapIndexed { index, amount ->
            val purchaseMonth = YearMonth.from(firstDate).plusMonths(index.toLong())
            val purchase = purchaseMonth.atDay(firstDate.dayOfMonth.coerceAtMost(purchaseMonth.lengthOfMonth()))
            val due = if (explicitDue != null) {
                val month = YearMonth.from(explicitDue).plusMonths(index.toLong())
                month.atDay(explicitDue.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
            } else card?.let { cycleDueDate(purchase, it) } ?: purchase
            FinancialDomain.normalize(item.copy(id = "installment:${item.id}:$index", type = "expense", title = "${item.title.take(170)} • ${index + 1}/$count",
                date = purchase.toString(), fields = item.fields - setOf("amountMinor", "installments", "firstInstallment") + mapOf(
                    "amount" to FinancialDomain.decimal(amount, FinancialDomain.currency(item)), "amountMinor" to amount.toString(),
                    "installmentPlanId" to item.id, "installmentIndex" to (index + 1).toString(), "installmentCount" to count.toString(),
                    "purchaseDate" to item.date,
                    "dueDate" to due.toString(), "status" to if (card != null || purchase.isAfter(LocalDate.now())) "pending" else "paid",
                    "planned" to if (card != null || purchase.isAfter(LocalDate.now())) "Sim" else "Não")))
        }
        val plan = FinancialDomain.normalize(item.copy(type = "installment_plan", fields = item.fields + mapOf("count" to count.toString(), "installments" to count.toString(), "firstInstallment" to (explicitDue ?: firstDate).toString())))
        return listOf(plan) + children
    }
    /** Stable payment ID prevents duplicate full payments after a repeated tap. */
    fun settleInvoice(invoice: CardInvoice, accountId: String, date: LocalDate = LocalDate.now(), amountMinor: Long = invoice.outstandingMinor): List<Item> {
        require(accountId.isNotBlank()) { "Escolha uma conta para pagar" }
        require(invoice.outstandingMinor > 0) { "Esta fatura já está paga" }
        require(amountMinor in 1..invoice.outstandingMinor) { "O pagamento precisa ser maior que zero e não pode superar a fatura" }
        val currency = invoice.currency
        val totalPaid = Math.addExact(invoice.paidMinor, amountMinor)
        return listOf(FinancialDomain.normalize(Item(id = "invoice-payment:${invoice.id}:$totalPaid", type = "expense", title = "Pagamento de fatura",
            date = date.toString(), fields = mapOf("amountMinor" to amountMinor.toString(), "currency" to currency,
                "account" to accountId, "card" to invoice.cardId, "status" to "paid", "paymentType" to "card_payment",
                "invoiceId" to invoice.id, "invoiceDueDate" to invoice.dueDate.toString(), "settledDate" to date.toString(), "category" to "Cartão"))))
    }
    fun advanceInstallments(items: List<Item>, planId: String, count: Int, date: LocalDate = LocalDate.now()): List<Item> {
        require(count > 0)
        val pending = items.filter { it.deletedAt == 0L && it.value("installmentPlanId") == planId && FinancialDomain.active(it) &&
            (!FinancialDomain.settled(it) || it.value("card").isNotBlank()) && FinancialDomain.dueDate(it).isAfter(date) }
            .sortedBy { it.value("installmentIndex").toIntOrNull() ?: 0 }
        require(count <= pending.size) { "Quantidade superior às parcelas futuras" }
        return pending.take(count).map { it.copy(date = date.toString(), fields = it.fields + mapOf("dueDate" to date.toString(), "advancedFrom" to it.value("dueDate"), "originalDate" to it.date, "advancedAt" to date.toString())) }
    }
}
