package app.veyra.data
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.veyra.model.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun crudSurvivesDatabaseReopenAndBackupRoundtrip() {
        val name = "test-${UUID.randomUUID()}.db"
        try {
            val original = Entry("stable",EntryKind.EXPENSE,"Mercado", amountCents=12345,date="2026-10-05")
            EntryStore(context,name).use { it.save(original) }
            EntryStore(context,name).use { db ->
                assertEquals(original, db.all().single())
                db.save(original.copy(title="Mercado editado"))
                val backup=db.exportJson()
                db.delete("stable"); assertTrue(db.all().isEmpty())
                db.importJson(backup)
                assertEquals("Mercado editado", db.all().single().title)
                assertEquals(12345L, db.all().single().amountCents)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun invalidBackupCannotPartiallyChangeExistingData() {
        val name="test-${UUID.randomUUID()}.db"
        try { EntryStore(context,name).use { db ->
            db.save(Entry("keep",EntryKind.NOTE,"Preservar",date="2026-10-05"))
            val original = db.exportJson()
            val invalid = """{"schema":1,"entries":[{"id":"new","kind":"NOTE","title":"Ok","body":"","amount":0,"date":"2026-10-05","done":false,"created":1},{"id":"bad","kind":"NOTE","title":"","body":"","amount":0,"date":"invalid","done":false,"created":2}]}"""
            try { db.importJson(invalid); fail("Invalid backup accepted") } catch(_: IllegalArgumentException) { }
            assertEquals(original, db.exportJson())
        } } finally { context.deleteDatabase(name) }
    }
}
