package app.veyra.model

import java.math.BigDecimal
import java.math.RoundingMode

enum class EntryKind(val label: String) {
    TASK("Tarefa"), NOTE("Nota"), EXPENSE("Despesa"), INCOME("Receita"),
    EVENT("Evento"), HABIT("Hábito"), JOURNAL("Diário")
}

data class Entry(
    val id: String, val kind: EntryKind, val title: String,
    val body: String = "", val amountCents: Long = 0,
    val date: String, val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object Money {
    fun parseCents(text: String): Long = BigDecimal(text.trim().replace(',', '.'))
        .setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    fun balance(entries: List<Entry>): Long = entries.fold(0L) { total, entry ->
        when (entry.kind) {
            EntryKind.INCOME -> Math.addExact(total, entry.amountCents)
            EntryKind.EXPENSE -> Math.subtractExact(total, entry.amountCents)
            else -> total
        }
    }
}

data class ModuleSpec(val id: String, val title: String, val capabilities: List<String>)

data class EntityRef(val module: String, val id: String)
data class EntityLink(val source: EntityRef, val target: EntityRef, val relation: String)
