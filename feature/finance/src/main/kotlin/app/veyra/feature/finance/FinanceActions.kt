package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import java.time.YearMonth

object FinanceActions {
    fun prepareSave(original: Item, items: List<Item>): List<Item> {
        val alias = mapOf("recurrenceStart" to "startDate", "recurrenceEnd" to "endDate", "recurrenceCount" to "occurrenceCount", "recurrenceDay" to "dayOfMonth", "recurrenceWeekdays" to "weekdays")
        val fields = original.fields.toMutableMap()
        if (items.any { it.id == original.id } && FinancialDomain.legacyCardCash(original)) fields["legacyCardCash"] = "yes"
        alias.forEach { (old, new) -> if (fields[old]?.isNotBlank() == true && fields[new].isNullOrBlank()) fields[new] = fields.getValue(old) }
        var item = FinancialDomain.normalize(original.copy(fields = fields))
        item = applyRules(item, items)
        FinancialDomain.validate(item, items)
        if (item.type in FinancialDomain.transactionTypes && item.value("source").isBlank() && item.value("recurrenceRuleId").isBlank() && item.value("recurrence") in setOf("yes", "Sim", "true")) {
            val rule = item.copy(type = "recurring_rule", done = false, fields = item.fields + mapOf("transactionType" to item.type, "startDate" to item.value("startDate").ifBlank { item.date }, "paused" to "no"))
            RecurrenceEngine.validate(rule)
            val first = RecurrenceEngine.occurrence(rule, LocalDate.parse(rule.value("startDate")))
            return if (FinancialDomain.settled(item)) listOf(rule, settle(first, LocalDate.parse(item.value("settledDate").ifBlank { item.date }))) else listOf(rule)
        }
        if (item.type == "expense" && (item.value("installments").toIntOrNull() ?: 1) > 1 && item.value("installmentPlanId").isBlank()) {
            val firstDate = LocalDate.parse(item.value("firstInstallment").ifBlank { item.date })
            return CardEngine.installments(item, item.value("installments").toInt(), firstDate, items.firstOrNull { it.id == item.value("card") })
        }
        if (RecurrenceEngine.isRule(item)) RecurrenceEngine.validate(item)
        if (item.type == "expense" && item.value("card").isNotBlank() && item.value("paymentType") != "card_payment" && item.value("dueDate").isBlank()) {
            items.firstOrNull { it.id == item.value("card") && it.type == "card" }?.let { card ->
                item = item.copy(fields = item.fields + ("dueDate" to CardEngine.cycleDueDate(LocalDate.parse(item.date), card).toString()))
            }
        }
        return listOf(item.copy(fields = item.fields - "virtual"))
    }
    fun settle(item: Item, date: LocalDate = LocalDate.now()): Item {
        require(FinancialDomain.active(item)) { "O lançamento está cancelado ou excluído" }
        return FinancialDomain.normalize(item.copy(done = true, fields = item.fields + mapOf("status" to if (item.type == "income") "received" else "paid",
            "planned" to "Não", "settledDate" to date.toString(), "virtual" to "no")))
    }
    fun transfer(amountMinor: Long, from: Item, to: Item, date: LocalDate = LocalDate.now(), title: String = "Transferência"): Item {
        val item = Item(type = "transfer", title = title, date = date.toString(), fields = mapOf("amountMinor" to amountMinor.toString(),
            "currency" to FinancialDomain.currency(from), "account" to from.id, "destination" to to.id, "status" to "paid"))
        FinancialDomain.validate(item, listOf(from, to))
        return FinancialDomain.normalize(item)
    }
    fun applyRules(item: Item, items: List<Item>): Item {
        val rule = items.filter { it.type == "financial_rule" && it.deletedAt == 0L && !it.done && it.value("enabled") != "no" && it.value("contains").isNotBlank() }
            .sortedBy { it.value("priority").toIntOrNull() ?: 0 }.firstOrNull {
                val kind = it.value("transactionType").ifBlank { it.value("kind") }
                item.title.contains(it.value("contains"), true) && (kind.isBlank() || kind == item.type)
            } ?: return item
        return item.copy(fields = item.fields + listOf("category", "subcategory", "account", "card", "paymentMethod", "essential", "costCenter")
            .filter { rule.value(it).isNotBlank() && (item.value(it).isBlank() || rule.value("overwrite") == "yes") }.associateWith { rule.value(it) })
    }
    fun suggestions(title: String, items: List<Item>, type: String): List<Item> = items.filter {
        it.type == type && it.deletedAt == 0L && it.title.contains(title, true)
    }.sortedByDescending { it.createdAt }.distinctBy { it.title.lowercase() }.take(5)
    fun template(item: Item): Item = item.copy(id = "template:${item.id}", type = "financial_template", fields = item.fields + ("transactionType" to item.type))
    fun useTemplate(template: Item, date: LocalDate = LocalDate.now()): Item = Item(type = template.value("transactionType").ifBlank { "expense" },
        title = template.title, notes = template.notes, date = date.toString(), tags = template.tags, fields = template.fields - setOf("financialVersion", "settledDate", "source", "occurrenceDate", "recurrenceRuleId", "virtual"))
    fun recordDebtPayment(debt: Item, amountMinor: Long, account: Item, date: LocalDate = LocalDate.now()): List<Item> {
        require(debt.type == "debt" && FinancialDomain.active(debt))
        val paid = FinancialDomain.amount(debt, "paid")
        require(amountMinor in 1..(FinancialDomain.amount(debt) - paid)) { "O pagamento supera o saldo devedor" }
        require(FinancialDomain.currency(debt) == FinancialDomain.currency(account)) { "Use a mesma moeda da dívida" }
        val updated = FinancialDomain.normalize(debt.copy(done = paid + amountMinor == FinancialDomain.amount(debt), fields = debt.fields + mapOf(
            "paidMinor" to Math.addExact(paid, amountMinor).toString(), "paid" to FinancialDomain.decimal(Math.addExact(paid, amountMinor), FinancialDomain.currency(debt)))))
        val receivable = debt.value("direction") in setOf("A receber", "receivable")
        val payment = FinancialDomain.normalize(Item(id = "debt-payment:${debt.id}:${paid + amountMinor}", type = if (receivable) "income" else "expense", title = "Quitação • ${debt.title.take(180)}",
            date = date.toString(), fields = mapOf("amountMinor" to amountMinor.toString(), "currency" to FinancialDomain.currency(debt),
                "account" to account.id, "status" to if (receivable) "received" else "paid", "category" to if (receivable) "Reembolso" else "Dívidas", "debtId" to debt.id)))
        FinancialDomain.validate(payment, listOf(account))
        return listOf(updated, payment)
    }
    fun trash(item: Item, items: List<Item>, now: Long = System.currentTimeMillis()): List<Item> {
        require(item.deletedAt == 0L)
        val changes = mutableListOf(item.copy(deletedAt = now, fields = item.fields + ("trashGroup" to item.id)))
        if (item.type == "installment_plan") changes += items.filter { it.deletedAt == 0L && it.value("installmentPlanId") == item.id }
            .map { it.copy(deletedAt = now, fields = it.fields + ("trashGroup" to item.id)) }
        if(RecurrenceEngine.isRule(item))changes+=items.distinctBy{it.id}.filter{it.deletedAt==0L && !FinancialDomain.settled(it) && (it.value("source")==item.id || it.value("recurrenceRuleId")==item.id)}
            .map{it.copy(deletedAt=now,fields=it.fields+("trashGroup" to item.id))}
        if (item.value("debtId").isNotBlank()) {
            val debt = items.firstOrNull { it.id == item.value("debtId") && it.type == "debt" && it.deletedAt == 0L }
            require(debt != null) { "A dívida relacionada precisa estar disponível para excluir este pagamento" }
            val paid = Math.subtractExact(FinancialDomain.amount(debt, "paid"), FinancialDomain.amount(item))
            require(paid >= 0) { "A quitação registrada diverge deste pagamento. Confira o histórico antes de excluir." }
            changes += FinancialDomain.normalize(debt.copy(done = false, fields = debt.fields + mapOf("paidMinor" to paid.toString(), "paid" to FinancialDomain.decimal(paid, FinancialDomain.currency(debt)))))
        }
        return changes
    }
    fun restore(item: Item, items: List<Item>): List<Item> {
        val current = items.firstOrNull { it.id == item.id } ?: item
        require(current.value("purged") != "yes") { "Este registro foi excluído definitivamente" }
        require(current.deletedAt != 0L) { "Este registro já está ativo" }
        val changes = mutableListOf(current.copy(deletedAt = 0L, fields = current.fields - "trashGroup"))
        if (current.type == "installment_plan") changes += items.filter { it.deletedAt != 0L && it.value("purged") != "yes" && it.value("installmentPlanId") == current.id && it.value("trashGroup") == current.id }
            .map { it.copy(deletedAt = 0L, fields = it.fields - "trashGroup") }
        if(RecurrenceEngine.isRule(current))changes+=items.distinctBy{it.id}.filter{it.deletedAt!=0L && it.value("purged")!="yes" && it.value("trashGroup")==current.id && (it.value("source")==current.id || it.value("recurrenceRuleId")==current.id)}
            .map{it.copy(deletedAt=0L,fields=it.fields-"trashGroup")}
        if (current.value("debtId").isNotBlank()) {
            val debt = items.firstOrNull { it.id == current.value("debtId") && it.type == "debt" && it.deletedAt == 0L }
            require(debt != null) { "Restaure primeiro a dívida relacionada" }
            val paid = Math.addExact(FinancialDomain.amount(debt, "paid"), FinancialDomain.amount(current))
            require(paid <= FinancialDomain.amount(debt)) { "Restaurar este pagamento ultrapassa a dívida. Confira as quitações posteriores." }
            changes += FinancialDomain.normalize(debt.copy(done = paid == FinancialDomain.amount(debt), fields = debt.fields + mapOf("paidMinor" to paid.toString(), "paid" to FinancialDomain.decimal(paid, FinancialDomain.currency(debt)))))
        }
        val restoredIds = changes.map { it.id }.toSet()
        val context = items.filterNot { it.id in restoredIds } + changes
        changes.forEach { FinancialDomain.validateReferences(it, context) }
        return changes
    }
    fun snapshot(items: List<Item>, date: LocalDate = LocalDate.now(), currency: String = "BRL"): Item = Item(
        id = "networth:$currency:$date", type = "networth_snapshot", title = "Patrimônio • $date", date = date.toString(),
        fields = mapOf("currency" to currency, "netWorthMinor" to FinanceEngine.netWorth(items, date, currency).toString()))
    fun closeMonth(items: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1): List<Item> {
        val close = FinancialAnalytics.monthClose(items, month, today, currency, financialDay)
        return listOf(Item(id = "month-close:$currency:$month:$financialDay", type = "month_close", title = "Fechamento • $month",
            date = close.summary.period.end.toString(), fields = mapOf("currency" to currency, "incomeMinor" to close.summary.incomeMinor.toString(),
                "expenseMinor" to close.summary.expenseMinor.toString(), "savingsMinor" to close.summary.savingsMinor.toString(),
                "netWorthMinor" to close.summary.netWorthMinor.toString(), "largestCategory" to close.largestCategory.orEmpty(),
                "periodStart" to close.summary.period.start.toString(), "periodEnd" to close.summary.period.end.toString(), "closedAt" to today.toString())),
            snapshot(items, minOf(today, close.summary.period.end), currency))
    }
}
