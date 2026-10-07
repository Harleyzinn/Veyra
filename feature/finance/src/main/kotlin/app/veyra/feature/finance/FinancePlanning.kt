package app.veyra.feature.finance

import app.veyra.model.Item
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

data class FinanceScenario(val projection: CashProjection, val lowestBalance: Long,
    val dailyMargin: Long, val firstBelowReserve: LocalDate?)
data class FinanceReview(val duplicates: List<List<Item>>, val uncategorized: List<Item>)

object FinancePlanning {
    /** One-off changes affect the chosen day and later days; no record or actual balance is changed. */
    fun simulate(base: CashProjection, date: LocalDate, extraIncome: Long = 0,
        extraExpense: Long = 0, reserve: Long = 0): FinanceScenario {
        require(base.points.isNotEmpty()) { "A previsão não tem datas." }
        require(listOf(extraIncome, extraExpense, reserve).all { it in 0..FinancialDomain.MAX_MINOR }) { "Use valores positivos dentro do limite." }
        val start = base.points.minOf { it.date }; val end = base.points.maxOf { it.date }
        require(date in start..end) { "A data precisa estar dentro do horizonte da simulação." }
        require(ChronoUnit.DAYS.between(start, end) <= 3660)
        val daily = base.points.groupBy { it.date }.mapValues { (_, rows) ->
            rows.last().copy(incomeMinor = rows.fold(0L) { n, p -> Math.addExact(n, p.incomeMinor) },
                expenseMinor = rows.fold(0L) { n, p -> Math.addExact(n, p.expenseMinor) }, descriptions = rows.flatMap { it.descriptions })
        }
        val delta = Math.subtractExact(extraIncome, extraExpense)
        val points = (daily.keys + date).sorted().map { day ->
            val original = daily[day] ?: CashPoint(day, daily.entries.filter { it.key < day }.maxBy { it.key }.value.balanceMinor, 0, 0, emptyList())
            original.copy(balanceMinor = if (day >= date) Math.addExact(original.balanceMinor, delta) else original.balanceMinor,
                incomeMinor = Math.addExact(original.incomeMinor, if (day == date) extraIncome else 0),
                expenseMinor = Math.addExact(original.expenseMinor, if (day == date) extraExpense else 0),
                descriptions = original.descriptions + if (day == date && (extraIncome > 0 || extraExpense > 0)) listOf("Cenário simulado") else emptyList())
        }
        // Sparse points are sufficient: between cash events, the cumulative daily allowance only grows.
        // Check the day immediately before each event as well, so a later deposit cannot finance earlier spending.
        val checkpoints = points.toMutableList()
        points.zipWithNext().forEach { (before, after) ->
            if (ChronoUnit.DAYS.between(before.date, after.date) > 1) checkpoints += before.copy(date = after.date.minusDays(1))
        }
        val margin = checkpoints.minOf { p ->
            Math.subtractExact(p.balanceMinor, reserve).coerceAtLeast(0) / (ChronoUnit.DAYS.between(start, p.date) + 1)
        }
        val projection = base.copy(expectedIncome = Math.addExact(base.expectedIncome, extraIncome),
            expectedExpense = Math.addExact(base.expectedExpense, extraExpense),
            projectedBalance = Math.addExact(base.projectedBalance, delta), points = points,
            firstNegativeDate = points.firstOrNull { it.balanceMinor < 0 }?.date)
        return FinanceScenario(projection, points.minOf { it.balanceMinor }, margin,
            points.firstOrNull { it.balanceMinor < reserve }?.date)
    }

    /** A suggestion for review, never an automatic merge or deletion. */
    fun review(items: List<Item>, from: LocalDate, through: LocalDate, currency: String): FinanceReview {
        require(!through.isBefore(from))
        val rows = items.distinctBy { it.id }.filter { it.type in setOf("income", "expense") &&
            FinancialDomain.active(it) && FinancialDomain.currency(it) == currency &&
            it.date.isNotBlank() && it.date >= from.toString() && it.date <= through.toString() &&
            it.value("virtual") != "yes" && it.value("paymentType") != "card_payment" }
        fun title(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        val duplicates = rows.filter { it.value("installmentPlanId").isBlank() &&
            it.value("recurrenceRuleId").isBlank() && it.value("recurrenceId").isBlank() && it.value("source").isBlank() }.groupBy {
            listOf(it.type, it.date, FinancialDomain.amount(it).toString(), title(it.title),
                it.value("account"), it.value("card"), it.value("paymentType"))
        }.values.filter { it.size > 1 }.map { it.sortedBy { row -> row.createdAt } }.sortedByDescending { it.first().date }
        return FinanceReview(duplicates, rows.filter { it.value("category").isBlank() || it.value("category") == "Sem categoria" }.sortedByDescending { it.date })
    }
}
