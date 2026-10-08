package app.veyra.model

enum class TaskOrder(val label: String) { PRIORITY("Prioridade"), DEADLINE("Prazo"), RECENT("Mais recentes") }
enum class NoteOrder(val label: String) { CREATED("Mais recentes"), TITLE("Título") }

object Organizer {
    private fun priority(item: Item) = when (item.value("priority")) { "Urgente" -> 0; "Alta" -> 1; "Média" -> 2; else -> 3 }
    fun tasks(items: List<Item>, order: TaskOrder): List<Item> {
        val rank = when (order) {
            TaskOrder.PRIORITY -> compareBy<Item> { priority(it) }.thenBy { it.date.ifBlank { "9999-12-31" } }
            TaskOrder.DEADLINE -> compareBy<Item> { it.date.ifBlank { "9999-12-31" } }.thenBy { priority(it) }
            TaskOrder.RECENT -> compareByDescending<Item> { it.createdAt }
        }
        return items.filter { it.type == "task" && it.deletedAt == 0L }.sortedWith(compareBy<Item> { it.done }.then(rank).thenBy { it.id })
    }
    fun notes(items: List<Item>, order: NoteOrder): List<Item> {
        val rank = when (order) {
            NoteOrder.CREATED -> compareByDescending<Item> { it.createdAt }
            NoteOrder.TITLE -> compareBy<Item> { it.title.lowercase(java.util.Locale.ROOT) }
        }
        return items.filter { it.deletedAt == 0L }.sortedWith(compareByDescending<Item> { it.value("pinned") in setOf("yes", "Sim") }
            .thenByDescending { it.favorite }.then(rank).thenBy { it.id })
    }
}
