package app.veyra.model
import kotlin.test.*
class MoneyTest {
    @Test fun decimalMoneyNeverUsesFloatingPoint() {
        assertEquals(12345L, Money.parseCents("123,45"))
        assertFails { Money.parseCents("0.001") }
        assertFails { Money.parseCents("999999999999999999999") }
    }
    @Test fun balanceIgnoresNonFinancialEntries() {
        val entries = listOf(
            Entry("1", EntryKind.INCOME, "Salário", amountCents=100000, date="2026-10-05"),
            Entry("2", EntryKind.EXPENSE, "Compra", amountCents=12345, date="2026-10-05"),
            Entry("3", EntryKind.TASK, "Tarefa", amountCents=999, date="2026-10-05")
        )
        assertEquals(87655L, Money.balance(entries))
    }
}
