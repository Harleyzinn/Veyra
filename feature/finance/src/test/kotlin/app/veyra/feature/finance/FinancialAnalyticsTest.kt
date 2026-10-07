package app.veyra.feature.finance

import app.veyra.model.Item
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class FinancialAnalyticsTest {
    private val today = LocalDate.of(2026, 10, 6)
    private fun expense(amount: String, day: String = "2026-10-01") = Item(type = "expense", title = "Almoço", date = day, fields = mapOf("amount" to amount, "category" to "Alimentação", "status" to "paid"))
    @Test fun outlierNeedsHistoryAndUsesOnlyOwnCategoryAndCurrency() {
        val history = listOf("40", "50", "60", "70", "80").map { expense(it, "2026-09-01") }
        assertTrue(FinancialAnalytics.unusualExpense(expense("450"), history))
        assertFalse(FinancialAnalytics.unusualExpense(expense("90"), history))
        assertFalse(FinancialAnalytics.unusualExpense(expense("450"), history.take(4)))
        assertFalse(FinancialAnalytics.unusualExpense(expense("450").copy(fields = expense("450").fields + ("category" to "Saúde")), history))
        assertFalse(FinancialAnalytics.unusualExpense(expense("450").copy(fields = expense("450").fields + ("currency" to "USD")), history))
    }
    @Test fun insightsCalculateRealCategoryChangeAndSubscriptionTotals() {
        val rows = listOf(expense("100", "2026-09-01"), expense("118"), expense("240").copy(fields = expense("240").fields + ("category" to "Assinaturas")))
        val insights = FinancialAnalytics.insights(rows, YearMonth.from(today), today)
        assertTrue(insights.any { it.text.contains("aumentaram 18%") })
        assertTrue(insights.any { it.text.contains("BRL 240.00") })
        assertFalse(insights.any { it.text.contains("IA") })
    }
    @Test fun goalsCalculateProgressAndCompletionFromConfiguredRealContribution() {
        val goal = Item(type = "savings_goal", title = "Viagem", fields = mapOf("amount" to "10000", "saved" to "4200", "monthlyContribution" to "1000"))
        val progress = FinanceEngine.goals(listOf(goal), today).single()
        assertEquals(42.0, progress.percent)
        assertEquals(580000L, progress.remainingMinor)
        assertEquals(today.plusMonths(6), progress.estimatedCompletion)
        assertNull(FinanceEngine.goals(listOf(goal.copy(fields = goal.fields - "monthlyContribution")), today).single().estimatedCompletion)
    }
    @Test fun emergencyReserveUsesActualRecordedCompletedMonths() {
        val rows = listOf(expense("1000", "2026-07-10"), expense("2000", "2026-08-10"), expense("3000", "2026-09-10"))
        assertEquals(1200000L, FinanceEngine.emergencyReserve(rows, today, 6))
        assertEquals(600000L, FinanceEngine.emergencyReserve(rows, today, 3))
        assertEquals(0L, FinanceEngine.emergencyReserve(emptyList(), today))
    }
    @Test fun subscriptionSummaryReflectsFrequencyPauseAndActualNextOccurrence() {
        val monthly = Item(type = "subscription", title = "Internet", date = "2026-10-10", fields = mapOf("amount" to "119.90"))
        val annual = Item(type = "subscription", title = "Domínio", date = "2026-10-15", fields = mapOf("amount" to "120", "frequency" to "YEARLY"))
        val costs = FinanceEngine.subscriptions(listOf(monthly, annual), today)
        assertEquals(11990L, costs.first().monthlyMinor)
        assertEquals(143880L, costs.first().annualMinor)
        assertEquals(1000L, costs.last().monthlyMinor)
        assertEquals(LocalDate.of(2026, 10, 10), costs.first().nextCharge)
        assertEquals(0L, FinanceEngine.subscriptions(listOf(RecurrenceEngine.pause(monthly, true)), today).single().annualMinor)
    }
    @Test fun alertsHaveStableIdsPrioritiesAndRealRecordedValues() {
        val overdue = expense("620", "2026-10-01").copy(fields = expense("620").fields + ("status" to "pending"))
        val budget = Item(type = "budget", title = "Alimentação", fields = mapOf("amount" to "100", "category" to "Alimentação"))
        val alerts = FinancialAnalytics.alerts(listOf(overdue, expense("90"), budget), today)
        assertTrue(alerts.any { it.priority == AlertPriority.URGENT && it.id.startsWith("overdue:") && it.description.contains("620.00") })
        assertTrue(alerts.any { it.id.contains(":90") && it.description.contains("90%") })
        assertEquals(alerts.map { it.id }, FinancialAnalytics.alerts(listOf(overdue, expense("90"), budget), today).map { it.id })
    }
    @Test fun healthExplainsEveryPointAndRefusesToInventMissingIncome() {
        assertNull(FinancialAnalytics.health(emptyList(), YearMonth.from(today), today).score)
        val income = Item(type = "income", title = "Salário", date = today.toString(), fields = mapOf("amount" to "1000", "status" to "received"))
        val health = FinancialAnalytics.health(listOf(income, expense("700")), YearMonth.from(today), today)
        assertNotNull(health.score)
        assertEquals(health.score, health.factors.sumOf { it.points })
        assertTrue(health.factors.all { it.explanation.isNotBlank() && it.points in 0..it.maximum })
    }
    @Test fun closingSummarizesLargestExpenseCategoryAndSavings() {
        val income = Item(type = "income", title = "Salário", date = today.toString(), fields = mapOf("amount" to "3500", "status" to "received"))
        val major = expense("1000").copy(title = "Aluguel", fields = expense("1000").fields + ("category" to "Casa"))
        val closing = FinancialAnalytics.monthClose(listOf(income, major, expense("100")), YearMonth.from(today), today)
        assertEquals("Casa", closing.largestCategory)
        assertEquals(100000L, closing.largestCategoryMinor)
        assertEquals(major, closing.largestExpense)
        assertEquals(240000L, closing.summary.savingsMinor)
    }
    @Test fun debtAmortizationPreservesPrincipalAndStopsExactlyAtZero() {
        val schedule = FinancialAnalytics.debtSchedule(100000, BigDecimal("1.5"), 12, LocalDate.of(2026, 1, 31))
        assertEquals(100000L, schedule.sumOf { it.principalMinor })
        assertEquals(0L, schedule.last().remainingMinor)
        assertEquals(LocalDate.of(2026, 2, 28), schedule[1].dueDate)
        assertEquals(LocalDate.of(2026, 3, 31), schedule[2].dueDate)
        assertTrue(schedule.all { it.paymentMinor == it.principalMinor + it.interestMinor && it.remainingMinor >= 0 })
        assertFails { FinancialAnalytics.debtSchedule(100000, BigDecimal("20"), 12, today, 100) }
    }
    @Test fun zeroInterestAmortizationKeepsRemainderCents() {
        val schedule = FinancialAnalytics.debtSchedule(1000, BigDecimal.ZERO, 3, today)
        assertEquals(listOf(334L, 334L, 332L), schedule.map { it.paymentMinor })
        assertEquals(1000L, schedule.sumOf { it.principalMinor })
    }
    @Test fun insightsRespectFinancialPeriodInsteadOfCalendarMonth() {
        val rows = listOf(expense("100", "2026-09-10"), expense("200", "2026-10-10"), expense("900", "2026-10-01"))
        val insights = FinanceEngine.insights(rows, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 20), "BRL", 5)
        assertTrue(insights.any { it.text.contains("diminuíram 80%") })
        assertFalse(insights.any { it.text.contains("1000%") })
    }
    @Test fun emergencyGoalFromEditorCountsEvenWithCustomTitleAndMinorOnlyContribution() {
        val goal = Item(type = "savings_goal", title = "Segurança", fields = mapOf("amount" to "600", "saved" to "600", "goalType" to "Reserva de emergência", "monthlyContributionMinor" to "1000"))
        val income = Item(type = "income", title = "Salário", date = today.toString(), fields = mapOf("amount" to "1000", "status" to "received"))
        val rows = listOf(goal, income, expense("100", "2026-09-01"))
        assertEquals(15, FinanceEngine.health(rows, YearMonth.from(today), today).factors.single { it.label == "Reserva" }.points)
        val unfinished = goal.copy(fields = goal.fields + ("saved" to "500"))
        assertEquals(today.plusMonths(10), FinanceEngine.goals(listOf(unfinished), today).single().estimatedCompletion)
    }
    @Test fun customBudgetAlertsUseLastReachedPercentAndStableThresholdIdentity() {
        val budget = Item(id = "b", type = "budget", title = "Comida", fields = mapOf("category" to "Alimentação", "amount" to "100", "thresholds" to "80, 25,60,25,100"))
        val normalized = FinancialDomain.normalize(budget)
        assertEquals("25,60,80,100", normalized.value("thresholds"))
        val rows = listOf(normalized, expense("35"))
        val progress = FinanceEngine.budgets(rows, today.withDayOfMonth(1), today.withDayOfMonth(31), today = today).single()
        assertEquals(25, progress.threshold)
        val first = FinanceEngine.alerts(rows, today).single { it.id.startsWith("budget:") }
        assertEquals("budget:b:2026-10:25", first.id)
        val next = FinanceEngine.alerts(listOf(normalized, expense("79.99")), today).single { it.id.startsWith("budget:") }
        assertEquals("budget:b:2026-10:60", next.id)
        assertTrue(FinanceEngine.alerts(listOf(normalized, expense("24.99")), today).none { it.id.startsWith("budget:") })
        val withoutHundred = normalized.copy(fields = normalized.fields + ("thresholds" to "25,80"))
        assertEquals(AlertPriority.URGENT, FinanceEngine.alerts(listOf(withoutHundred, expense("120")), today).single { it.id.startsWith("budget:") }.priority)
        assertFails { FinancialDomain.normalize(budget.copy(fields = budget.fields + ("thresholds" to "0,50"))) }
        assertFails { FinancialDomain.normalize(budget.copy(fields = budget.fields + ("thresholds" to "50,101"))) }
        assertFails { FinancialDomain.normalize(budget.copy(fields = budget.fields + ("thresholds" to "50.5,80"))) }
    }
}
