package app.veyra.cloud

import app.veyra.model.Item
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CloudMoneyTest {
    private fun legacy(value: String, currency: String = "BRL") = Item(type = "expense", title = "Legado",
        fields = mapOf("amount" to value, "currency" to currency))

    @Test fun persistedMinorUnitsHavePriorityAndKeepTheirSign() {
        assertEquals(800L, CloudMoney.amount(legacy("inválido", "JPY").copy(fields = mapOf("amountMinor" to "800", "amount" to "inválido", "currency" to "JPY"))))
        assertEquals(-100L, CloudMoney.amount(Item(type = "account", title = "Conta", fields = mapOf("amountMinor" to "-100"))))
    }
    @Test fun brazilianAndInternationalLegacyDecimalsBecomeExactIntegers() {
        assertEquals(123456L, CloudMoney.amount(legacy("R$ 1.234,56")))
        assertEquals(123400L, CloudMoney.amount(legacy("R$ 1.234")))
        assertEquals(1230L, CloudMoney.amount(legacy("12.30")))
        assertEquals(123456L, CloudMoney.amount(legacy("1,234.56", "USD")))
    }
    @Test fun zeroAndThreeDecimalCurrenciesNeverUseTwoDecimalFallback() {
        assertEquals(1234L, CloudMoney.amount(legacy("1234", "JPY")))
        assertEquals(12345L, CloudMoney.amount(legacy("12.345", "KWD")))
        assertFailsWith<IllegalArgumentException> { CloudMoney.amount(legacy("1.01", "JPY")) }
        assertFailsWith<IllegalArgumentException> { CloudMoney.amount(legacy("1.234", "BRL")) }
    }
    @Test fun malformedValuesAndUnknownCurrencyNeverBecomeZero() {
        for (value in listOf("inválido", "NaN", "Infinity", "1e3", "1,2,3", "1.2.3"))
            assertFailsWith<IllegalArgumentException>(value) { CloudMoney.amount(legacy(value)) }
        assertFailsWith<IllegalArgumentException> { CloudMoney.amount(legacy("10", "INVALID")) }
    }
    @Test fun monetaryLimitsAndOverflowAreCheckedBeforeWritingTheIndex() {
        assertEquals(CloudMoney.MAX_MINOR, CloudMoney.amount(legacy("9000000000000.00")))
        assertFailsWith<IllegalArgumentException> { CloudMoney.amount(legacy("9000000000000.01")) }
        for (value in listOf("900000000000001", "9223372036854775808", "1.5", "NaN"))
            assertFailsWith<IllegalArgumentException>(value) { CloudMoney.amount(legacy("1").copy(fields = mapOf("amountMinor" to value))) }
    }
    @Test fun nonFinancialLegacyTextDoesNotBecomeAFinancialTransaction() {
        assertEquals(0L, CloudMoney.amount(Item(type = "note", title = "Nota", fields = mapOf("amount" to "texto"))))
    }
}
