package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class FinancialDomainTest {
    private val today = LocalDate.of(2026, 10, 6)
    @Test fun parsesBrazilianGroupingAndForeignScalesExactly() {
        assertEquals(350000L, FinancialDomain.parseMinor("R$ 3.500,00"))
        assertEquals(350000L, FinancialDomain.parseMinor("R$ 3.500"))
        assertEquals(100000L, FinancialDomain.parseMinor("1,000.00", "USD"))
        assertFails { FinancialDomain.parseMinor("1.2,34") }
        assertEquals(1090L, FinancialDomain.parseMinor("10.90"))
        assertEquals(1500L, FinancialDomain.parseMinor("1500", "JPY"))
        assertEquals(1234L, FinancialDomain.parseMinor("1.234", "KWD"))
        assertFails { FinancialDomain.parseMinor("1.001", "BRL") }
        assertFails { FinancialDomain.parseMinor("1.5", "JPY") }
        assertFails { FinancialDomain.parseMinor("NaN") }
        assertFails { FinancialDomain.parseMinor("9999999999999999999999") }
    }
    @Test fun preciseMinorRepresentationWinsOverLegacyText() {
        val item = Item(type = "expense", title = "Café", fields = mapOf("amountMinor" to "800", "amount" to "10"))
        assertEquals(800L, FinancialDomain.amount(item))
        assertEquals("8.00", FinancialDomain.normalize(item).value("amount"))
    }
    @Test fun statesDistinguishPendingOverdueCancelledAndSettlement() {
        val item = Item(type = "income", title = "Salário", date = "2026-10-05", fields = mapOf("status" to "EXPECTED", "amount" to "3500"))
        assertEquals(TransactionStatus.OVERDUE, FinancialDomain.status(item, today))
        assertFalse(FinancialDomain.settled(item))
        assertEquals(TransactionStatus.EXPECTED, FinancialDomain.status(item, today.minusDays(2)))
        val paid = FinanceActions.settle(item, today)
        assertEquals(TransactionStatus.RECEIVED, FinancialDomain.status(paid, today))
        assertTrue(FinancialDomain.settled(paid))
        val cancelled = item.copy(done = true, fields = item.fields + ("status" to "CANCELLED"))
        assertFalse(FinancialDomain.active(cancelled))
        assertEquals(0L, FinancialDomain.cashDelta(cancelled))
    }
    @Test fun legacyMarkersKeepOriginalHistoricalCardCash() {
        val old = Item(id = "old", type = "expense", title = "Compra antiga", fields = mapOf("amount" to "50", "card" to "c", "account" to "a"))
        val migrated = FinanceMigration.migrate(listOf(old)).single()
        assertEquals("yes", migrated.value("legacyCardCash"))
        assertEquals(-5000L, FinancialDomain.cashDelta(migrated))
        assertEquals(listOf(migrated), FinanceMigration.migrate(listOf(migrated)))
        assertEquals(old.id, migrated.id)
        assertEquals(old.createdAt, migrated.createdAt)
    }
    @Test fun newCardPurchaseCreatesLiabilityWithoutImmediateCashDebit() {
        val item = FinancialDomain.normalize(Item(type = "expense", title = "Compra", fields = mapOf("amount" to "50", "card" to "c", "status" to "PAID")))
        assertEquals(0L, FinancialDomain.cashDelta(item))
    }
    @Test fun accountReferencesCurrencyAndTransferAreValidated() {
        val brl = Item(id = "a", type = "account", title = "Real")
        val usd = Item(id = "b", type = "account", title = "Dólar", fields = mapOf("currency" to "USD"))
        assertFails { FinanceActions.transfer(1000, brl, brl, today) }
        assertFails { FinanceActions.transfer(1000, brl, usd, today) }
        assertFails { FinancialDomain.validate(Item(type = "expense", title = "Erro", fields = mapOf("amount" to "1", "account" to "missing")), listOf(brl)) }
    }
    @Test fun migrationPreservesAllOtherModulesUnknownFieldsAndTrash() {
        val note = Item(type = "note", title = "Nota", fields = mapOf("unknown" to "preservar"))
        val income = Item(type = "income", title = "Receita", deletedAt = 123, fields = mapOf("amount" to "10", "unknown" to "preservar"))
        val migrated = FinanceMigration.migrate(listOf(note, income))
        assertEquals(note, migrated.first())
        assertEquals(123L, migrated.last().deletedAt)
        assertEquals("preservar", migrated.last().value("unknown"))
    }
    @Test fun legacyPlannedAndBillsKeepPendingState() {
        assertEquals(TransactionStatus.PENDING, FinancialDomain.storedStatus(Item(type = "bill", title = "Luz")))
        val item = Item(type = "expense", title = "Luz", fields = mapOf("planned" to "Sim"))
        assertFalse(FinancialDomain.settled(item))
        assertTrue(FinancialDomain.settled(item.copy(done = true)))
    }
    @Test fun goalContributionAndDebtInstallmentUseCanonicalMoneyAndRejectInvalidPrecision() {
        val goal = Item(type = "savings_goal", title = "Reserva", fields = mapOf("amount" to "1000", "monthlyContribution" to "33,33"))
        val normalized = FinancialDomain.normalize(goal)
        assertEquals("3333", normalized.value("monthlyContributionMinor"))
        assertEquals("33.33", normalized.value("monthlyContribution"))
        val debt = Item(type = "debt", title = "Empréstimo", fields = mapOf("amount" to "1000", "installmentAmount" to "99,99"))
        assertEquals("9999", FinancialDomain.normalize(debt).value("installmentAmountMinor"))
        assertFails { FinancialDomain.validate(debt.copy(fields = debt.fields + ("installmentAmount" to "0.001"))) }
        assertFails { FinancialDomain.validate(goal.copy(fields = goal.fields + ("monthlyContribution" to "-10"))) }
    }
    @Test fun reminderValidationRunsBeforeAnyFinancialRecordIsPersisted() {
        val item = Item(type = "expense", title = "Luz", fields = mapOf("amount" to "10", "reminder" to "09:30"))
        FinancialDomain.validate(item)
        assertFails { FinanceActions.prepareSave(item.copy(fields = item.fields + ("reminder" to "25:00")), emptyList()) }
        assertFails { FinanceActions.prepareSave(item.copy(fields = item.fields + ("reminder" to "cedo")), emptyList()) }
    }
}
