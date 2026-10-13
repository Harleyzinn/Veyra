package app.veyra.model

import kotlin.test.*

class OrganizerTest {
    @Test fun upcomingScopesIncludeExactlySevenCalendarDaysAndExcludeDoneOrDeleted(){
        val today=java.time.LocalDate.of(2026,10,13)
        val item=Item(type="task",title="Tarefa",date=today.plusDays(6).toString())
        assertTrue(Organizer.taskScope(item,"Próximos 7 dias",today))
        assertFalse(Organizer.taskScope(item.copy(date=today.plusDays(7).toString()),"Próximos 7 dias",today))
        assertFalse(Organizer.taskScope(item.copy(done=true),"Próximos 7 dias",today))
        assertFalse(Organizer.taskScope(item.copy(deletedAt=1),"Todas",today))
        assertTrue(Organizer.taskScope(item.copy(date=today.plusDays(1).toString()),"Amanhã",today))
        assertFalse(Organizer.taskScope(item.copy(date="inválida"),"Atrasadas",today))
    }
    @Test fun archiveRestorePreservesContentPinFavoriteAndMetadata(){
        val note=Item(type="note",title="Nota",notes="Texto completo".repeat(100),favorite=true,fields=mapOf("pinned" to "yes","attachment" to "local"))
        val archived=Organizer.toggleArchived(note)
        assertEquals("Sim",archived.value("archived"));assertEquals(note.notes,archived.notes)
        assertEquals("local",archived.value("attachment"));assertTrue(Organizer.pinned(archived))
        assertTrue(Organizer.noteMatches(archived,NoteFilter.FAVORITES))
        assertTrue(Organizer.noteMatches(archived,NoteFilter.PINNED))
        assertFalse(Organizer.noteMatches(note.copy(favorite=false),NoteFilter.FAVORITES))
        assertEquals("Não",Organizer.toggleArchived(archived).value("archived"))
        assertFailsWith<IllegalArgumentException>{Organizer.toggleArchived(note.copy(type="task"))}
    }
    @Test fun quickTaskHonorsDatePriorityAndRejectsInvalidTitles() {
        val today=java.time.LocalDate.of(2026,10,12)
        val task=Organizer.quickTask("  Fazer revisão  ",today,"Alta")
        assertEquals("Fazer revisão",task.title);assertEquals(today.toString(),task.date)
        assertEquals("Alta",task.value("priority"));assertFalse(task.done)
        assertEquals("",Organizer.quickTask("Sem prazo",null,"Média").date)
        assertEquals("2026-10-13",Organizer.quickTask("Amanhã",today.plusDays(1),"Urgente").date)
        assertFailsWith<IllegalArgumentException>{Organizer.quickTask("  ",today,"Alta")}
        assertFailsWith<IllegalArgumentException>{Organizer.quickTask("x".repeat(201),today,"Alta")}
    }
    @Test fun pinningPreservesFullNoteAndExistingMetadata() {
        val note=Item(type="note",title="Nota",notes="Corpo integral".repeat(100),favorite=true,fields=mapOf("url" to "https://example.com","attachment" to "local","pinned" to "Sim"))
        val unpinned=Organizer.togglePinned(note)
        assertFalse(Organizer.pinned(unpinned));assertEquals(note.notes,unpinned.notes)
        assertEquals("local",unpinned.value("attachment"));assertTrue(unpinned.favorite)
        assertTrue(Organizer.pinned(Organizer.togglePinned(unpinned)))
        assertFailsWith<IllegalArgumentException>{Organizer.togglePinned(note.copy(deletedAt=1))}
    }
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
