package app.veyra.feature.finance

import app.veyra.model.Item
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class AlertPriority(val label: String) { URGENT("Urgente"), ATTENTION("Atenção"), INFORMATION("Informativo") }
data class FinancialAlert(val id: String, val priority: AlertPriority, val title: String, val description: String, val itemId: String = "")
data class FinancialInsight(val id: String, val text: String, val category: String = "")
data class HealthFactor(val label: String, val points: Int, val maximum: Int, val explanation: String)
data class FinancialHealth(val score: Int?, val label: String, val factors: List<HealthFactor>)
data class MonthClose(val summary: FinancialSummary, val largestCategory: String?, val largestCategoryMinor: Long,
    val largestExpense: Item?, val goals: List<GoalProgress>, val insights: List<FinancialInsight>)
data class DebtInstallment(val index: Int, val dueDate: LocalDate, val paymentMinor: Long, val interestMinor: Long,
    val principalMinor: Long, val remainingMinor: Long)

object FinancialAnalytics {
    private fun money(amount: Long, currency: String) = "$currency ${FinancialDomain.decimal(amount, currency)}"
    fun alerts(items: List<Item>, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1): List<FinancialAlert> {
        val result = mutableListOf<FinancialAlert>()
        val future = FinanceEngine.transactions(items, FinanceEngine.forecastStart(items, today), today.plusDays(7), currency)
        (items + future).distinctBy { it.id }.filter { it.type in FinancialDomain.transactionTypes &&
            FinancialDomain.active(it) && !FinancialDomain.settled(it) && FinancialDomain.currency(it) == currency && it.date.isNotBlank() &&
            (it.value("card").isBlank() || it.type == "income") }.forEach { item ->
            val date = FinancialDomain.dueDate(item)
            if (date.isBefore(today)) result += FinancialAlert("overdue:${item.id}", AlertPriority.URGENT,
                if (item.type == "income") "Recebimento atrasado" else "Conta atrasada", "${item.title}: ${money(FinancialDomain.amount(item), currency)} • venceu em $date", item.id)
            else if (!date.isAfter(today.plusDays(3))) result += FinancialAlert("due:${item.id}:$date", AlertPriority.ATTENTION,
                if (item.type == "income") "Entrada próxima" else "Vencimento próximo", "${item.title}: ${money(FinancialDomain.amount(item), currency)} • $date", item.id)
        }
        val month = if (today.isBefore(FinanceEngine.period(YearMonth.from(today), financialDay).start)) YearMonth.from(today).minusMonths(1) else YearMonth.from(today)
        val period = FinanceEngine.period(month, financialDay)
        FinanceEngine.budgets(items, period.start, period.end, currency, today).filter { it.threshold > 0 }.forEach { budget ->
            result += FinancialAlert("budget:${budget.budget.id}:$month:${budget.threshold}",
                if (budget.usedMinor >= budget.limitMinor) AlertPriority.URGENT else AlertPriority.ATTENTION, "Orçamento: ${budget.budget.title}",
                "${budget.percent.toInt()}% utilizados • ${money(budget.usedMinor, currency)} / ${money(budget.limitMinor, currency)}", budget.budget.id)
        }
        val forecast = FinanceEngine.projection(items, today, today.plusDays(30), currency)
        forecast.firstNegativeDate?.let { date -> result += FinancialAlert("negative:$currency:$date", AlertPriority.URGENT,
            "Saldo futuro negativo", "O fluxo registrado fica negativo em $date. Confira entradas e vencimentos.") }
        items.filter { it.type == "card" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency }.forEach { card ->
            val virtualCardItems = items + FinanceEngine.transactions(items, FinanceEngine.forecastStart(items, today), today.plusDays(3), currency).filter { it.value("virtual") == "yes" }
            CardEngine.invoices(virtualCardItems, card, today.minusYears(10), today.plusDays(3)).filter { it.outstandingMinor > 0 }.forEach { invoice ->
                result += FinancialAlert("invoice:${invoice.id}", if (invoice.dueDate.isBefore(today)) AlertPriority.URGENT else AlertPriority.ATTENTION,
                    "Fatura ${card.title}", "${money(invoice.outstandingMinor, currency)} • vencimento ${invoice.dueDate}", card.id)
            }
        }
        FinanceEngine.goals(items, today, currency).filter { it.remainingMinor == 0L && it.targetMinor > 0L }.forEach { goal ->
            result += FinancialAlert("goal:${goal.goal.id}", AlertPriority.INFORMATION, "Meta alcançada", goal.goal.title, goal.goal.id)
        }
        return result.sortedWith(compareBy<FinancialAlert> { it.priority.ordinal }.thenBy { it.id })
    }
    fun insights(items: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1): List<FinancialInsight> {
        val period = FinanceEngine.period(month, financialDay)
        val previous = FinanceEngine.period(month.minusMonths(1), financialDay)
        val current = FinanceEngine.transactions(items, period.start, period.end, currency).filter { FinanceEngine.recognizedExpense(it, today) }
        val old = FinanceEngine.transactions(items, previous.start, previous.end, currency).filter { FinanceEngine.recognizedExpense(it, today) }
        fun totals(rows: List<Item>) = rows.groupBy { it.value("category").ifBlank { "Sem categoria" } }.mapValues { (_, entries) -> entries.fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) } }
        val historic = totals(old)
        val result = mutableListOf<FinancialInsight>()
        totals(current).entries.sortedByDescending { it.value }.take(5).forEach { (category, amount) ->
            val before = historic[category] ?: 0
            if (before > 0) {
                val percent = BigDecimal.valueOf(amount).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(before), 0, RoundingMode.HALF_UP).toLong() - 100
                if (percent != 0L) result += FinancialInsight("category:$category:$month", "Seus gastos em $category ${if (percent > 0) "aumentaram" else "diminuíram"} ${kotlin.math.abs(percent)}% em relação ao mês anterior.", category)
            }
        }
        val subscriptions = current.filter { it.value("category") == "Assinaturas" }.fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) }
        if (subscriptions > 0) result += FinancialInsight("subscriptions:$month", "Você registrou ${money(subscriptions, currency)} em assinaturas neste mês.", "Assinaturas")
        FinanceEngine.budgets(items, period.start, period.end, currency, today).filter { it.threshold > 0 }.forEach { budget ->
            result += FinancialInsight("budget:${budget.budget.id}:$month", "Você já utilizou ${budget.percent.toInt()}% do orçamento de ${budget.budget.value("category")}.")
        }
        val upcoming = FinanceEngine.projection(items, today, today.plusDays(7), currency)
        if (upcoming.expectedExpense > 0) result += FinancialInsight("week:$today", "Há ${money(upcoming.expectedExpense, currency)} previstos para sair nos próximos 7 dias, incluindo valores atrasados.")
        val atypical = current.filter { unusualExpense(it, old + current.filter { candidate -> candidate.date < it.date }) }.take(3)
        atypical.forEach { result += FinancialInsight("unusual:${it.id}", "${it.title}: gasto acima do seu padrão recente.", it.value("category")) }
        return result
    }
    fun unusualExpense(item: Item, history: List<Item>): Boolean {
        if (!FinancialDomain.isExpense(item)) return false
        val amounts = history.filter { it.id != item.id && FinancialDomain.isExpense(it) && FinancialDomain.currency(it) == FinancialDomain.currency(item) &&
            it.value("category").equals(item.value("category"), true) }.sortedByDescending { it.date }.take(30).map(FinancialDomain::amount).sorted()
        if (amounts.size < 5) return false
        fun median(values: List<Long>) = if (values.size % 2 == 1) values[values.size / 2] else
            BigDecimal.valueOf(values[values.size / 2 - 1]).add(BigDecimal.valueOf(values[values.size / 2])).divide(BigDecimal(2), 0, RoundingMode.HALF_UP).longValueExact()
        val middle = median(amounts)
        val deviation = median(amounts.map { kotlin.math.abs(it - middle) }.sorted())
        val threshold = maxOf(BigDecimal.valueOf(middle).multiply(BigDecimal(3)), BigDecimal.valueOf(middle).add(BigDecimal.valueOf(deviation).multiply(BigDecimal(3))))
        return BigDecimal.valueOf(FinancialDomain.amount(item)) > threshold
    }
    fun health(items: List<Item>, month: YearMonth = YearMonth.now(), today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1): FinancialHealth {
        val summary = FinanceEngine.summary(items, month, today, currency, financialDay)
        if (summary.incomeMinor <= 0L) return FinancialHealth(null, "Registre receitas para calcular", emptyList())
        val factors = mutableListOf<HealthFactor>()
        val savingsRatio = summary.savingsMinor.toDouble() / summary.incomeMinor
        factors += HealthFactor("Receitas e despesas", when { savingsRatio >= .2 -> 30; savingsRatio >= .1 -> 24; savingsRatio >= 0 -> 18; else -> 0 }, 30,
            "Economia registrada: ${(savingsRatio * 100).toInt()}% das receitas do período.")
        val budget = FinanceEngine.budgets(items, summary.period.start, summary.period.end, currency, today)
        val within = budget.count { it.usedMinor <= it.limitMinor }
        factors += HealthFactor("Orçamentos", if (budget.isEmpty()) 10 else within * 20 / budget.size, 20,
            if (budget.isEmpty()) "Nenhum orçamento cadastrado; pontuação neutra." else "$within de ${budget.size} orçamentos dentro do limite.")
        val debts = items.filter { it.type == "debt" && it.deletedAt == 0L && FinancialDomain.currency(it) == currency && it.value("direction") !in setOf("A receber", "receivable") }
            .fold(0L) { sum, item -> Math.addExact(sum, (FinancialDomain.amount(item) - FinancialDomain.amount(item, "paid")).coerceAtLeast(0)) }
        factors += HealthFactor("Dívidas registradas", when { debts == 0L -> 15; debts <= summary.incomeMinor -> 10; debts <= Math.multiplyExact(summary.incomeMinor, 3) -> 5; else -> 0 }, 15,
            "Saldo devedor registrado: ${money(debts, currency)}.")
        val reserve = FinanceEngine.goals(items, today, currency).filter { it.goal.value("goalType") in setOf("emergency", "Reserva de emergência") || it.goal.title.contains("emergência", true) }
            .fold(0L) { sum, goal -> Math.addExact(sum, goal.currentMinor) }
        val recommended = FinanceEngine.emergencyReserve(items, today, 6, currency)
        factors += HealthFactor("Reserva", if (recommended == 0L) 5 else (reserve.toDouble() / recommended * 15).toInt().coerceIn(0, 15), 15,
            if (recommended == 0L) "Histórico insuficiente para a média de despesas; pontuação neutra." else "Reserva registrada: ${money(reserve, currency)}; referência de seis meses: ${money(recommended, currency)}.")
        val overdue = alerts(items, today, currency, financialDay).count { it.id.startsWith("overdue:") || it.id.startsWith("invoice:") && it.priority == AlertPriority.URGENT }
        factors += HealthFactor("Vencimentos", (20 - overdue * 5).coerceAtLeast(0), 20, "$overdue vencimentos registrados em atraso.")
        val score = factors.sumOf { it.points }
        return FinancialHealth(score, when { score >= 80 -> "Equilíbrio alto nos registros"; score >= 60 -> "Equilíbrio moderado"; else -> "Pontos de atenção" }, factors)
    }
    fun monthClose(items: List<Item>, month: YearMonth, today: LocalDate = LocalDate.now(), currency: String = "BRL", financialDay: Int = 1): MonthClose {
        val asOf = minOf(today, FinanceEngine.period(month, financialDay).end)
        val summary = FinanceEngine.summary(items, month, asOf, currency, financialDay)
        val expenses = FinanceEngine.transactions(items, summary.period.start, summary.period.end, currency).filter { FinanceEngine.recognizedExpense(it, asOf) }
        val largest = expenses.groupBy { it.value("category").ifBlank { "Sem categoria" } }.mapValues { (_, rows) -> rows.fold(0L) { sum, item -> Math.addExact(sum, FinancialDomain.amount(item)) } }.maxByOrNull { it.value }
        return MonthClose(summary, largest?.key, largest?.value ?: 0L, expenses.maxByOrNull(FinancialDomain::amount), FinanceEngine.goals(items, asOf, currency), insights(items, month, asOf, currency, financialDay))
    }
    /** Fixed-payment amortization, with interest rounded once per installment in minor units. */
    fun debtSchedule(principalMinor: Long, monthlyRate: BigDecimal, count: Int, firstDueDate: LocalDate, paymentMinor: Long? = null): List<DebtInstallment> {
        require(principalMinor > 0 && monthlyRate >= BigDecimal.ZERO && monthlyRate <= BigDecimal(100) && count in 1..120)
        val rate = monthlyRate.divide(BigDecimal(100), 16, RoundingMode.HALF_UP)
        val payment = paymentMinor ?: if (rate.signum() == 0) (principalMinor / count + if (principalMinor % count > 0) 1 else 0) else {
            val growth = (BigDecimal.ONE + rate).pow(count)
            BigDecimal.valueOf(principalMinor).multiply(rate).multiply(growth).divide(growth - BigDecimal.ONE, 0, RoundingMode.CEILING).longValueExact()
        }
        require(payment > 0)
        var balance = principalMinor
        return (1..count).map { index ->
            val interest = BigDecimal.valueOf(balance).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact()
            require(payment > interest || balance == 0L) { "A parcela não cobre os juros" }
            val actual = if (index == count) Math.addExact(balance, interest) else minOf(payment, Math.addExact(balance, interest))
            val principal = actual - interest
            balance -= principal
            val month = YearMonth.from(firstDueDate).plusMonths(index.toLong() - 1)
            DebtInstallment(index, month.atDay(firstDueDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())), actual, interest, principal, balance)
        }
    }
}
