package app.veyra.feature.finance

import app.veyra.model.Item
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Currency

enum class TransactionStatus(val label: String) {
    RECEIVED("Recebido"), PAID("Pago"), PENDING("Pendente"), EXPECTED("Previsto"),
    OVERDUE("Atrasado"), CANCELLED("Cancelado")
}

/** The adapter accepts the existing schema without deleting any original field. */
object FinancialDomain {
    val transactionTypes = setOf("income", "expense", "transfer")
    val financialTypes = transactionTypes + setOf("account", "card", "budget", "subscription",
        "recurring_rule", "recurrence_exception", "installment_plan", "investment", "debt",
        "savings_goal", "bill", "financial_category", "financial_rule", "financial_template",
        "cash_carry", "card_carry", "networth_snapshot", "month_close", "financial_asset")
    const val MAX_MINOR = 900_000_000_000_000L
    private val monetaryFields = listOf("amount", "opening", "limit", "current", "saved", "paid", "payment", "monthlyContribution", "installmentAmount")
    fun currency(item: Item): String = item.value("currency").ifBlank { "BRL" }
    fun scale(currency: String): Int = Currency.getInstance(currency).defaultFractionDigits.also {
        require(it in 0..3) { "Moeda sem precisão monetária suportada" }
    }
    fun parseMinor(value: String, currency: String = "BRL"): Long {
        val text = value.trim().replace("R$", "").replace(" ", "")
        val normalized = when {
            ',' in text && '.' in text && text.lastIndexOf('.') > text.lastIndexOf(',') -> {
                require(text.matches(Regex("[+-]?\\d{1,3}(,\\d{3})+\\.\\d+"))) { "Separadores monetários inválidos" }
                text.replace(",", "")
            }
            ',' in text -> {
                require(text.matches(Regex("[+-]?(\\d+|\\d{1,3}(\\.\\d{3})+),\\d+"))) { "Separadores monetários inválidos" }
                text.replace(".", "").replace(',', '.')
            }
            currency == "BRL" && value.contains("R$") && text.matches(Regex("[+-]?\\d{1,3}(\\.\\d{3})+")) -> text.replace(".", "")
            else -> text
        }
        require(normalized.matches(Regex("[+-]?\\d+(\\.\\d+)?"))) { "Valor monetário inválido" }
        return BigDecimal(normalized).setScale(scale(currency), RoundingMode.UNNECESSARY)
            .movePointRight(scale(currency)).longValueExact().also { require(it in -MAX_MINOR..MAX_MINOR) { "Valor muito alto" } }
    }
    fun amount(item: Item): Long = amount(item, "amount")
    fun amount(item: Item, key: String): Long {
        val minorKey = if (key == "amount") "amountMinor" else "${key}Minor"
        if (item.value(minorKey).isNotBlank()) return item.value(minorKey).toLong()
        return if (item.value(key).isBlank()) 0L else parseMinor(item.value(key), currency(item))
    }
    fun decimal(minor: Long, currency: String = "BRL") = BigDecimal.valueOf(minor, scale(currency)).toPlainString()
    fun budgetThresholds(item: Item): List<Int> {
        if (item.value("thresholds").isBlank()) return listOf(50, 75, 90, 100)
        require(item.value("thresholds").length <= 1000) { "A lista de alertas do orçamento é muito longa" }
        return item.value("thresholds").split(',').map { value ->
            val percent = value.trim().toIntOrNull()
            require(percent != null && percent in 1..100) { "Informe percentuais inteiros de 1 a 100 separados por vírgula" }
            percent
        }.distinct().sorted()
    }
    fun dueDate(item: Item): LocalDate = LocalDate.parse(item.value("dueDate").ifBlank { item.date })
    fun transactionDate(item: Item): LocalDate = LocalDate.parse(item.date)
    fun bookedDate(item: Item): LocalDate = LocalDate.parse(item.value("settledDate").ifBlank { item.date })
    fun storedStatus(item: Item): TransactionStatus {
        val value = item.value("status").lowercase()
        return when (value) {
            "received", "recebido", "recebida" -> if (item.type == "income") TransactionStatus.RECEIVED else TransactionStatus.PAID
            "paid", "pago", "paga", "realizado", "realizada" -> if (item.type == "income") TransactionStatus.RECEIVED else TransactionStatus.PAID
            "pending", "pendente" -> TransactionStatus.PENDING
            "expected", "previsto", "prevista" -> TransactionStatus.EXPECTED
            "overdue", "atrasado", "atrasada" -> TransactionStatus.OVERDUE
            "cancelled", "canceled", "cancelado", "cancelada" -> TransactionStatus.CANCELLED
            else -> if (value.isNotBlank() || (item.type == "bill" && !item.done) || (item.value("planned") == "Sim" && !item.done)) {
                if (item.type == "income") TransactionStatus.EXPECTED else TransactionStatus.PENDING
            } else if (item.type == "income") TransactionStatus.RECEIVED else TransactionStatus.PAID
        }
    }
    fun status(item: Item, today: LocalDate = LocalDate.now()): TransactionStatus {
        val status = storedStatus(item)
        return if (status in setOf(TransactionStatus.PENDING, TransactionStatus.EXPECTED) &&
            item.date.isNotBlank() && dueDate(item).isBefore(today)) TransactionStatus.OVERDUE else status
    }
    fun settled(item: Item) = storedStatus(item) in setOf(TransactionStatus.RECEIVED, TransactionStatus.PAID)
    fun legacyCardCash(item: Item) = item.value("legacyCardCash") == "yes" || item.type == "expense" && item.value("card").isNotBlank() && item.value("financialVersion") != "3" && settled(item)
    fun active(item: Item) = item.deletedAt == 0L && storedStatus(item) != TransactionStatus.CANCELLED
    fun cashDelta(item: Item): Long {
        if (!active(item) || !settled(item)) return 0
        val amount = amount(item)
        return when (item.type) {
            "income" -> amount
            "expense" -> if (item.value("card").isBlank() || legacyCardCash(item) || item.value("paymentType") == "card_payment") -amount else 0
            else -> 0
        }
    }
    fun accountDelta(item: Item, accountId: String): Long {
        if (!active(item) || !settled(item)) return 0
        if (item.type == "transfer") return when (accountId) {
            item.value("account") -> -amount(item)
            item.value("destination") -> amount(item)
            else -> 0
        }
        return if (item.value("account") == accountId) cashDelta(item) else 0
    }
    fun isExpense(item: Item) = active(item) && item.type == "expense" && item.value("paymentType") != "card_payment"
    fun normalize(item: Item): Item {
        if (item.type !in financialTypes || item.type in setOf("cash_carry", "card_carry")) return item
        if (item.type in transactionTypes || item.type == "bill") require(item.value("status").isBlank() || item.value("status").lowercase() in setOf(
            "received", "recebido", "recebida", "paid", "pago", "paga", "realizado", "realizada", "pending", "pendente", "expected", "previsto", "prevista", "overdue", "atrasado", "atrasada", "cancelled", "canceled", "cancelado", "cancelada")) { "Status financeiro inválido" }
        val fields = item.fields.toMutableMap()
        fields["financialVersion"] = "3"
        fields["currency"] = currency(item)
        if (item.type == "budget") fields["thresholds"] = budgetThresholds(item).joinToString(",")
        for (key in monetaryFields) {
            if (item.value(key).isNotBlank() || item.value(if (key == "amount") "amountMinor" else "${key}Minor").isNotBlank()) {
                val minor = amount(item, key)
                fields[if (key == "amount") "amountMinor" else "${key}Minor"] = minor.toString()
                fields[key] = decimal(minor, currency(item))
            }
        }
        if (item.type in transactionTypes || item.type == "bill") {
            val state = storedStatus(item)
            fields["status"] = state.name.lowercase()
            fields["planned"] = if (state in setOf(TransactionStatus.PAID, TransactionStatus.RECEIVED)) "Não" else "Sim"
        }
        return item.copy(fields = fields)
    }
    fun validate(item: Item, workspace: List<Item> = emptyList()) {
        if (item.type !in financialTypes) return
        require(item.title.isNotBlank() && item.title.length <= 200) { "Preencha a descrição (até 200 caracteres)" }
        scale(currency(item))
        if (item.date.isNotBlank()) LocalDate.parse(item.date)
        for (key in listOf("dueDate", "settledDate", "firstInstallment", "endDate", "startDate", "targetDate")) if (item.value(key).isNotBlank()) LocalDate.parse(item.value(key))
        for (key in listOf("time", "reminder")) if (item.value(key).isNotBlank()) LocalTime.parse(item.value(key))
        if (item.value("competence").isNotBlank()) YearMonth.parse(item.value("competence"))
        for (key in monetaryFields) {
            val value = amount(item, key)
            require(value in -MAX_MINOR..MAX_MINOR) { "Valor muito alto" }
            require(key == "opening" || item.type in setOf("cash_carry", "networth_snapshot", "month_close") || value >= 0) { "Use um valor positivo" }
        }
        if (item.type in transactionTypes) require(amount(item) > 0) { "O valor precisa ser maior que zero" }
        if (item.type == "transfer") {
            require(item.value("account").isNotBlank() && item.value("destination").isNotBlank() && item.value("account") != item.value("destination")) { "Escolha duas contas diferentes" }
        }
        if (item.type == "card") for (key in listOf("closing", "due")) require(item.value(key).toIntOrNull() in 1..31) { "Fechamento e vencimento precisam ficar entre 1 e 31" }
        if (item.type == "savings_goal") require(amount(item, "saved") <= amount(item)) { "A reserva supera o objetivo" }
        if (item.type == "budget") budgetThresholds(item)
        if (item.type == "debt") require(amount(item, "paid") <= amount(item)) { "A quitação supera a dívida" }
        if (item.type == "debt" && item.value("interest").isNotBlank()) require(item.value("interest").replace(',', '.').toBigDecimal() in BigDecimal.ZERO..BigDecimal(100)) { "Juros mensais devem ficar entre 0 e 100%" }
        if (item.value("lastFour").isNotBlank()) require(item.value("lastFour").matches(Regex("\\d{4}"))) { "Informe exatamente os quatro últimos dígitos" }
        if (item.value("installments").isNotBlank()) require(item.value("installments").toIntOrNull() in 1..120) { "Use de 1 a 120 parcelas" }
        if (item.value("attachment").isNotBlank()) require(item.value("attachment").length <= 14_000_000) { "Anexo excede 10 MB" }
        if (workspace.isNotEmpty()) validateReferences(item, workspace)
    }
    /** Restore validates ownership/currency without rewriting historical cash or rejecting legacy optional fields. */
    fun validateReferences(item: Item, workspace: List<Item>) {
        for (key in listOf("account", "destination", "card")) if (item.value(key).isNotBlank()) {
            val ref = workspace.firstOrNull { it.id == item.value(key) && it.deletedAt == 0L }
            require(ref != null && ref.type == if (key == "card") "card" else "account") { "${if (key == "card") "Cartão" else "Conta"} não encontrado" }
            require(currency(ref) == currency(item)) { "Transferências e lançamentos precisam usar a moeda da conta/cartão" }
        }
    }
}

object FinancialCategories {
    val income = listOf("Salário", "Freelance", "Comissão", "Venda", "Cashback", "Reembolso", "Rendimentos", "Investimentos", "Presente", "Outros")
    val expense = listOf("Alimentação", "Mercado", "Transporte", "Combustível", "Casa", "Aluguel", "Energia", "Água", "Internet", "Telefone", "Assinaturas", "Compras", "Lazer", "Saúde", "Educação", "Viagem", "Impostos", "Dívidas", "Cartão", "Investimentos", "Outros")
    fun categories(items: List<Item>, type: String): List<String> = ((if (type == "income") income else expense) +
        items.filter { it.deletedAt == 0L && ((it.type == "financial_category" && it.value("transactionType").ifBlank { it.value("kind") }.let { kind -> kind.isBlank() || kind == type }) || it.type == type) }
            .map { if (it.type == "financial_category") it.value("parent").ifBlank { it.value("category").ifBlank { it.title } } else it.value("category") }).filter(String::isNotBlank).distinct()
    fun subcategories(items: List<Item>, category: String): List<String> = items.filter { it.deletedAt == 0L && it.value("parent").ifBlank { it.value("category") } == category }
        .map { it.value("subcategory").ifBlank { if (it.type == "financial_category") it.title else "" } }.filter(String::isNotBlank).distinct()
}
