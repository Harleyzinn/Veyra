package app.veyra.model

enum class TaskOrder(val label: String) { PRIORITY("Prioridade"), DEADLINE("Prazo"), RECENT("Mais recentes") }
enum class NoteOrder(val label: String) { CREATED("Mais recentes"), TITLE("Título") }
enum class NoteFilter(val label: String) { ALL("Todas"), FAVORITES("Favoritas"), PINNED("Fixadas") }

object Organizer {
    fun taskScope(item: Item, scope: String, today: java.time.LocalDate): Boolean {
        if(item.type!="task" || item.deletedAt!=0L)return false
        val day=runCatching{java.time.LocalDate.parse(item.date)}.getOrNull()
        return when(scope){
            "Hoje"->!item.done && day==today
            "Amanhã"->!item.done && day==today.plusDays(1)
            "Próximos 7 dias"->!item.done && day!=null && day in today..today.plusDays(6)
            "Atrasadas"->!item.done && day!=null && day<today
            "Concluídas"->item.done
            "Todas"->true
            else->!item.done
        }
    }
    fun noteMatches(item:Item,filter:NoteFilter)=when(filter){NoteFilter.ALL->true;NoteFilter.FAVORITES->item.favorite;NoteFilter.PINNED->pinned(item)}
    fun toggleArchived(item:Item):Item {
        require(item.type=="note" && item.deletedAt==0L){"Nota indisponível"}
        return item.copy(fields=item.fields+("archived" to if(item.value("archived")=="Sim")"Não"else"Sim"))
    }
    fun quickTask(title: String, day: java.time.LocalDate?, priority: String): Item {
        require(title.trim().length in 1..200) { "Use um título entre 1 e 200 caracteres" }
        require(priority in setOf("Baixa", "Média", "Alta", "Urgente")) { "Prioridade inválida" }
        return Item(type="task",title=title.trim(),date=day?.toString().orEmpty(),fields=mapOf("priority" to priority,"status" to "A fazer"))
    }
    fun pinned(item: Item) = item.value("pinned") in setOf("yes", "Sim")
    fun togglePinned(item: Item): Item {
        require(item.type in setOf("note", "journal", "link") && item.deletedAt==0L) { "Nota indisponível" }
        return item.copy(fields=item.fields+("pinned" to if(pinned(item)) "no" else "yes"))
    }
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
        return items.filter { it.deletedAt == 0L }.sortedWith(compareByDescending<Item> { pinned(it) }
            .thenByDescending { it.favorite }.then(rank).thenBy { it.id })
    }
}
