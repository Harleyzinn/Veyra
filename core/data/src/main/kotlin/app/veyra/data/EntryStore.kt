package app.veyra.data

import android.content.Context
import androidx.room.*
import app.veyra.model.*
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName="entries", indices=[Index(value=["kind", "date"])])
data class EntryRecord(@PrimaryKey val id: String, val kind: String, val title: String,
    val body: String, val amount: Long, val date: String, val done: Boolean, val created: Long) {
    fun model() = Entry(id, EntryKind.valueOf(kind), title, body, amount, date, done, created)
}
@Dao interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY created DESC") fun all(): List<EntryRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun save(entry: EntryRecord)
    @Query("DELETE FROM entries WHERE id=:id") fun delete(id: String)
}
@Database(entities=[EntryRecord::class, ItemRecord::class, PreferenceRecord::class], version=2, exportSchema=true)
abstract class VeyraDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun workspace(): WorkspaceDao
}

class EntryStore(context: Context, databaseName: String = "veyra.db") : AutoCloseable {
    private val db = Room.databaseBuilder(context.applicationContext, VeyraDatabase::class.java, databaseName).addMigrations(WorkspaceStore.MIGRATION).build()
    override fun close() { db.close() }
    fun all(): List<Entry> = db.entries().all().map { it.model() }
    fun save(e: Entry) {
        require(e.title.isNotBlank() && e.amountCents >= 0)
        db.entries().save(EntryRecord(e.id,e.kind.name,e.title,e.body,e.amountCents,e.date,e.done,e.createdAt))
    }
    fun delete(id: String) { db.entries().delete(id) }
    fun exportJson(): String = JSONObject().put("schema", 1).put("entries", JSONArray().apply {
        all().forEach { e -> put(JSONObject().put("id",e.id).put("kind",e.kind.name).put("title",e.title)
            .put("body",e.body).put("amount",e.amountCents).put("date",e.date).put("done",e.done).put("created",e.createdAt)) }
    }).toString(2)
    /** Validate everything before an atomic merge. Existing matching IDs are replaced. */
    fun importJson(text: String) {
        require(text.toByteArray().size <= 5_000_000) { "Backup excede 5 MB" }
        val root = JSONObject(text)
        require(root.getInt("schema") == 1) { "Versão de backup incompatível" }
        val array = root.getJSONArray("entries")
        require(array.length() <= 20_000)
        val entries = (0 until array.length()).map { i -> array.getJSONObject(i).let { o ->
            Entry(o.getString("id"), EntryKind.valueOf(o.getString("kind")), o.getString("title"),
                o.getString("body"), o.getLong("amount"), o.getString("date"), o.getBoolean("done"), o.getLong("created"))
        } }
        entries.forEach { require(it.id.isNotBlank() && it.title.isNotBlank() && it.amountCents >= 0); java.time.LocalDate.parse(it.date) }
        db.runInTransaction { entries.forEach(::save) }
    }
}
