package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.math.BigDecimal
import java.text.Normalizer

data class FinancialPeriod(val start: LocalDate, val end: LocalDate)
data class FinancialSummary(val currentBalance: Long, val incomeMinor: Long, val expenseMinor: Long,
    val projectedBalance: Long, val savingsMinor: Long, val savingsRate: Double?, val payableMinor: Long,
    val receivableMinor: Long, val cardInvoicesMinor: Long, val budgetUsedMinor: Long, val budgetLimitMinor: Long,
    val netWorthMinor: Long, val previousIncomeMinor: Long, val previousExpenseMinor: Long,
    val expenseChangePercent: Double?, val period: FinancialPeriod)
data class CashPoint(val date: LocalDate, val balanceMinor: Long, val incomeMinor: Long, val expenseMinor: Long, val descriptions: List<String>)
data class CashProjection(val currentBalance: Long, val expectedIncome: Long, val expectedExpense: Long,
    val projectedBalance: Long, val points: List<CashPoint>, val firstNegativeDate: LocalDate?,
    val virtualLookbackStart: LocalDate = points.firstOrNull()?.date ?: LocalDate.now())
data class BudgetProgress(val budget: Item, val usedMinor: Long, val limitMinor: Long, val percent: Double, val threshold: Int)
data class GoalProgress(val goal: Item, val currentMinor: Long, val targetMinor: Long, val remainingMinor: Long,
    val percent: Double, val estimatedCompletion: LocalDate?)
data class SubscriptionCost(val item: Item, val monthlyMinor: Long, val annualMinor: Long, val nextCharge: LocalDate?)
data class FinancialFilter(val from: LocalDate? = null, val through: LocalDate? = null, val type: String = "",
    val category: String = "", val account: String = "", val card: String = "", val minMinor: Long? = null,
    val maxMinor: Long? = null, val status: TransactionStatus? = null, val recurring: Boolean? = null, val installment: Boolean? = null)

object FinanceEngine {
    fun alerts(items: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1) = FinancialAnalytics.alerts(items, today, currency, financialDay)
    fun insights(items: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1) = FinancialAnalytics.insights(items, month, today, currency, financialDay)
    fun health(items: List<Item>, month: YearMonth = YearMonth.now(), today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1) = FinancialAnalytics.health(items, month, today, currency, financialDay)
    fun monthClose(items: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1) = FinancialAnalytics.monthClose(items, month, today, currency, financialDay)
    private fun sum(items: List<Item>, amount: (Item) -> Long = FinancialDomain::amount) = items.fold(0L) { total, item -> Math.addExact(total, amount(item)) }
    /** A database window explicitly supplies its historical cutoff; existing overdue records are never cut off. */
    fun forecastStart(items: List<Item>, today: LocalDate = LocalDate.now()): LocalDate {
        val cutoff = items.filter { it.type == "cash_carry" && it.date.isNotBlank() }.map { LocalDate.parse(it.date).plusDays(1) }.minOrNull()
        if (cutoff != null) return minOf(today, cutoff)
        return items.filter { RecurrenceEngine.isRule(it) && it.deletedAt == 0L &&
            (!it.done && it.value("paused") !in setOf("yes", "true", "Sim") || it.value("pausedAt").isNotBlank()) }
            .map { LocalDate.parse(it.value("startDate").ifBlank { it.date }) }.minOrNull()?.let { minOf(today, it) } ?: today
    }
    fun period(month: YearMonth, financialDay: Int = 1): FinancialPeriod {
        require(financialDay in 1..31)
        val start = month.atDay(financialDay.coerceAtMost(month.lengthOfMonth()))
        val next = month.plusMonths(1)
        return FinancialPeriod(start, next.atDay(financialDay.coerceAtMost(next.lengthOfMonth())).minusDays(1))
    }
    private fun expanded(items: List<Item>, from: LocalDate, through: LocalDate): List<Item> {
        val existing = items.filter { it.type in FinancialDomain.transactionTypes }
        val bills = items.filter { it.type == "bill" && FinancialDomain.active(it) && !it.done && it.date.isNotBlank() &&
            existing.none { payment -> payment.id == "bill-payment:${it.id}" && FinancialDomain.active(payment) } }.map {
            it.copy(id = "bill-payment:${it.id}", type = "expense", fields = it.fields + mapOf("status" to "pending", "planned" to "Sim", "source" to it.id, "virtual" to "yes"))
        }
        return (existing + RecurrenceEngine.occurrences(items, from, through) + bills).distinctBy { it.id }.filter(FinancialDomain::active).map { item ->
            val card = items.firstOrNull { it.type == "card" && it.id == item.value("card") }
            if (card != null && item.value("virtual") == "yes" && item.type == "expense") item.copy(fields = item.fields +
                ("dueDate" to CardEngine.cycleDueDate(LocalDate.parse(item.date), card).toString())) else item
        }
    }
    fun cardInvoices(items: List<Item>, card: Item, from: LocalDate, through: LocalDate): List<CardInvoice> =
        CardEngine.invoices(expanded(items, from.minusMonths(2), through) + items.filter { it.type == "card_carry" }, card, from, through)
    /** Predicted future subscriptions are not purchases yet; already registered installments remain liabilities. */
    fun cardUsedLimit(items: List<Item>, card: Item, today: LocalDate = LocalDate.now()): Long =
        CardEngine.usedLimit(expanded(items, forecastStart(items, today), today) + items.filter { it.type == "card_carry" }, card)
    fun cardAvailableLimit(items: List<Item>, card: Item, today: LocalDate = LocalDate.now()): Long =
        (FinancialDomain.amount(card, "limit") - cardUsedLimit(items, card, today)).coerceAtLeast(0)
    fun transactions(items: List<Item>, from: LocalDate, through: LocalDate, currency: String = "BRL"): List<Item> =
        expanded(items, from, through).filter { FinancialDomain.currency(it) == currency && it.date >= from.toString() && it.date <= through.toString() }.sortedByDescending { it.date }
    fun currentBalance(items: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL", carry: Long = 0L): Long {
        val opening = sum(items.filter { it.type == "account" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency }) { FinancialDomain.amount(it, "opening") }
        val historical = sum(items.filter { it.type == "cash_carry" && FinancialDomain.currency(it) == currency })
        val cutoff = items.filter { it.type == "cash_carry" && FinancialDomain.currency(it) == currency }.mapNotNull { it.date.takeIf(String::isNotBlank)?.let(LocalDate::parse) }.maxOrNull()
        val changes = sum(items.filter { it.type in FinancialDomain.transactionTypes && FinancialDomain.currency(it) == currency &&
            it.date.isNotBlank() && !FinancialDomain.bookedDate(it).isAfter(today) && (cutoff == null || FinancialDomain.bookedDate(it).isAfter(cutoff)) }, FinancialDomain::cashDelta)
        return Math.addExact(Math.addExact(opening, historical), Math.addExact(carry, changes))
    }
    fun accountBalance(items: List<Item>, account: Item, today: LocalDate = LocalDate.now(), currency: String = FinancialDomain.currency(account), carry: Long = 0L): Long {
        val history = sum(items.filter { it.type == "cash_carry" && FinancialDomain.currency(it) == currency && it.value("account") == account.id })
        val cutoff = items.filter { it.type == "cash_carry" && FinancialDomain.currency(it) == currency }.mapNotNull { it.date.takeIf(String::isNotBlank)?.let(LocalDate::parse) }.maxOrNull()
        val movement = sum(items.filter { FinancialDomain.currency(it) == currency && it.date.isNotBlank() &&
            it.type in FinancialDomain.transactionTypes && !FinancialDomain.bookedDate(it).isAfter(today) && (cutoff == null || FinancialDomain.bookedDate(it).isAfter(cutoff)) }) { FinancialDomain.accountDelta(it, account.id) }
        return Math.addExact(Math.addExact(FinancialDomain.amount(account, "opening"), history), Math.addExact(carry, movement))
    }
    fun accountProjection(items: List<Item>, account: Item, today: LocalDate = LocalDate.now(), through: LocalDate = today.plusDays(30)): Long = accountCashProjection(items, account, today, through).projectedBalance
    fun accountCashProjection(items: List<Item>, account: Item, today: LocalDate = LocalDate.now(), through: LocalDate = today.plusDays(30)): CashProjection {
        require(!through.isBefore(today))
        val start = forecastStart(items, today)
        require(ChronoUnit.DAYS.between(start, through) <= 3660) { "O histórico de recorrências excede dez anos. Carregue uma janela financeira e confira os vencimentos anteriores." }
        val currency = FinancialDomain.currency(account)
        val current = accountBalance(items, account, today)
        data class Event(val date: LocalDate, val delta: Long, val title: String)
        val events = expanded(items, start, through).filter { FinancialDomain.currency(it) == currency &&
            ((!FinancialDomain.settled(it) && !FinancialDomain.dueDate(it).isAfter(through)) ||
                (FinancialDomain.settled(it) && FinancialDomain.bookedDate(it).isAfter(today) && !FinancialDomain.bookedDate(it).isAfter(through))) &&
            (it.value("card").isBlank() || FinancialDomain.legacyCardCash(it) || it.value("paymentType") == "card_payment") }.mapNotNull { item ->
                val value = FinancialDomain.amount(item)
                val delta = when {
                    item.type == "transfer" && item.value("account") == account.id -> -value
                    item.type == "transfer" && item.value("destination") == account.id -> value
                    item.value("account") != account.id -> 0
                    item.type == "income" -> value
                    item.type == "expense" -> -value
                    else -> 0
                }
                if (delta == 0L) null else Event(maxOf(today, if (FinancialDomain.settled(item)) FinancialDomain.bookedDate(item) else FinancialDomain.dueDate(item)), delta, item.title)
            }.toMutableList()
        items.filter { it.type == "card" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency && it.value("account") == account.id }.forEach { card ->
            CardEngine.invoices(expanded(items, start, through) + items.filter { it.type == "card_carry" }, card, LocalDate.of(1900, 1, 1), through)
                .filter { it.outstandingMinor > 0L }.forEach { events += Event(maxOf(today, it.dueDate), -it.outstandingMinor, "Fatura ${card.title}") }
        }
        var balance = current
        val points = mutableListOf(CashPoint(today, current, 0, 0, listOf("Saldo atual")))
        events.groupBy { it.date }.toSortedMap().forEach { (date, rows) ->
            val input = rows.filter { it.delta > 0 }.fold(0L) { total, event -> Math.addExact(total, event.delta) }
            val output = rows.filter { it.delta < 0 }.fold(0L) { total, event -> Math.addExact(total, -event.delta) }
            balance = Math.subtractExact(Math.addExact(balance, input), output)
            points += CashPoint(date, balance, input, output, rows.map { it.title })
        }
        if (points.last().date != through) points += CashPoint(through, balance, 0, 0, emptyList())
        return CashProjection(current, events.filter { it.delta > 0L }.fold(0L) { total, event -> Math.addExact(total, event.delta) },
            events.filter { it.delta < 0L }.fold(0L) { total, event -> Math.addExact(total, -event.delta) }, balance, points, points.firstOrNull { it.balanceMinor < 0L }?.date, start)
    }
    fun projection(items: List<Item>, today: LocalDate, through: LocalDate, currency: String = "BRL", carry: Long = 0L): CashProjection {
        require(!through.isBefore(today) && ChronoUnit.DAYS.between(today, through) <= 3660)
        val start = forecastStart(items, today)
        require(ChronoUnit.DAYS.between(start, through) <= 3660) { "O histórico de recorrências excede dez anos. Carregue uma janela financeira e confira os vencimentos anteriores." }
        val current = currentBalance(items, today, currency, carry)
        val events = expanded(items, start, through).filter { FinancialDomain.currency(it) == currency &&
            ((!FinancialDomain.settled(it) && !FinancialDomain.dueDate(it).isAfter(through)) ||
                (FinancialDomain.settled(it) && FinancialDomain.bookedDate(it).isAfter(today) && !FinancialDomain.bookedDate(it).isAfter(through))) }
            .filter { it.type != "expense" || it.value("card").isBlank() || FinancialDomain.legacyCardCash(it) || it.value("paymentType") == "card_payment" }
            .filter { it.type != "transfer" }
        data class Event(val date: LocalDate, val amount: Long, val income: Boolean, val description: String)
        val flow = events.map { Event(maxOf(today, if (FinancialDomain.settled(it)) FinancialDomain.bookedDate(it) else FinancialDomain.dueDate(it)),
            FinancialDomain.amount(it), it.type == "income", it.title) }.toMutableList()
        items.filter { it.type == "card" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency }.forEach { card ->
            val expanded = expanded(items, start, through) + items.filter { it.type == "card_carry" }
            CardEngine.invoices(expanded, card, LocalDate.of(1900, 1, 1), through).filter { it.outstandingMinor > 0 }.forEach { invoice ->
                flow += Event(maxOf(today, invoice.dueDate), invoice.outstandingMinor, false, "Fatura ${card.title}")
            }
        }
        val income = flow.filter { it.income }.fold(0L) { total, event -> Math.addExact(total, event.amount) }
        val expense = flow.filter { !it.income }.fold(0L) { total, event -> Math.addExact(total, event.amount) }
        var balance = current
        val points = mutableListOf(CashPoint(today, current, 0, 0, listOf("Saldo atual")))
        flow.groupBy { it.date }.toSortedMap().forEach { (date, daily) ->
            val input = daily.filter { it.income }.fold(0L) { total, event -> Math.addExact(total, event.amount) }
            val output = daily.filter { !it.income }.fold(0L) { total, event -> Math.addExact(total, event.amount) }
            balance = Math.subtractExact(Math.addExact(balance, input), output)
            points += CashPoint(date, balance, input, output, daily.map { it.description })
        }
        if (points.last().date != through) points += CashPoint(through, balance, 0, 0, emptyList())
        return CashProjection(current, income, expense, balance, points, points.firstOrNull { it.balanceMinor < 0 }?.date, start)
    }
    fun recognizedExpense(item: Item, today: LocalDate) = FinancialDomain.isExpense(item) &&
        (FinancialDomain.settled(item) && !FinancialDomain.bookedDate(item).isAfter(today) ||
            item.value("card").isNotBlank() && !LocalDate.parse(item.date).isAfter(today))
    fun summary(items: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1, carry: Long = 0L): FinancialSummary {
        val period = period(month, financialDay)
        val data = transactions(items, period.start, period.end, currency)
        val previousPeriod = period(month.minusMonths(1), financialDay)
        val previous = transactions(items, previousPeriod.start, previousPeriod.end, currency)
        fun received(items: List<Item>) = sum(items.filter { it.type == "income" && FinancialDomain.settled(it) && !FinancialDomain.bookedDate(it).isAfter(today) })
        fun spent(items: List<Item>) = sum(items.filter { recognizedExpense(it, today) })
        val income = received(data)
        val expense = spent(data)
        val priorIncome = received(previous)
        val priorExpense = spent(previous)
        val forecast = projection(items, today, maxOf(today, period.end), currency, carry)
        val budget = budgets(items, period.start, period.end, currency, today)
        val cardTotal = items.filter { it.type == "card" && FinancialDomain.currency(it) == currency && it.deletedAt == 0L }.fold(0L) { total, card ->
            Math.addExact(total, cardInvoices(items, card, period.start, period.end).fold(0L) { a, invoice -> Math.addExact(a, invoice.outstandingMinor) })
        }
        val savings = Math.subtractExact(income, expense)
        return FinancialSummary(currentBalance(items, today, currency, carry), income, expense, forecast.projectedBalance, savings,
            if (income > 0) savings.toDouble() / income * 100 else null, forecast.expectedExpense, forecast.expectedIncome,
            cardTotal, budget.fold(0L) { a, b -> Math.addExact(a, b.usedMinor) }, budget.fold(0L) { a, b -> Math.addExact(a, b.limitMinor) },
            netWorth(items, today, currency, carry), priorIncome, priorExpense, if (priorExpense > 0) (expense.toDouble() / priorExpense - 1) * 100 else null, period)
    }
    fun budgets(items: List<Item>, from: LocalDate, through: LocalDate, currency: String = "BRL", today: LocalDate = LocalDate.now()): List<BudgetProgress> {
        val transactions = transactions(items, from, through, currency).filter { recognizedExpense(it, today) }
        return items.filter { it.type == "budget" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency &&
            (it.value("month").isBlank() || it.value("month") == YearMonth.from(from).toString()) }.map { budget ->
            val used = sum(transactions.filter { it.value("category").equals(budget.value("category"), true) &&
                (budget.value("subcategory").isBlank() || it.value("subcategory") == budget.value("subcategory")) })
            val limit = FinancialDomain.amount(budget)
            val percent = if (limit > 0) used.toDouble() / limit * 100 else 0.0
            val threshold = if (limit <= 0L) 0 else FinancialDomain.budgetThresholds(budget).asReversed().firstOrNull {
                BigDecimal.valueOf(used).multiply(BigDecimal(100)) >= BigDecimal.valueOf(limit).multiply(BigDecimal(it))
            } ?: 0
            BudgetProgress(budget, used, limit, percent, threshold)
        }
    }
    fun goals(items: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL"): List<GoalProgress> = items.filter {
        it.type == "savings_goal" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency
    }.map { goal ->
        val target = FinancialDomain.amount(goal)
        val saved = FinancialDomain.amount(goal, "saved")
        val remaining = (target - saved).coerceAtLeast(0)
        val monthly = FinancialDomain.amount(goal, "monthlyContribution")
        val months = if (monthly > 0L) (remaining / monthly) + if (remaining % monthly > 0) 1 else 0 else 0
        val completion = if (remaining == 0L) today else if (months in 1..1200) today.plusMonths(months) else null
        GoalProgress(goal, saved, target, remaining, if (target > 0) saved.toDouble() / target * 100 else 0.0, completion)
    }
    fun netWorth(items: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL", carry: Long = 0L): Long {
        var total = currentBalance(items, today, currency, carry)
        items.filter { it.deletedAt == 0L && FinancialDomain.currency(it) == currency }.forEach { item ->
            when (item.type) {
                "investment", "financial_asset" -> if (item.value("includedInAccount") != "yes") total = Math.addExact(total, FinancialDomain.amount(item, "current"))
                "debt" -> {
                    val remaining = (FinancialDomain.amount(item) - FinancialDomain.amount(item, "paid")).coerceAtLeast(0)
                    total = if (item.value("direction") in setOf("A receber", "receivable")) Math.addExact(total, remaining) else Math.subtractExact(total, remaining)
                }
                "card" -> total = Math.subtractExact(total, cardUsedLimit(items, item, today))
            }
        }
        return total
    }
    fun emergencyReserve(items: List<Item>, today: LocalDate = LocalDate.now(), months: Int = 6, currency: String = "BRL"): Long {
        require(months in 1..60)
        val first = YearMonth.from(today).minusMonths(3).atDay(1)
        val last = YearMonth.from(today).atDay(1).minusDays(1)
        val data = transactions(items, first, last, currency).filter { recognizedExpense(it, today) }
        val recorded = data.map { YearMonth.from(LocalDate.parse(it.date)) }.distinct().size
        if (recorded == 0) return 0
        val numerator = Math.multiplyExact(sum(data), months.toLong())
        return numerator / recorded + if (numerator % recorded > 0) 1 else 0
    }
    fun subscriptions(items: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL"): List<SubscriptionCost> = items.filter {
        RecurrenceEngine.isRule(it) && it.deletedAt == 0L && FinancialDomain.currency(it) == currency &&
            (it.type == "subscription" || it.value("category") == "Assinaturas")
    }.map { rule ->
        val occurrences = RecurrenceEngine.dates(rule, today, today.plusYears(1).minusDays(1))
        val annual = Math.multiplyExact(FinancialDomain.amount(rule), occurrences.size.toLong())
        val frequency = RecurrenceEngine.frequency(rule)
        val interval = rule.value("interval").toLongOrNull() ?: 1
        val monthly = if (occurrences.isEmpty()) 0L else when (frequency) {
            RecurrenceFrequency.MONTHLY -> FinancialDomain.amount(rule) / interval
            RecurrenceFrequency.BIMONTHLY -> FinancialDomain.amount(rule) / (2 * interval)
            RecurrenceFrequency.QUARTERLY -> FinancialDomain.amount(rule) / (3 * interval)
            RecurrenceFrequency.SEMIANNUAL -> FinancialDomain.amount(rule) / (6 * interval)
            RecurrenceFrequency.YEARLY -> FinancialDomain.amount(rule) / (12 * interval)
            else -> annual / 12
        }
        SubscriptionCost(rule, monthly, annual, occurrences.firstOrNull())
    }
    fun search(items: List<Item>, query: String, workspace: List<Item> = items): List<Item> {
        val names = workspace.associate { it.id to it.title }
        fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
        val needle = normalized(query.trim())
        return items.filter { item -> needle.isBlank() || (listOf(item.title, item.notes, item.tags, item.date, FinancialDomain.decimal(FinancialDomain.amount(item), FinancialDomain.currency(item))) +
            item.fields.filterKeys { it !in setOf("attachment", "receipt", "fileData", "thumbnail") }.values +
            listOf(names[item.value("account")].orEmpty(), names[item.value("card")].orEmpty(), names[item.value("destination")].orEmpty()))
            .any { normalized(it).contains(needle) } }
    }
    fun filter(items: List<Item>, filter: FinancialFilter, today: LocalDate = LocalDate.now()) = items.filter { item ->
        (filter.from == null || item.date >= filter.from.toString()) && (filter.through == null || item.date <= filter.through.toString()) &&
            (filter.type.isBlank() || item.type == filter.type) && (filter.category.isBlank() || item.value("category") == filter.category) &&
            (filter.account.isBlank() || item.value("account") == filter.account || item.value("destination") == filter.account) &&
            (filter.card.isBlank() || item.value("card") == filter.card) && (filter.minMinor == null || FinancialDomain.amount(item) >= filter.minMinor) &&
            (filter.maxMinor == null || FinancialDomain.amount(item) <= filter.maxMinor) && (filter.status == null || FinancialDomain.status(item, today) == filter.status) &&
            (filter.recurring == null || item.value("source").isNotBlank() == filter.recurring) &&
            (filter.installment == null || item.value("installmentPlanId").isNotBlank() == filter.installment)
    }
}
