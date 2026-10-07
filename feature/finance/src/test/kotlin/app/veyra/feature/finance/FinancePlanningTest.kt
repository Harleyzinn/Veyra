package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class FinancePlanningTest {
    private val start = LocalDate.of(2026, 10, 7)
    private fun forecast(vararg points: CashPoint) = CashProjection(points.first().balanceMinor, 0, 0,
        points.last().balanceMinor, points.toList(), points.firstOrNull { it.balanceMinor < 0 }?.date)
    private fun point(days: Long, balance: Long) = CashPoint(start.plusDays(days), balance, 0, 0, emptyList())
    @Test fun extraExpenseAffectsOnlyChosenDayAndLaterAndKeepsActualBalance() {
        val base = forecast(point(0, 10000), point(5, 10000), point(10, 20000))
        val result = FinancePlanning.simulate(base, start.plusDays(3), extraExpense = 3000)
        assertEquals(listOf(10000L, 7000L, 7000L, 17000L), result.projection.points.map { it.balanceMinor })
        assertEquals(10000L, result.projection.currentBalance)
        assertEquals(20000L, base.projectedBalance)
        assertEquals(3000L, result.projection.expectedExpense)
    }
    @Test fun laterDepositCannotFundDailySpendingBeforeItArrives() {
        val base = forecast(point(0, 900), point(9, 100900), point(19, 100900))
        assertEquals(100L, FinancePlanning.simulate(base, start).dailyMargin)
    }
    @Test fun reserveIsAFloorAndDoesNotBecomeAnExpense() {
        val base = forecast(point(0, 10000), point(9, 10000))
        val result = FinancePlanning.simulate(base, start, reserve = 4000)
        assertEquals(600L, result.dailyMargin)
        assertEquals(10000L, result.projection.projectedBalance)
        assertEquals(0L, result.projection.expectedExpense)
    }
    @Test fun detectsNegativeDateBeforeLaterRecoveryAndOffersZeroMargin() {
        val base = forecast(point(0, 1000), point(4, 500), point(8, 10000))
        val result = FinancePlanning.simulate(base, start.plusDays(2), extraExpense = 1500)
        assertEquals(start.plusDays(2), result.projection.firstNegativeDate)
        assertEquals(-1000L, result.lowestBalance)
        assertEquals(0L, result.dailyMargin)
    }
    @Test fun incomeAndExpenseOnSameDateAreBothIncludedOnce() {
        val base = forecast(point(0, 1000), point(0, 500), point(3, 500))
        val result = FinancePlanning.simulate(base, start, extraIncome = 200, extraExpense = 100)
        assertEquals(2, result.projection.points.size)
        assertEquals(600L, result.projection.projectedBalance)
        assertEquals(200L, result.projection.points.first().incomeMinor)
        assertEquals(100L, result.projection.points.first().expenseMinor)
    }
    @Test fun rejectsDatesOutsideHorizonNegativeAmountsAndOverflow() {
        val base = forecast(point(0, 1000), point(9, 1000))
        assertFails { FinancePlanning.simulate(base, start.minusDays(1)) }
        assertFails { FinancePlanning.simulate(base, start.plusDays(10)) }
        assertFails { FinancePlanning.simulate(base, start, extraIncome = -1) }
        assertFails { FinancePlanning.simulate(base, start, reserve = FinancialDomain.MAX_MINOR + 1) }
        assertFails { FinancePlanning.simulate(forecast(point(0, Long.MAX_VALUE)), start, extraIncome = 1) }
    }
    @Test fun duplicateSuggestionsNormalizeTitleButKeepAccountsAndCurrencySeparate() {
        val original = Item(id = "a", type = "expense", title = "Café  Centro", date = start.toString(),
            fields = mapOf("amountMinor" to "1200", "account" to "bank-a", "currency" to "BRL"))
        val copy = original.copy(id = "b", title = " cafe centro ")
        val otherBank = original.copy(id = "c", fields = original.fields + ("account" to "bank-b"))
        val dollar = original.copy(id = "d", fields = original.fields + ("currency" to "USD"))
        val report = FinancePlanning.review(listOf(original, copy, otherBank, dollar), start, start, "BRL")
        assertEquals(setOf("a", "b"), report.duplicates.single().map { it.id }.toSet())
        assertEquals(3, report.uncategorized.size)
    }
    @Test fun automaticSeriesDeletedCancelledAndOutOfRangeDoNotBecomeDuplicates() {
        val original = Item(id = "a", type = "expense", title = "Compra", date = start.toString(), fields = mapOf("amountMinor" to "100", "category" to "Compras"))
        val excluded = listOf(
            original.copy(id = "b", fields = original.fields + ("installmentPlanId" to "plan")),
            original.copy(id = "c", fields = original.fields + ("source" to "rule")),
            original.copy(id = "d", fields = original.fields + ("virtual" to "yes")),
            original.copy(id = "e", fields = original.fields + ("status" to "cancelled")),
            original.copy(id = "f", deletedAt = 100), original.copy(id = "g", date = start.minusDays(1).toString()))
        val result = FinancePlanning.review(listOf(original, original) + excluded, start, start, "BRL")
        assertTrue(result.duplicates.isEmpty())
        assertTrue(result.uncategorized.isEmpty())
    }
    @Test fun fractionalDailyMarginRoundsDownWithoutSpendingReservedCent() {
        val result = FinancePlanning.simulate(forecast(point(0, 1000), point(2, 1000)), start, reserve = 1)
        assertEquals(333L, result.dailyMargin)
        assertTrue(result.dailyMargin * 3 <= 999)
    }
}
