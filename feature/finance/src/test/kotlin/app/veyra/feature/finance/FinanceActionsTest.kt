package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class FinanceActionsTest {
    @Test fun deletingAndRestoringRuleCascadesOnlyItsPendingMembers(){
        val rule=Item(id="series",type="recurring_rule",title="Serviço",date="2026-10-10",fields=mapOf("amountMinor" to "1000","transactionType" to "expense","frequency" to "MONTHLY"))
        val pending=RecurrenceEngine.occurrence(rule,LocalDate.parse("2030-01-10"))
        val paid=FinanceActions.settle(RecurrenceEngine.occurrence(rule,LocalDate.parse("2026-10-10")),LocalDate.parse("2026-10-10"))
        val previouslyDeleted=RecurrenceEngine.occurrence(rule,LocalDate.parse("2027-01-10")).copy(deletedAt=1)
        val other=pending.copy(id="other",fields=pending.fields+("source" to "other")+("recurrenceRuleId" to "other"))
        val trashed=FinanceActions.trash(rule,listOf(rule,pending,paid,previouslyDeleted,other),20)
        assertEquals(setOf(rule.id,pending.id),trashed.map{it.id}.toSet())
        val restored=FinanceActions.restore(trashed.first(),trashed+listOf(paid,previouslyDeleted,other))
        assertEquals(setOf(rule.id,pending.id),restored.map{it.id}.toSet())
        assertTrue(restored.all{it.deletedAt==0L})
    }
    @Test fun simplifiedIncomeAndExpenseNormalizeWithoutRequiringOptionalFields() {
        val item = Item(type = "expense", title = "Pizza", date = "2026-10-06", fields = mapOf("amount" to "42,90", "category" to "Alimentação", "status" to "PAID"))
        val saved = FinanceActions.prepareSave(item, emptyList()).single()
        assertEquals(4290L, FinancialDomain.amount(saved))
        assertEquals("3", saved.value("financialVersion"))
        assertEquals("paid", saved.value("status"))
    }
    @Test fun recurringSalaryStoresOneRuleAndOnlyExistingReceivedOccurrence() {
        val item = Item(id = "salary", type = "income", title = "Salário", date = "2026-10-05", fields = mapOf("amount" to "3500", "status" to "RECEIVED", "recurrence" to "yes", "frequency" to "MONTHLY", "dayOfMonth" to "5"))
        val saved = FinanceActions.prepareSave(item, emptyList())
        assertEquals(2, saved.size)
        assertEquals("recurring_rule", saved.first().type)
        assertEquals("recurring:salary:2026-10-05", saved.last().id)
        val actual = FinanceEngine.transactions(saved, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31))
        assertEquals(3, actual.size)
        assertEquals(350000L, FinanceEngine.currentBalance(saved, LocalDate.of(2026, 10, 6)))
    }
    @Test fun pendingRecurrenceDoesNotMaterializeAnyFutureRecord() {
        val item = Item(type = "expense", title = "Internet", date = "2026-10-10", fields = mapOf("amount" to "119.90", "status" to "PENDING", "recurrence" to "yes", "frequency" to "MONTHLY", "recurrenceEnd" to "2026-12-31"))
        val saved = FinanceActions.prepareSave(item, emptyList())
        assertEquals(1, saved.size)
        assertEquals("2026-12-31", saved.single().value("endDate"))
    }
    @Test fun installmentCreationIsAnAtomicListWithOneParentAndNoDuplicateFullExpense() {
        val card = Item(id = "c", type = "card", title = "Cartão", fields = mapOf("closing" to "10", "due" to "20"))
        val item = Item(type = "expense", title = "Celular", date = "2026-10-06", fields = mapOf("amount" to "1899", "installments" to "12", "card" to card.id))
        val saved = FinanceActions.prepareSave(item, listOf(card))
        assertEquals(13, saved.size)
        assertEquals("installment_plan", saved.first().type)
        assertEquals(189900L, saved.filter { it.type == "expense" }.sumOf(FinancialDomain::amount))
    }
    @Test fun customRulesFillMissingFieldsButRespectExplicitChoiceAndDisabledRules() {
        val rule = Item(type = "financial_rule", title = "Uber", fields = mapOf("contains" to "UBER", "category" to "Transporte", "enabled" to "yes", "kind" to "expense"))
        val item = Item(type = "expense", title = "Uber da viagem", fields = mapOf("amount" to "20"))
        assertEquals("Transporte", FinanceActions.prepareSave(item, listOf(rule)).single().value("category"))
        assertEquals("Viagem", FinanceActions.prepareSave(item.copy(fields = item.fields + ("category" to "Viagem")), listOf(rule)).single().value("category"))
        assertEquals("", FinanceActions.prepareSave(item, listOf(rule.copy(fields = rule.fields + ("enabled" to "no")))).single().value("category"))
    }
    @Test fun templatesAndHistorySuggestionsUseRealRecordsAndFreshIdentifiers() {
        val item = Item(type = "expense", title = "Netflix", fields = mapOf("amount" to "39.90", "category" to "Assinaturas"))
        val template = FinanceActions.template(item)
        val a = FinanceActions.useTemplate(template)
        val b = FinanceActions.useTemplate(template)
        assertNotEquals(a.id, b.id)
        assertEquals(item.title, a.title)
        assertEquals("39.90", a.value("amount"))
        assertEquals(listOf(item), FinanceActions.suggestions("net", listOf(item), "expense"))
    }
    @Test fun financialCategoriesPreserveCustomCategoryAndSubcategoryNames() {
        val item = Item(type = "expense", title = "Curso", fields = mapOf("category" to "Desenvolvimento", "subcategory" to "Kotlin"))
        val custom = Item(type = "financial_category", title = "Pets", fields = mapOf("transactionType" to "expense"))
        assertTrue(FinancialCategories.categories(listOf(item, custom), "expense").containsAll(listOf("Desenvolvimento", "Pets", "Alimentação")))
        assertEquals(listOf("Kotlin"), FinancialCategories.subcategories(listOf(item), "Desenvolvimento"))
        val child = custom.copy(title = "Veterinário", fields = custom.fields + ("category" to "Pets"))
        assertEquals(listOf("Veterinário"), FinancialCategories.subcategories(listOf(custom, child), "Pets"))
        assertFalse("Veterinário" in FinancialCategories.categories(listOf(custom, child), "expense"))
    }
    @Test fun editingAnOccurrenceNeverTurnsItIntoANewRule() {
        val rule = Item(id = "salary", type = "recurring_rule", title = "Salário", date = "2026-10-05", fields = mapOf("amount" to "3500", "frequency" to "MONTHLY", "transactionType" to "income", "recurrence" to "yes"))
        val occurrence = RecurrenceEngine.occurrence(rule, LocalDate.of(2026, 10, 5))
        val saved = FinanceActions.prepareSave(occurrence.copy(fields = occurrence.fields + ("recurrence" to "yes")), listOf(rule)).single()
        assertEquals("income", saved.type)
        assertEquals(occurrence.id, saved.id)
    }
    @Test fun debtPaymentCreatesOneAtomicCashMovementAndAdvancesTrackedPrincipal() {
        val account = Item(id = "a", type = "account", title = "Carteira", fields = mapOf("opening" to "1000"))
        val debt = Item(id = "loan", type = "debt", title = "Empréstimo", fields = mapOf("amount" to "500", "paid" to "100"))
        val changes = FinanceActions.recordDebtPayment(debt, 20000, account, LocalDate.of(2026, 10, 6))
        assertEquals(30000L, FinancialDomain.amount(changes.first(), "paid"))
        assertEquals(80000L, FinanceEngine.currentBalance(listOf(account) + changes, LocalDate.of(2026, 10, 6)))
        assertEquals(changes.map { it.id }, FinanceActions.recordDebtPayment(debt, 20000, account, LocalDate.of(2026, 10, 6)).map { it.id })
        assertFails { FinanceActions.recordDebtPayment(debt, 50000, account) }
    }
    @Test fun signedFinancialSnapshotsAndClosingNumbersAreValid() {
        FinancialDomain.validate(Item(type = "networth_snapshot", title = "Patrimônio", fields = mapOf("amount" to "-500")))
        FinancialDomain.validate(Item(type = "month_close", title = "Fechamento", fields = mapOf("amount" to "-100")))
        assertFails { FinancialDomain.normalize(Item(type = "expense", title = "Erro", fields = mapOf("amount" to "10", "status" to "invalid"))) }
    }
    @Test fun deletingAndRestoringDebtPaymentKeepsLedgerAndPrincipalConsistent() {
        val account = Item(id = "a", type = "account", title = "Banco", fields = mapOf("opening" to "1000"))
        val debt = Item(id = "debt", type = "debt", title = "Dívida", fields = mapOf("amount" to "500", "paid" to "0"))
        val payment = FinanceActions.recordDebtPayment(debt, 10000, account, LocalDate.of(2026, 10, 6))
        val deleted = FinanceActions.trash(payment.last(), listOf(account) + payment, 123L)
        assertEquals(0L, FinancialDomain.amount(deleted.last(), "paid"))
        assertEquals(100000L, FinanceEngine.currentBalance(listOf(account) + deleted, LocalDate.of(2026, 10, 6)))
        val restored = FinanceActions.restore(deleted.first(), listOf(account) + deleted)
        assertEquals(10000L, FinancialDomain.amount(restored.last(), "paid"))
        assertEquals(90000L, FinanceEngine.currentBalance(listOf(account) + restored, LocalDate.of(2026, 10, 6)))
    }
    @Test fun deletingPlanCascadesOnlyItsActiveChildrenAndRestoreDoesNotReviveOlderTrash() {
        val plan = CardEngine.installments(Item(id = "plan", type = "expense", title = "Notebook", date = "2026-10-06", fields = mapOf("amount" to "300")), 3)
        val existing = plan.mapIndexed { index, item -> if (index == 1) item.copy(deletedAt = 10L) else item }
        val deleted = FinanceActions.trash(existing.first(), existing, 123L)
        assertEquals(3, deleted.size)
        val merged = (existing.filter { old -> deleted.none { it.id == old.id } } + deleted)
        val restored = FinanceActions.restore(deleted.first(), merged)
        assertEquals(3, restored.size)
        assertTrue(restored.none { it.id == existing[1].id })
    }
    @Test fun firstInstallmentControlsRecordedMonthAndRetainsOriginalPurchaseDate() {
        val item = Item(type = "expense", title = "Celular", date = "2026-10-06", fields = mapOf("amount" to "120", "installments" to "12", "firstInstallment" to "2026-11-30"))
        val saved = FinanceActions.prepareSave(item, emptyList())
        assertEquals("2026-11-30", saved[1].date)
        assertEquals("2026-10-06", saved[1].value("purchaseDate"))
        assertEquals("2027-02-28", saved[4].date)
    }
    @Test fun categoryEditorKindAndParentAliasesRemainCompatibleWithLegacyCategories() {
        val category = Item(type = "financial_category", title = "Pets", fields = mapOf("kind" to "expense"))
        val child = Item(type = "financial_category", title = "Veterinário", fields = mapOf("kind" to "expense", "parent" to "Pets"))
        assertTrue("Pets" in FinancialCategories.categories(listOf(category, child), "expense"))
        assertFalse("Veterinário" in FinancialCategories.categories(listOf(category, child), "expense"))
        assertFalse("Pets" in FinancialCategories.categories(listOf(category, child), "income"))
        assertEquals(listOf("Veterinário"), FinancialCategories.subcategories(listOf(category, child), "Pets"))
    }
    @Test fun restoreRequiresActiveSameCurrencyAccountAndCardWithoutChangingLegacyCash() {
        val account = Item(id = "a", type = "account", title = "Banco", deletedAt = 123)
        val card = Item(id = "c", type = "card", title = "Cartão", deletedAt = 123)
        val legacy = Item(type = "expense", title = "Compra antiga", deletedAt = 123, fields = mapOf("amount" to "50", "account" to "a", "card" to "c", "legacyCardCash" to "yes"))
        assertFails { FinanceActions.restore(legacy, listOf(account, card, legacy)) }
        assertFails { FinanceActions.restore(legacy, listOf(account.copy(deletedAt = 0), card, legacy)) }
        val active = listOf(account.copy(deletedAt = 0), card.copy(deletedAt = 0), legacy)
        val restored = FinanceActions.restore(legacy, active).single()
        assertEquals(legacy.fields, restored.fields)
        assertEquals(-5000L, FinancialDomain.cashDelta(restored))
        assertFails { FinanceActions.restore(legacy, active.map { if (it.id == "a") it.copy(fields = mapOf("currency" to "USD")) else it }) }
    }
    @Test fun permanentDeletionCannotBeRestoredAloneOrThroughItsPlan() {
        val plan = CardEngine.installments(Item(id = "p", type = "expense", title = "Compra", date = "2026-10-06", fields = mapOf("amount" to "20")), 2)
        val trashed = FinanceActions.trash(plan.first(), plan, 123L)
        val purged = trashed[1].copy(fields = trashed[1].fields + ("purged" to "yes"))
        val context = trashed.map { if (it.id == purged.id) purged else it }
        assertFails { FinanceActions.restore(purged, context) }
        assertTrue(FinanceActions.restore(context.first(), context).none { it.id == purged.id })
    }
}
