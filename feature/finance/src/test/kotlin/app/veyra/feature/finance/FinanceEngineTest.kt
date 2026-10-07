package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.*

class FinanceEngineTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val account = Item(id = "a", type = "account", title = "Banco", fields = mapOf("opening" to "2800"))
    private fun tx(type: String, amount: String, date: String = "2026-10-06", status: String = "paid", extras: Map<String, String> = emptyMap()) =
        FinancialDomain.normalize(Item(type = type, title = "Teste", date = date, fields = mapOf("amount" to amount, "status" to status, "account" to account.id) + extras))
    @Test fun currentAndProjectedBalancesIncludeOpeningAndPendingCash() {
        val rows = listOf(account, tx("income", "3500", "2026-10-10", "expected"), tx("expense", "2100", "2026-10-15", "pending"))
        val forecast = FinanceEngine.projection(rows, today, today.plusDays(30))
        assertEquals(280000L, forecast.currentBalance)
        assertEquals(350000L, forecast.expectedIncome)
        assertEquals(210000L, forecast.expectedExpense)
        assertEquals(420000L, forecast.projectedBalance)
    }
    @Test fun overduePendingAndFutureSettledEntriesAreProjectedOnce() {
        val rows = listOf(account, tx("expense", "100", "2026-10-01", "pending"), tx("income", "500", "2026-10-10", "received"))
        val forecast = FinanceEngine.projection(rows, today, today.plusDays(7))
        assertEquals(280000L, forecast.currentBalance)
        assertEquals(320000L, forecast.projectedBalance)
        assertEquals(today, forecast.points[1].date)
    }
    @Test fun cancelledAndDeletedTransactionsNeverAffectAnyBalance() {
        val rows = listOf(account, tx("expense", "5000", status = "cancelled"), tx("income", "9999", status = "received").copy(deletedAt = 1))
        assertEquals(280000L, FinanceEngine.currentBalance(rows, today))
        assertEquals(280000L, FinanceEngine.projection(rows, today, today.plusDays(30)).projectedBalance)
    }
    @Test fun internalTransferConservesAllMoneyAndUpdatesBothAccounts() {
        val destination = Item(id = "b", type = "account", title = "Carteira")
        val transfer = FinanceActions.transfer(10000, account, destination, today)
        val rows = listOf(account, destination, transfer)
        assertEquals(270000L, FinanceEngine.accountBalance(rows, account, today))
        assertEquals(10000L, FinanceEngine.accountBalance(rows, destination, today))
        assertEquals(280000L, FinanceEngine.currentBalance(rows, today))
        assertEquals(0L, FinanceEngine.summary(rows, YearMonth.from(today), today).incomeMinor)
        assertEquals(0L, FinanceEngine.summary(rows, YearMonth.from(today), today).expenseMinor)
    }
    @Test fun cashCarryAvoidsDoubleCountingOldBookedEntriesSelectedByDueDate() {
        val carry = Item(type = "cash_carry", title = "Histórico", date = "2026-09-30", fields = mapOf("amountMinor" to "50000", "account" to account.id))
        val overlapping = tx("income", "500", "2026-10-10", "received", mapOf("settledDate" to "2026-09-20"))
        val rows = listOf(account, carry, overlapping, tx("expense", "100"))
        assertEquals(320000L, FinanceEngine.currentBalance(rows, today))
        assertEquals(320000L, FinanceEngine.accountBalance(rows, account, today))
    }
    @Test fun currenciesAreNeverAddedTogetherWithoutAnExchangeRate() {
        val usd = tx("income", "100", status = "received", extras = mapOf("currency" to "USD"))
        assertEquals(280000L, FinanceEngine.currentBalance(listOf(account, usd), today, "BRL"))
        assertEquals(10000L, FinanceEngine.currentBalance(listOf(account, usd), today, "USD"))
    }
    @Test fun recurringSalaryAndBillsAppearInForecastWithoutDatabaseMaterialization() {
        val salary = Item(id = "salary", type = "recurring_rule", title = "Salário", date = "2026-10-10", fields = mapOf("amount" to "3500", "frequency" to "MONTHLY", "transactionType" to "income"))
        val bill = Item(id = "internet", type = "subscription", title = "Internet", date = "2026-10-15", fields = mapOf("amount" to "119.90"))
        val data = listOf(account, salary, bill)
        assertEquals(3, data.size)
        val forecast = FinanceEngine.projection(data, today, LocalDate.of(2026, 12, 31))
        assertEquals(1050000L, forecast.expectedIncome)
        assertEquals(35970L, forecast.expectedExpense)
        assertEquals(1294030L, forecast.projectedBalance)
    }
    @Test fun persistedBillPaymentSuppressesVirtualBillAndCountsOnlyOneCashDebit() {
        val bill = Item(id = "bill", type = "bill", title = "Luz", date = "2026-10-01", fields = mapOf("amount" to "100"))
        val payment = tx("expense", "100").copy(id = "bill-payment:bill")
        val rows = listOf(account, bill, payment)
        assertEquals(1, FinanceEngine.transactions(rows, today.withDayOfMonth(1), today.plusDays(25)).size)
        assertEquals(270000L, FinanceEngine.projection(rows, today, today.plusDays(30)).projectedBalance)
    }
    @Test fun projectedCurveDetectsTemporaryNegativeBalanceBeforeLaterSalary() {
        val rows = listOf(account, tx("expense", "3000", "2026-10-08", "pending"), tx("income", "3500", "2026-10-10", "expected"))
        val forecast = FinanceEngine.projection(rows, today, today.plusDays(30))
        assertEquals(LocalDate.of(2026, 10, 8), forecast.firstNegativeDate)
        assertEquals(330000L, forecast.projectedBalance)
    }
    @Test fun cardExpensesAndInvoicePaymentAreNotDoubleCountedInSpendingOrForecast() {
        val card = Item(id = "c", type = "card", title = "Cartão", fields = mapOf("closing" to "10", "due" to "20"))
        val purchase = tx("expense", "100", extras = mapOf("card" to "c", "dueDate" to "2026-10-20"))
        val rows = listOf(account, card, purchase)
        assertEquals(280000L, FinanceEngine.currentBalance(rows, today))
        assertEquals(270000L, FinanceEngine.projection(rows, today, today.plusDays(30)).projectedBalance)
        assertEquals(10000L, FinanceEngine.summary(rows, YearMonth.from(today), today).expenseMinor)
        val invoice = CardEngine.invoices(rows, card, today, today.plusDays(30)).single()
        val paid = rows + CardEngine.settleInvoice(invoice, account.id, today)
        assertEquals(10000L, FinanceEngine.summary(paid, YearMonth.from(today), today).expenseMinor)
        assertEquals(270000L, FinanceEngine.projection(paid, today, today.plusDays(30)).projectedBalance)
    }
    @Test fun customFinancialMonthClampsDay31WithoutLeavingAGap() {
        val january = FinanceEngine.period(YearMonth.of(2026, 1), 31)
        val february = FinanceEngine.period(YearMonth.of(2026, 2), 31)
        assertEquals(LocalDate.of(2026, 1, 31), january.start)
        assertEquals(LocalDate.of(2026, 2, 27), january.end)
        assertEquals(january.end.plusDays(1), february.start)
        assertEquals(LocalDate.of(2026, 3, 30), february.end)
    }
    @Test fun budgetThresholdAndHistoricalMonthComparisonUseRecordedAmounts() {
        val rows = listOf(account, tx("expense", "90", extras = mapOf("category" to "Lazer")), tx("expense", "100", "2026-09-06", extras = mapOf("category" to "Lazer")),
            Item(type = "budget", title = "Lazer", fields = mapOf("category" to "Lazer", "amount" to "100")))
        val budget = FinanceEngine.budgets(rows, today.withDayOfMonth(1), today.withDayOfMonth(31), today = today).single()
        assertEquals(90, budget.threshold)
        assertEquals(90.0, budget.percent)
        val summary = FinanceEngine.summary(rows, YearMonth.from(today), today)
        assertEquals(-10.0, summary.expenseChangePercent!!, .0001)
    }
    @Test fun netWorthDoesNotDoubleCountSavingsGoalsAndIncludesManualAssetsAndDebts() {
        val rows = listOf(account, Item(type = "savings_goal", title = "Reserva", fields = mapOf("amount" to "10000", "saved" to "1000")),
            Item(type = "investment", title = "Aplicação", fields = mapOf("current" to "2000")),
            Item(type = "financial_asset", title = "Carro", fields = mapOf("current" to "30000")),
            Item(type = "debt", title = "Empréstimo", fields = mapOf("amount" to "5000", "paid" to "1000")))
        assertEquals(3080000L, FinanceEngine.netWorth(rows, today))
    }
    @Test fun searchAndFiltersCoverReferencedNamesNotesAndInstallments() {
        val item = tx("expense", "42.90", extras = mapOf("category" to "Alimentação", "person" to "Ana", "installmentPlanId" to "plan"))
            .copy(notes = "Aniversário", tags = "#pizza")
        assertEquals(listOf(item), FinanceEngine.search(listOf(item), "Banco", listOf(account)))
        assertEquals(listOf(item), FinanceEngine.search(listOf(item), "Ana"))
        assertEquals(listOf(item), FinanceEngine.search(listOf(item), "42.90"))
        assertEquals(listOf(item), FinanceEngine.filter(listOf(item), FinancialFilter(category = "Alimentação", installment = true, minMinor = 4200, maxMinor = 4300)))
        assertTrue(FinanceEngine.filter(listOf(item), FinancialFilter(status = TransactionStatus.EXPECTED)).isEmpty())
    }
    @Test fun zeroCarryStillMarksCutoffAgainstOneOverlappingHistoricalTransaction() {
        val zero = Item(type = "cash_carry", title = "Histórico", date = "2026-09-30", fields = mapOf("amountMinor" to "0", "currency" to "BRL"))
        val old = tx("expense", "100", "2026-10-05", extras = mapOf("settledDate" to "2026-09-20"))
        assertEquals(280000L, FinanceEngine.currentBalance(listOf(account, zero, old), today))
    }
    @Test fun historicalCardCarryParticipatesInForecastAndMonthlyOutstandingInvoice() {
        val card = Item(id = "c", type = "card", title = "Cartão", fields = mapOf("closing" to "10", "due" to "20"))
        val carry = Item(type = "card_carry", title = "Histórico", date = "2026-10-20", fields = mapOf("card" to "c", "dueDate" to "2026-10-20", "amountMinor" to "10000", "paidMinor" to "3000"))
        val data = listOf(account, card, carry)
        assertEquals(273000L, FinanceEngine.projection(data, today, today.plusDays(30)).projectedBalance)
        assertEquals(7000L, FinanceEngine.summary(data, YearMonth.from(today), today).cardInvoicesMinor)
    }
    @Test fun accountProjectionIncludesPendingTransfersAndLinkedInvoiceWithoutAffectingGlobalIncome() {
        val b = Item(id = "b", type = "account", title = "B")
        val transfer = FinanceActions.transfer(10000, account, b, today.plusDays(1)).copy(fields = FinanceActions.transfer(10000, account, b, today.plusDays(1)).fields + ("status" to "pending"))
        assertEquals(270000L, FinanceEngine.accountProjection(listOf(account, b, transfer), account, today, today.plusDays(30)))
        assertEquals(10000L, FinanceEngine.accountProjection(listOf(account, b, transfer), b, today, today.plusDays(30)))
        assertEquals(280000L, FinanceEngine.projection(listOf(account, b, transfer), today, today.plusDays(30)).projectedBalance)
    }
    @Test fun overdueUnmaterializedSalaryIsIncludedAlongsideNextMonthAndInAlerts() {
        val salary = Item(id = "salary", type = "recurring_rule", title = "Salário", date = "2026-10-05", fields = mapOf("amount" to "3500", "frequency" to "MONTHLY", "transactionType" to "income"))
        val forecast = FinanceEngine.projection(listOf(account, salary), today, today.plusDays(30))
        assertEquals(700000L, forecast.expectedIncome)
        assertEquals(LocalDate.of(2026, 10, 5), forecast.virtualLookbackStart)
        assertTrue(FinanceEngine.alerts(listOf(account, salary), today).any { it.id == "overdue:recurring:salary:2026-10-05" })
        val paid = FinanceActions.settle(RecurrenceEngine.occurrence(salary, LocalDate.of(2026, 10, 5)), today)
        val settledForecast = FinanceEngine.projection(listOf(account, salary, paid), today, today.plusDays(30))
        assertEquals(350000L, settledForecast.expectedIncome)
        assertEquals(630000L, settledForecast.currentBalance)
        assertEquals(980000L, settledForecast.projectedBalance)
    }
    @Test fun cashCarryWindowExplicitlyBoundsUnmaterializedPastDueRules() {
        val salary = Item(id = "salary", type = "recurring_rule", title = "Salário", date = "2000-01-05", fields = mapOf("amount" to "100", "frequency" to "MONTHLY", "transactionType" to "income"))
        val carry = Item(type = "cash_carry", title = "Histórico", date = "2026-09-30", fields = mapOf("amountMinor" to "0"))
        val forecast = FinanceEngine.projection(listOf(account, salary, carry), today, today.plusDays(30))
        assertEquals(20000L, forecast.expectedIncome)
        assertEquals(LocalDate.of(2026, 10, 1), forecast.virtualLookbackStart)
        assertFails { FinanceEngine.projection(listOf(account, salary), today, today.plusDays(30)) }
    }
    @Test fun cardInvoiceHelperExpandsSubscriptionsBeforeTheirBillingDueMonth() {
        val card = Item(id = "c", type = "card", title = "Cartão", fields = mapOf("closing" to "10", "due" to "5"))
        val rule = Item(id = "netflix", type = "subscription", title = "Netflix", date = "2026-10-09", fields = mapOf("amount" to "40", "card" to "c"))
        val invoice = FinanceEngine.cardInvoices(listOf(card, rule), card, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).single()
        assertEquals(LocalDate.of(2026, 11, 5), invoice.dueDate)
        assertEquals(4000L, invoice.outstandingMinor)
    }
    @Test fun currentCardLiabilityIncludesPastVirtualChargesButNotFuturePredictedSubscriptions() {
        val card = Item(id = "c", type = "card", title = "Cartão", fields = mapOf("closing" to "10", "due" to "5", "limit" to "1000"))
        val rule = Item(id = "netflix", type = "subscription", title = "Netflix", date = "2026-09-09", fields = mapOf("amount" to "40", "card" to "c"))
        val data = listOf(account, card, rule)
        assertEquals(4000L, FinanceEngine.cardUsedLimit(data, card, today))
        assertEquals(96000L, FinanceEngine.cardAvailableLimit(data, card, today))
        assertEquals(276000L, FinanceEngine.netWorth(data, today))
        assertEquals(4000L, FinanceEngine.summary(data, YearMonth.from(today), today).cardInvoicesMinor)
        val first = FinanceEngine.cardInvoices(data, card, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).single()
        val payment = CardEngine.settleInvoice(first, account.id, today)
        val paid = data + payment
        assertEquals(0L, FinanceEngine.cardUsedLimit(paid, card, today))
        assertEquals(276000L, FinanceEngine.netWorth(paid, today))
    }
}
