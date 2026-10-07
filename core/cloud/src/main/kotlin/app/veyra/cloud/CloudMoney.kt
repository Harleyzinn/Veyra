package app.veyra.cloud

import app.veyra.model.Item
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/** Legacy decimal values must retain their currency precision before becoming query indexes. */
internal object CloudMoney {
    const val MAX_MINOR = 900_000_000_000_000L
    private val nonFinancial = setOf("notes", "tasks", "settings", "workspace")

    fun financial(item: Item) = CloudCollections.forType(item.type) !in nonFinancial

    fun amount(item: Item): Long {
        val exact = item.value("amountMinor")
        if (exact.isNotBlank()) return bounded(exact.toLongOrNull()
            ?: throw IllegalArgumentException("O valor financeiro em unidades menores não é um inteiro válido."))
        val value = item.value("amount")
        if (value.isBlank()) return 0L
        if (!financial(item)) return runCatching { item.cents() }.getOrDefault(0L)
        return parse(value, item.value("currency").ifBlank { "BRL" })
    }

    private fun parse(value: String, currency: String): Long {
        try {
            val scale = Currency.getInstance(currency).defaultFractionDigits
            require(scale in 0..3)
            val text = value.trim().replace("R$", "").replace(" ", "")
            val normalized = when {
                ',' in text && '.' in text && text.lastIndexOf('.') > text.lastIndexOf(',') -> {
                    require(text.matches(Regex("[+-]?\\d{1,3}(,\\d{3})+\\.\\d+")))
                    text.replace(",", "")
                }
                ',' in text -> {
                    require(text.matches(Regex("[+-]?(\\d+|\\d{1,3}(\\.\\d{3})+),\\d+")))
                    text.replace(".", "").replace(',', '.')
                }
                currency == "BRL" && value.contains("R$") && text.matches(Regex("[+-]?\\d{1,3}(\\.\\d{3})+")) -> text.replace(".", "")
                else -> text
            }
            require(normalized.matches(Regex("[+-]?\\d+(\\.\\d+)?")))
            return bounded(BigDecimal(normalized).setScale(scale, RoundingMode.UNNECESSARY)
                .movePointRight(scale).longValueExact())
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Valor financeiro ou moeda inválidos. Confira o registro antes de sincronizar.", exception)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException("Valor financeiro incompatível com a precisão da moeda ou acima do limite seguro.", exception)
        }
    }

    private fun bounded(value: Long): Long = value.also {
        require(it in -MAX_MINOR..MAX_MINOR) { "Valor financeiro acima do limite seguro." }
    }
}
