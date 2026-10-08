package app.veyra.model

import kotlin.test.*

class OrganizerTest {
    @Test fun deadlinesKeepUndatedAndCompletedTasksAfterActionableTasks() {
        val undated = Item(id = "none", type = "task", title = "Sem prazo", date = "")
        val urgent = undated.copy(id = "urgent", date = "2026-10-10", fields = mapOf("priority" to "Urgente"))
        val early = undated.copy(id = "early", date = "2026-10-08")
        val done = early.copy(id = "done", done = true, date = "2026-01-01")
        val rows = listOf(undated, urgent, early, done, early.copy(id = "deleted", deletedAt = 1))
        assertEquals(listOf(early, urgent, undated, done), Organizer.tasks(rows, TaskOrder.DEADLINE))
        assertEquals(urgent, Organizer.tasks(rows, TaskOrder.PRIORITY).first())
    }
    @Test fun noteOrderHonorsPinnedAndFavoritesWithoutModifyingOriginals() {
        val a = Item(id = "a", type = "note", title = "A", createdAt = 1)
        val z = a.copy(id = "z", title = "Z", createdAt = 2)
        val favorite = z.copy(id = "favorite", favorite = true)
        val pinned = z.copy(id = "pinned", fields = mapOf("pinned" to "yes"))
        val rows = listOf(z, a, favorite, pinned)
        assertEquals(listOf(pinned, favorite, a, z), Organizer.notes(rows, NoteOrder.TITLE))
        assertEquals(listOf(pinned, favorite, z, a), Organizer.notes(rows, NoteOrder.CREATED))
        assertEquals(listOf(z, a, favorite, pinned), rows)
    }
}
