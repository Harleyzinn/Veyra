package app.veyra.feature.finance

import app.veyra.model.Item
import java.time.LocalDate
import kotlin.test.*

class CardEngineTest {
    @Test fun partialPaymentsKeepRemainingInvoiceAndDebitOnlyTheirOwnAmounts() {
        val buy=purchase(value="100.01")
        val original=CardEngine.invoices(listOf(buy),card,date("2026-10-01"),date("2026-12-31")).single()
        val first=CardEngine.settleInvoice(original,"account",date("2026-10-18"),3333).single()
        val remaining=CardEngine.invoices(listOf(buy,first),card,date("2026-10-01"),date("2026-12-31")).single()
        assertEquals(6668L,remaining.outstandingMinor)
        assertEquals(-3333L,FinancialDomain.cashDelta(first))
        assertEquals("2026-10-18",first.value("settledDate"))
        assertFails{CardEngine.settleInvoice(remaining,"account",amountMinor=6669)}
        assertFails{CardEngine.settleInvoice(remaining,"account",amountMinor=0)}
        val second=CardEngine.settleInvoice(remaining,"account",date("2026-10-20"),6668).single()
        assertNotEquals(first.id,second.id)
        assertEquals(0L,CardEngine.invoices(listOf(buy,first,second),card,date("2026-10-01"),date("2026-12-31")).single().outstandingMinor)
        assertEquals(-10001L,listOf(first,second).sumOf(FinancialDomain::cashDelta))
    }
    private val card = Item(id = "card", type = "card", title = "Cartão", fields = mapOf("closing" to "10", "due" to "20", "limit" to "5000"))
    private fun date(text: String) = LocalDate.parse(text)
    private fun purchase(day: String = "2026-10-09", value: String = "100.00") = FinancialDomain.normalize(Item(type = "expense", title = "Compra", date = day, fields = mapOf("card" to card.id, "amount" to value, "status" to "paid")))
    @Test fun purchasesBeforeOnAndAfterClosingUseCorrectInvoices() {
        assertEquals(date("2026-10-20"), CardEngine.cycleDueDate(date("2026-10-09"), card))
        assertEquals(date("2026-11-20"), CardEngine.cycleDueDate(date("2026-10-10"), card))
        assertEquals(date("2026-11-20"), CardEngine.cycleDueDate(date("2026-10-11"), card))
        val following = card.copy(fields = card.fields + ("due" to "5"))
        assertEquals(date("2026-11-05"), CardEngine.cycleDueDate(date("2026-10-09"), following))
    }
    @Test fun FebruaryLeapYearAndYearRolloverDoNotProduceInvalidDates() {
        val monthEnd = card.copy(fields = card.fields + mapOf("closing" to "31", "due" to "31"))
        assertEquals(date("2024-03-31"), CardEngine.cycleDueDate(date("2024-02-28"), monthEnd))
        assertEquals(date("2024-04-30"), CardEngine.cycleDueDate(date("2024-02-29"), monthEnd))
        assertEquals(date("2027-01-20"), CardEngine.cycleDueDate(date("2026-12-10"), card))
    }
    @Test fun invoicePaymentDebitsCashWithoutCountingThePurchaseTwice() {
        val buy = purchase()
        val invoice = CardEngine.invoices(listOf(buy), card, date("2026-10-01"), date("2026-12-31")).single()
        assertEquals(10000L, invoice.outstandingMinor)
        val payments = CardEngine.settleInvoice(invoice, "account", date("2026-10-20"))
        assertEquals(payments.map { it.id }, CardEngine.settleInvoice(invoice, "account", date("2026-10-20")).map { it.id })
        assertEquals(-10000L, FinancialDomain.cashDelta(payments.single()))
        assertFalse(FinancialDomain.isExpense(payments.single()))
        assertEquals(0L, CardEngine.invoices(listOf(buy) + payments, card, date("2026-10-01"), date("2026-12-31")).single().outstandingMinor)
        assertFails { CardEngine.settleInvoice(invoice.copy(outstandingMinor = 0), "account") }
    }
    @Test fun installmentsRetainEveryCentAndMonthEndAnchor() {
        val item = purchase("2026-01-31", "10.00")
        val records = CardEngine.installments(item, 3, date("2026-01-31"), card)
        assertEquals("installment_plan", records.first().type)
        val installments = records.drop(1)
        assertEquals(listOf(334L, 333L, 333L), installments.map(FinancialDomain::amount))
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), installments.map { it.date })
        assertEquals(1000L, installments.sumOf(FinancialDomain::amount))
        assertTrue(installments.all { it.value("installmentPlanId") == item.id })
    }
    @Test fun explicitlyChosenFirstDueDateControlsInstallmentInvoices() {
        val item = purchase("2026-10-09", "1899.00").copy(fields = purchase().fields + mapOf("amount" to "1899.00", "amountMinor" to "189900", "firstInstallment" to "2026-11-30"))
        val records = CardEngine.installments(item, 12, date("2026-10-09"), card).drop(1)
        assertEquals("2026-11-30", records.first().value("dueDate"))
        assertEquals("2027-02-28", records[3].value("dueDate"))
        assertEquals(189900L, records.sumOf(FinancialDomain::amount))
    }
    @Test fun anticipationMovesFutureDueDatesAndPreservesAmountsAndAuditSource() {
        val records = CardEngine.installments(purchase("2027-01-01", "300"), 3, date("2027-01-01"), card)
        val changes = CardEngine.advanceInstallments(records, records.first().id, 2, date("2026-12-20"))
        assertEquals(2, changes.size)
        assertTrue(changes.all { it.value("dueDate") == "2026-12-20" && it.value("advancedFrom").isNotBlank() })
        assertEquals(20000L, changes.sumOf(FinancialDomain::amount))
        assertFails { CardEngine.advanceInstallments(records, records.first().id, 4, date("2026-12-20")) }
    }
    @Test fun limitIncludesFutureInstallmentsAndDropsPaidInvoices() {
        val records = CardEngine.installments(purchase("2026-10-09", "3600"), 12, date("2026-10-09"), card)
        assertEquals(360000L, CardEngine.usedLimit(records, card))
        assertEquals(140000L, CardEngine.availableLimit(records, card))
        val first = CardEngine.invoices(records, card, date("2026-10-01"), date("2026-10-31")).single()
        assertEquals(330000L, CardEngine.usedLimit(records + CardEngine.settleInvoice(first, "a", date("2026-10-20")), card))
    }
    @Test fun cashPreservedLegacyCardPurchaseDoesNotCreateNewDebt() {
        val legacy = purchase().copy(fields = purchase().fields + ("legacyCardCash" to "yes"))
        assertTrue(CardEngine.invoices(listOf(legacy), card, date("2026-10-01"), date("2026-12-31")).isEmpty())
    }
    @Test fun carriedInvoicesMergeOnlyHiddenPortionWithWindowPurchases() {
        val carry = Item(type = "card_carry", title = "Histórico", date = "2026-10-20", fields = mapOf("card" to card.id, "amountMinor" to "20000", "paidMinor" to "5000", "dueDate" to "2026-10-20"))
        val invoice = CardEngine.invoices(listOf(carry, purchase()), card, date("2026-10-01"), date("2026-10-31")).single()
        assertEquals(30000L, invoice.totalMinor)
        assertEquals(25000L, invoice.outstandingMinor)
        assertEquals(1, invoice.purchases.size)
    }
    @Test fun foreignCurrencyIsRetainedWhenOnlyCarryExists() {
        val usdCard = card.copy(fields = card.fields + ("currency" to "USD"))
        val carry = Item(type = "card_carry", title = "Histórico", date = "2026-10-20", fields = mapOf("card" to card.id, "currency" to "USD", "amountMinor" to "1000", "paidMinor" to "0", "dueDate" to "2026-10-20"))
        val invoice = CardEngine.invoices(listOf(carry), usdCard, date("2026-10-01"), date("2026-10-31")).single()
        assertEquals("USD", CardEngine.settleInvoice(invoice, "a").single().value("currency"))
    }
}
