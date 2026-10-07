package app.veyra.feature.finance

import app.veyra.model.Item

object FinanceMigration {
    const val VERSION = 3
    fun migrate(items: List<Item>): List<Item> = items.map { item ->
        if (item.type !in FinancialDomain.financialTypes || item.value("financialVersion") == VERSION.toString()) item
        else {
            val legacy = if (item.type == "expense" && item.value("card").isNotBlank() && FinancialDomain.settled(item))
                item.copy(fields = item.fields + ("legacyCardCash" to "yes")) else item
            val card = items.firstOrNull { it.type == "card" && it.id == legacy.value("card") }
            val dated = if (legacy.type == "expense" && card != null && legacy.value("dueDate").isBlank() && legacy.date.isNotBlank()) {
                legacy.copy(fields = legacy.fields + ("dueDate" to CardEngine.cycleDueDate(java.time.LocalDate.parse(legacy.date), card).toString()))
            } else legacy
            FinancialDomain.normalize(dated)
        }
    }
}
