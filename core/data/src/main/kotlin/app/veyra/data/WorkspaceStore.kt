package app.veyra.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.veyra.model.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.math.BigDecimal

@Entity(tableName="items",indices=[Index(value=["type","date"]),Index(value=["parentId"])])
data class ItemRecord(@PrimaryKey val id:String,val type:String,val title:String,val notes:String,
    val date:String,val done:Boolean,val favorite:Boolean,val tags:String,val parentId:String,
    val payload:String,val deletedAt:Long,val createdAt:Long) {
    fun model()=Item(id,type,title,notes,date,done,favorite,tags,parentId,jsonMap(JSONObject(payload)),deletedAt,createdAt)
}
@Entity(tableName="preferences") data class PreferenceRecord(@PrimaryKey val key:String,val value:String)
@Dao interface WorkspaceDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC") fun all():List<ItemRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun save(item:ItemRecord)
    @Query("SELECT * FROM preferences") fun preferences():List<PreferenceRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun preference(value:PreferenceRecord)
    @Query("DELETE FROM items WHERE type=:type") fun clearModule(type:String)
}
private fun jsonMap(o:JSONObject):Map<String,String> = o.keys().asSequence().associateWith{o.getString(it)}

class WorkspaceStore(context:Context,name:String="veyra.db"):AutoCloseable {
    private val db=Room.databaseBuilder(context.applicationContext,VeyraDatabase::class.java,name).addMigrations(MIGRATION).build()
    override fun close()=db.close()
    fun all()=db.workspace().all().map{it.model()}
    fun preferences()=db.workspace().preferences().associate{it.key to it.value}
    fun preference(key:String,value:String) { db.workspace().preference(PreferenceRecord(key,value)) }
    fun save(i:Item) {
        require(i.id.isNotBlank() && i.title.isNotBlank() && i.title.length<=200 && i.notes.length<=100_000)
        require(i.type.matches(Regex("[a-z_]{1,40}")))
        if(i.date.isNotBlank()) LocalDate.parse(i.date)
        db.workspace().save(ItemRecord(i.id,i.type,i.title,i.notes,i.date,i.done,i.favorite,i.tags,i.parentId,JSONObject(i.fields).toString(),i.deletedAt,i.createdAt))
    }
    fun saveAll(items:List<Item>) = db.runInTransaction { items.forEach(::save) }
    fun clearModule(type:String)=db.workspace().clearModule(type)
    fun exportJson():String = JSONObject().put("schema",2).put("items",JSONArray().apply{all().forEach{put(toJson(it))}})
        .put("preferences",JSONObject(preferences().filterKeys{it !in setOf("lock","onlineKey")})).toString()
    fun importJson(text:String) {
        require(text.toByteArray().size<=30_000_000){"Backup excede 30 MB"}
        val root=JSONObject(text)
        val version=root.getInt("schema")
        require(version==1 || version==2){"Versão de backup incompatível"}
        val array=root.getJSONArray(if(version==1) "entries" else "items")
        require(array.length()<=30_000)
        val items=(0 until array.length()).map { n ->
            val o=array.getJSONObject(n)
            if(version==2) fromJson(o) else Item(o.getString("id"),o.getString("kind").lowercase(),o.getString("title"),o.getString("body"),o.getString("date"),o.getBoolean("done"),
                fields=if(o.getString("kind") in listOf("INCOME","EXPENSE")) mapOf("amount" to BigDecimal.valueOf(o.getLong("amount"),2).toPlainString()) else emptyMap(),createdAt=o.getLong("created"))
        }
        require(items.map{it.id}.distinct().size==items.size){"Identificadores duplicados"}
        items.forEach { i ->
            require(i.id.isNotBlank() && i.title.isNotBlank() && i.title.length<=200 && i.notes.length<=100_000)
            require(i.type.matches(Regex("[a-z_]{1,40}")))
            if(i.date.isNotBlank()) LocalDate.parse(i.date)
            if(i.type in listOf("expense","income","transfer","budget","subscription")) require(i.cents()>=0)
            require(i.fields.size<=100 && i.fields.all{(key,value)->value.length<=if(key=="attachment")14_000_000 else 500_000})
        }
        val prefs=if(version==2 && root.has("preferences")) jsonMap(root.getJSONObject("preferences")) else emptyMap()
        db.runInTransaction {
            items.forEach(::save)
            prefs.filterKeys{it in setOf("name","theme","profile","home","hidden","favoriteModules","focusMinutes","breakMinutes","weatherConsent","weatherMode","weatherCity","weatherTemperature","weatherCondition","aiEndpoint","aiModel")}.forEach{(k,v)->preference(k,v)}
        }
    }
    companion object {
        fun toJson(i:Item)=JSONObject().put("id",i.id).put("type",i.type).put("title",i.title).put("notes",i.notes).put("date",i.date)
            .put("done",i.done).put("favorite",i.favorite).put("tags",i.tags).put("parentId",i.parentId).put("fields",JSONObject(i.fields)).put("deletedAt",i.deletedAt).put("createdAt",i.createdAt)
        fun fromJson(o:JSONObject)=Item(o.getString("id"),o.getString("type"),o.getString("title"),o.optString("notes"),o.optString("date"),o.optBoolean("done"),o.optBoolean("favorite"),o.optString("tags"),o.optString("parentId"),jsonMap(o.getJSONObject("fields")),o.optLong("deletedAt"),o.getLong("createdAt"))
        val MIGRATION=object:Migration(1,2) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS items (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, date TEXT NOT NULL, done INTEGER NOT NULL, favorite INTEGER NOT NULL, tags TEXT NOT NULL, parentId TEXT NOT NULL, payload TEXT NOT NULL, deletedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_type_date ON items(type,date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_items_parentId ON items(parentId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS preferences (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
                db.query("SELECT * FROM entries").use { c -> while(c.moveToNext()) {
                    val kind=c.getString(c.getColumnIndexOrThrow("kind")).lowercase()
                    val amount=c.getLong(c.getColumnIndexOrThrow("amount"))
                    val fields=if(kind in listOf("income","expense")) JSONObject().put("amount",BigDecimal.valueOf(amount,2).toPlainString()) else JSONObject()
                    db.execSQL("INSERT OR IGNORE INTO items VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf(c.getString(0),kind,c.getString(2),c.getString(3),c.getString(5),c.getInt(6),0,"","",fields.toString(),0,c.getLong(7)))
                } }
            }
        }
    }
}
