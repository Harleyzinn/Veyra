package app.veyra.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import app.veyra.model.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.math.BigDecimal
import java.util.UUID
import java.util.Locale

@Entity(tableName="items",indices=[Index(value=["type","date"]),Index(value=["parentId"])])
data class ItemRecord(@PrimaryKey val id:String,val type:String,val title:String,val notes:String,
    val date:String,val done:Boolean,val favorite:Boolean,val tags:String,val parentId:String,
    val payload:String,val deletedAt:Long,val createdAt:Long) {
    fun model()=Item(id,type,title,notes,date,done,favorite,tags,parentId,jsonMap(JSONObject(payload)),deletedAt,createdAt)
    companion object { fun from(i:Item)=ItemRecord(i.id,i.type,i.title,i.notes,i.date,i.done,i.favorite,i.tags,i.parentId,JSONObject(i.fields).toString(),i.deletedAt,i.createdAt) }
}
@Entity(tableName="preferences") data class PreferenceRecord(@PrimaryKey val key:String,val value:String)
@Dao interface WorkspaceDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC") fun all():List<ItemRecord>
    @Query("SELECT * FROM items WHERE id=:id") fun find(id:String):ItemRecord?
    @Query("SELECT * FROM items WHERE type=:type ORDER BY date DESC,id DESC") fun module(type:String):List<ItemRecord>
    @Query("SELECT COUNT(*) FROM items WHERE deletedAt=0 AND type!='cloud_settings'") fun visibleCount():Int
    @Query("SELECT id FROM items WHERE :includeInternal=1 OR type!='cloud_settings' ORDER BY id") fun ids(includeInternal:Boolean):List<String>
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun save(item:ItemRecord)
    @Query("SELECT * FROM preferences") fun preferences():List<PreferenceRecord>
    @Insert(onConflict=OnConflictStrategy.REPLACE) fun preference(value:PreferenceRecord)
}
private fun jsonMap(o:JSONObject):Map<String,String> = o.keys().asSequence().associateWith{o.getString(it)}

/** Local writes, index, revision, audit, and durable upload intent are committed atomically. */
class WorkspaceStore(context:Context,val databaseName:String=WorkspaceIdentity.database(context)):AutoCloseable {
    val uid=WorkspaceIdentity.activeUid(context)?.takeIf { databaseName==WorkspaceIdentity.databaseForUid(it) }
    private val db=Room.databaseBuilder(context.applicationContext,VeyraDatabase::class.java,databaseName).addMigrations(*MIGRATIONS).build()
    override fun close()=db.close()
    fun all()=db.workspace().all().map{it.model()}
    fun find(id:String)=db.workspace().find(id)?.model()
    fun visibleCount()=db.workspace().visibleCount()
    fun ids(includeInternal:Boolean=false)=db.workspace().ids(includeInternal)
    fun acknowledgedCount(ids:List<String>):Int=ids.distinct().chunked(900).sumOf{db.persistence().acknowledgedCount(it)}
    fun preferences()=db.workspace().preferences().associate{it.key to it.value}
    fun syncablePreferences()=preferences().filterKeys{it in SYNC_PREFERENCES}
    fun preference(key:String,value:String) { require(key.length in 1..100 && value.length<=100_000);db.workspace().preference(PreferenceRecord(key,value)) }
    fun stored(id:String):StoredItem? {
        val item=find(id) ?: return null
        val meta=db.persistence().metadata(id)
        return StoredItem(item,meta?.localRevision ?: 0,meta?.serverRevision ?: 0,meta?.updatedAt ?: item.createdAt)
    }
    fun save(item:Item) { validate(item);db.runInTransaction { saveLocal(item) } }
    fun saveAll(items:List<Item>) {
        require(items.map{it.id}.distinct().size==items.size){"Identificadores duplicados"}
        items.forEach(::validate)
        db.runInTransaction { items.forEach{saveLocal(it)} }
    }
    private fun saveLocal(input:Item,force:Boolean=false) {
        val old=find(input.id)
        require(old==null || !isPurged(old) || input.deletedAt!=0L){"Este registro foi excluído definitivamente"}
        val item=if(input.value("hasAttachment")=="yes" && input.value("attachment").isBlank() && old?.value("attachment").orEmpty().isNotBlank())input.copy(fields=input.fields+("attachment" to old!!.value("attachment")))else input
        if(!force && old==item)return
        val meta=db.persistence().metadata(item.id)
        val revision=Math.addExact(meta?.localRevision ?: 0,1)
        val now=System.currentTimeMillis()
        val operationId=UUID.randomUUID().toString()
        db.workspace().save(ItemRecord.from(item))
        index(item)
        db.persistence().metadata(ItemMetadataRecord(item.id,revision,meta?.serverRevision ?: 0,now,operationId))
        val conflict=db.persistence().conflictForItem(item.id)!=null
        db.persistence().outbox(SyncOutboxRecord(item.id,operationId,meta?.serverRevision ?: 0,revision,now,toJson(itemWithoutBinary(item)).toString(),conflict))
        val action=when {
            old==null -> "create"
            !isPurged(old) && isPurged(item) -> "purge"
            old.deletedAt==0L && item.deletedAt!=0L -> "trash"
            old.deletedAt!=0L && item.deletedAt==0L -> "restore"
            else -> "update"
        }
        db.persistence().audit(AuditEventRecord(operationId,item.id,action,old?.let{toJson(itemWithoutBinary(it)).toString()}.orEmpty(),toJson(itemWithoutBinary(item)).toString(),now,revision))
    }
    private fun index(item:Item) {
        db.persistence().summary(ItemSummaryRecord.from(item))
        db.persistence().removeSearch(item.id)
        db.persistence().search(ItemSearchRecord(item.id,searchContent(item)))
        if(item.type in financeTypes)db.persistence().index(FinanceIndexRecord.from(item)) else db.persistence().removeIndex(item.id)
    }
    fun clearModule(type:String) {
        val now=System.currentTimeMillis()
        saveAll(db.workspace().module(type).map{it.model()}.filter{it.deletedAt==0L}.map{it.copy(deletedAt=now)})
    }
    fun trash(id:String) { find(id)?.let{save(it.copy(deletedAt=System.currentTimeMillis()))} }
    fun restore(id:String) { find(id)?.let{require(!isPurged(it)){"Este registro foi excluído definitivamente"};save(it.copy(deletedAt=0))} }
    /** Permanent removal clears the visible payload but retains a synchronized tombstone and audit. */
    fun purge(id:String) {
        val item=find(id) ?: return
        require(item.deletedAt!=0L){"Mova para a lixeira antes de excluir definitivamente"}
        val suppression=item.fields.filterKeys{it in setOf("source","recurrenceRuleId","occurrenceDate")}
        save(item.copy(title="Registro excluído",notes="",tags="",fields=suppression+mapOf("purged" to "yes")))
    }
    fun audit(id:String,limit:Int=100):List<AuditEvent> = db.persistence().audit(id,limit.coerceIn(1,500)).map {
        AuditEvent(it.id,it.itemId,it.action,it.beforeJson.takeIf(String::isNotBlank)?.let{json->fromJson(JSONObject(json))},it.afterJson.takeIf(String::isNotBlank)?.let{json->fromJson(JSONObject(json))},it.changedAt,it.revision)
    }
    /** Overview never reads attachments; only the current month ledger is included. */
    fun overview(today:LocalDate=LocalDate.now()):List<Item> {
        val from=today.withDayOfMonth(1)
        val other=queryItems("i.type NOT IN (${placeholders(ledgerTypes.size)}) AND i.type!='cloud_settings'",ledgerTypes.toList())
        return (other+financeWindow(from,today.withDayOfMonth(today.lengthOfMonth()))).distinctBy{it.id}
    }
    /** A bounded ledger, reusable context, overdue obligations, and SQL-carried prior cash. */
    fun financeWindow(from:LocalDate,to:LocalDate):List<Item> {
        require(!to.isBefore(from))
        val context=queryItems("i.type IN (${placeholders((financeTypes-ledgerTypes).size)})",(financeTypes-ledgerTypes).toList())
        val activeCondition="deletedAt=0 AND type IN ('income','expense','transfer') AND ((date>=? AND date<=?) OR (bookedDate>=? AND bookedDate<=? AND cashDelta!=0) OR (dueDate>=? AND dueDate<=?) OR (dueDate<? AND status IN ('pending','expected','overdue','pendente','previsto','prevista','atrasado','atrasada')))"
        // ISO occurrenceDate contains no JSON escapes. A compact substring comparison
        // keeps API 26 compatibility without depending on SQLite's optional JSON extension.
        val occurrenceKey="\"occurrenceDate\":\""
        val recurrenceIds="SELECT f.id FROM finance_index f JOIN item_summaries s ON s.id=f.id WHERE f.type IN ('income','expense','transfer') AND f.ruleId!='' AND ((f.date>=? AND f.date<=?) OR (instr(s.payload,?)>0 AND substr(s.payload,instr(s.payload,?)+?,10) BETWEEN ? AND ?))"
        val ledgerCondition="($activeCondition) OR id IN ($recurrenceIds)"
        val ledgerArgs=listOf<Any>(from.toString(),to.toString(),from.toString(),to.toString(),from.toString(),to.toString(),from.toString(),from.toString(),to.toString(),occurrenceKey,occurrenceKey,occurrenceKey.length,from.toString(),to.toString())
        val ledger=queryItems("i.id IN (SELECT id FROM finance_index WHERE $ledgerCondition)",ledgerArgs)
        val prior=db.persistence().carry(from.toString())
        val currencies=(prior.map{it.currency}+context.map{it.value("currency").ifBlank{"BRL"}}+ledger.map{it.value("currency").ifBlank{"BRL"}}+"BRL").distinct()
        val carried=(prior+currencies.filter{currency->prior.none{it.currency==currency && it.accountId.isBlank()}}.map{AccountCarryRecord(it,"",0)}).map { carry ->
            Item(id="carry:${carry.currency}:${carry.accountId}",type="cash_carry",title="Saldo anterior",date=from.minusDays(1).toString(),
                fields=mapOf("amountMinor" to carry.amountMinor.toString(),"currency" to carry.currency,"account" to carry.accountId),createdAt=0)
        }
        val cardCarry=db.persistence().cardCarry(SimpleSQLiteQuery("SELECT cardId,currency,invoiceDueDate AS dueDate,SUM(cardChargeMinor) AS amountMinor,SUM(cardPaidMinor) AS paidMinor FROM finance_index WHERE deletedAt=0 AND cardId!='' AND (cardChargeMinor!=0 OR cardPaidMinor!=0) AND id NOT IN (SELECT id FROM finance_index WHERE $ledgerCondition) GROUP BY cardId,currency,invoiceDueDate",ledgerArgs.toTypedArray())).map { carry ->
            Item(id="card-carry:${carry.cardId}:${carry.dueDate}",type="card_carry",title="Resumo da fatura",date=carry.dueDate,
                fields=mapOf("card" to carry.cardId,"currency" to carry.currency,"dueDate" to carry.dueDate,"invoiceMonth" to carry.dueDate.take(7),"amountMinor" to carry.amountMinor.toString(),"paidMinor" to carry.paidMinor.toString()),createdAt=0)
        }
        return context+ledger+carried+cardCarry
    }
    fun financeWindow(from:String,to:String)=financeWindow(LocalDate.parse(from),LocalDate.parse(to))
    fun cashNet(through:LocalDate,currency:String="BRL"):Long=db.persistence().cashNet(through.toString(),currency) ?: 0
    fun financeSum(from:LocalDate,to:LocalDate,type:String,currency:String="BRL"):Long=db.persistence().sum(from.toString(),to.toString(),type,currency) ?: 0
    /** Exact links across the complete compact history, including installments outside the screen window. */
    fun financePlanMembers(planId:String,includeDeleted:Boolean=false):List<Item> {
        require(planId.isNotBlank() && planId.length<=512)
        val active=if(includeDeleted)""else"AND i.deletedAt=0"
        return queryItems("i.id IN (SELECT id FROM finance_index WHERE type IN ('income','expense','transfer')) $active AND instr(i.payload,?)>0",listOf(jsonMember("installmentPlanId",planId)))
            .filter{it.value("installmentPlanId")==planId && !isPurged(it)}
    }
    fun financeSeriesMembers(ruleId:String,includeDeleted:Boolean=false):List<Item> {
        require(ruleId.isNotBlank() && ruleId.length<=512)
        val active=if(includeDeleted)""else"AND i.deletedAt=0"
        return queryItems("i.id IN (SELECT id FROM finance_index WHERE type IN ('income','expense','transfer','recurrence_exception')) $active AND (instr(i.payload,?)>0 OR instr(i.payload,?)>0)",listOf(jsonMember("source",ruleId),jsonMember("recurrenceRuleId",ruleId)))
            .filter{it.value("source")==ruleId || it.value("recurrenceRuleId")==ruleId}
    }
    fun hasActiveFinanceReferences(id:String):Boolean {
        require(id.isNotBlank() && id.length<=512)
        val references=listOf("account","destination","card").map{jsonMember(it,id)}
        val sql="SELECT EXISTS(SELECT 1 FROM item_summaries i JOIN finance_index f ON f.id=i.id WHERE i.deletedAt=0 AND i.id!=? AND (${references.joinToString(" OR "){"instr(i.payload,?)>0"}}))"
        return db.openHelper.readableDatabase.query(sql,arrayOf<Any>(id,*references.toTypedArray())).use{it.moveToFirst() && it.getLong(0)!=0L}
    }
    /** Financial entities and transactions share a stable trash page; permanent tombstones stay invisible. */
    fun financeTrash(cursor:FinancePageCursor?=null,limit:Int=100):FinancePage {
        require(limit in 1..100)
        val clauses=mutableListOf("i.deletedAt>0","instr(i.payload,?)=0","NOT(i.title='Registro excluído' AND i.payload='{}')")
        val args=mutableListOf<Any>(jsonMember("purged","yes"))
        if(cursor!=null){if(cursor.date.isNotBlank())LocalDate.parse(cursor.date);clauses.add("(i.date<? OR (i.date=? AND i.id<?))");args.add(cursor.date);args.add(cursor.date);args.add(cursor.id)}
        args.add(limit+1)
        val records=db.persistence().page(SimpleSQLiteQuery("SELECT i.* FROM item_summaries i JOIN finance_index f ON f.id=i.id WHERE ${clauses.joinToString(" AND ")} ORDER BY i.date DESC,i.id DESC LIMIT ?",args.toTypedArray()))
        val page=records.take(limit).map{it.model()}
        return FinancePage(page,if(records.size>limit)page.last().let{FinancePageCursor(it.date,it.id)}else null)
    }
    private fun queryItems(where:String,args:List<Any>):List<Item> = db.persistence().page(SimpleSQLiteQuery("SELECT i.* FROM item_summaries i WHERE $where ORDER BY i.date DESC,i.id DESC",args.toTypedArray())).map{it.model()}
    fun search(query:String,limit:Int=100,offset:Int=0):List<Item> {
        require(limit in 1..100 && offset>=0)
        if(query.isBlank())return emptyList()
        val tokens=Regex("[\\p{L}\\p{N}_]+").findAll(query.take(200)).map{it.value}.take(20).toList()
        if(tokens.isEmpty())return emptyList()
        // Android's basic FTS4 syntax treats explicit AND as a search word. Spaces
        // mean intersection in both basic and enhanced FTS4; prefixes stay quoted.
        val match=tokens.joinToString(" "){"\"$it*\""}
        return db.persistence().page(SimpleSQLiteQuery("SELECT i.* FROM item_summaries i JOIN item_search ON item_search.itemId=i.id WHERE i.deletedAt=0 AND i.type!='cloud_settings' AND item_search MATCH ? ORDER BY i.date DESC,i.id DESC LIMIT ? OFFSET ?",arrayOf<Any>(match,limit,offset))).map{it.model()}
    }
    fun queryFinance(query:FinanceQuery=FinanceQuery(),cursor:FinancePageCursor?=null,limit:Int=100):FinancePage {
        require(limit in 1..100 && query.minAmountMinor>=0 && query.maxAmountMinor>=query.minAmountMinor)
        LocalDate.parse(query.from);LocalDate.parse(query.to);require(query.from<=query.to)
        val clauses=mutableListOf("f.type IN ('income','expense','transfer')","f.date>=?","f.date<=?","f.amountMinor>=?","f.amountMinor<=?",if(query.deletedOnly)"f.deletedAt>0"else"f.deletedAt=0")
        val args=mutableListOf<Any>(query.from,query.to,query.minAmountMinor,query.maxAmountMinor)
        listOf("type" to query.type,"category" to query.category,"accountId" to query.account,"cardId" to query.card,"status" to query.status,"currency" to query.currency).filter{it.second.isNotBlank()}.forEach{(column,value)->clauses.add("f.$column=?");args.add(value)}
        if(query.search.isNotBlank()){clauses.add("instr(f.searchText,?)>0");args.add(query.search.lowercase(Locale.ROOT).take(200))}
        if(query.recurringOnly)clauses.add("f.ruleId!=''")
        if(query.installmentOnly)clauses.add("f.installment=1")
        if(cursor!=null){LocalDate.parse(cursor.date);clauses.add("(f.date<? OR (f.date=? AND f.id<?))");args.add(cursor.date);args.add(cursor.date);args.add(cursor.id)}
        args.add(limit+1)
        val rows=db.persistence().page(SimpleSQLiteQuery("SELECT i.* FROM item_summaries i JOIN finance_index f ON f.id=i.id WHERE ${clauses.joinToString(" AND ")} ORDER BY f.date DESC,f.id DESC LIMIT ?",args.toTypedArray()))
        val page=rows.take(limit).map{itemWithoutBinary(it.model())}
        return FinancePage(page,if(rows.size>limit)page.last().let{FinancePageCursor(it.date,it.id)}else null)
    }
    fun pendingSync(limit:Int=100):List<PendingSyncOperation> = db.persistence().pending(limit.coerceIn(1,100)).map(::pendingOperation)
    fun pendingSyncForItem(id:String):PendingSyncOperation? = db.persistence().outbox(id)?.takeUnless{it.suspended}?.let(::pendingOperation)
    private fun pendingOperation(operation:SyncOutboxRecord):PendingSyncOperation {
        val snapshot=fromJson(JSONObject(operation.payload))
        // Binary content lives once in items. A queued operation remains a stable metadata snapshot.
        val current=find(operation.itemId)
        val item=if(current!=null && db.persistence().metadata(operation.itemId)?.localRevision==operation.localRevision) current else snapshot
        return PendingSyncOperation(operation.operationId,item,operation.baseServerRevision,operation.localRevision,operation.queuedAt)
    }
    fun pendingSyncCount():Int=db.persistence().pendingCount()
    /** Upload references are cache metadata, not a second user edit or a second upload intent. */
    fun cacheAttachmentReference(itemId:String,hash:String,path:String,size:Long) {
        require(hash.matches(Regex("[a-fA-F0-9]{64}")) && size in 1..10_000_000)
        require(uid!=null && path.startsWith("users/$uid/") && !path.contains(".."))
        db.runInTransaction {
            val item=find(itemId) ?: return@runInTransaction
            val bytes=item.value("attachment").takeIf(String::isNotBlank)?.let{android.util.Base64.decode(it,android.util.Base64.NO_WRAP)}
            val actual=bytes?.let{java.security.MessageDigest.getInstance("SHA-256").digest(it).joinToString(""){byte->"%02x".format(byte)}} ?: item.value("attachmentHash")
            if(!actual.equals(hash,true))return@runInTransaction
            val cached=item.copy(fields=item.fields+mapOf("attachmentHash" to hash.lowercase(Locale.ROOT),"cloudAttachmentSha256" to hash.lowercase(Locale.ROOT),"cloudAttachmentPath" to path,"cloudAttachmentSize" to size.toString()))
            db.workspace().save(ItemRecord.from(cached));index(cached)
            db.persistence().outbox(itemId)?.let{db.persistence().outbox(it.copy(payload=toJson(itemWithoutBinary(cached)).toString()))}
        }
    }
    fun acknowledgeSync(operationId:String,serverRevision:Long,serverUpdatedAt:Long) {
        require(serverRevision>0 && serverUpdatedAt>=0)
        db.runInTransaction {
            val operation=db.persistence().operation(operationId)
            val audit=db.persistence().auditEvent(operationId)
            val itemId=operation?.itemId ?: audit?.itemId ?: return@runInTransaction
            val meta=db.persistence().metadata(itemId) ?: return@runInTransaction
            if(serverRevision<meta.serverRevision)return@runInTransaction
            db.persistence().metadata(meta.copy(serverRevision=serverRevision,updatedAt=maxOf(meta.updatedAt,serverUpdatedAt)))
            val queued=db.persistence().outbox(itemId)
            if(queued?.operationId==operationId)db.persistence().acknowledge(operationId)
            else if(queued!=null)db.persistence().outbox(queued.copy(baseServerRevision=serverRevision))
        }
    }
    fun applyRemote(remote:RemoteItem):Boolean {
        validate(remote.item);require(remote.serverRevision>0 && remote.updatedAt>=0)
        var applied=false
        db.runInTransaction {
            val meta=db.persistence().metadata(remote.item.id)
            val queued=db.persistence().outbox(remote.item.id)
            if(queued?.operationId==remote.operationId){acknowledgeSync(remote.operationId,remote.serverRevision,remote.updatedAt);applied=true;return@runInTransaction}
            if(remote.serverRevision<=(meta?.serverRevision ?: 0)){applied=true;return@runInTransaction}
            if(queued!=null){recordConflict(queued.operationId,remote);return@runInTransaction}
            val before=find(remote.item.id)
            val incoming=preserveBinaryWhenSameAttachment(before,remote.item)
            db.workspace().save(ItemRecord.from(incoming));index(incoming)
            if(incoming.type=="cloud_settings" && incoming.deletedAt==0L)incoming.fields.filterKeys{it in SYNC_PREFERENCES}.forEach{(key,value)->preference(key,value)}
            val revision=Math.addExact(meta?.localRevision ?: 0,1)
            db.persistence().metadata(ItemMetadataRecord(incoming.id,revision,remote.serverRevision,remote.updatedAt,remote.operationId))
            db.persistence().audit(AuditEventRecord(UUID.randomUUID().toString(),incoming.id,"remote",before?.let{toJson(itemWithoutBinary(it)).toString()}.orEmpty(),toJson(itemWithoutBinary(incoming)).toString(),System.currentTimeMillis(),revision))
            applied=true
        }
        return applied
    }
    fun recordConflict(operationId:String,remote:RemoteItem) {
        validate(remote.item);require(remote.serverRevision>0)
        db.runInTransaction {
            val local=find(remote.item.id) ?: return@runInTransaction
            val queued=db.persistence().outbox(local.id) ?: return@runInTransaction
            val existing=db.persistence().conflictForItem(local.id)
            if(existing!=null && existing.serverRevision>remote.serverRevision)return@runInTransaction
            db.persistence().conflict(SyncConflictRecord("conflict:${local.id}",local.id,operationId,toJson(itemWithoutBinary(local)).toString(),toJson(itemWithoutBinary(remote.item)).toString(),remote.serverRevision,remote.updatedAt,System.currentTimeMillis()))
            db.persistence().outbox(queued.copy(suspended=true))
        }
    }
    fun conflicts():List<SyncConflict> = db.persistence().conflicts().map { record ->
        SyncConflict(record.id,record.itemId,record.operationId,fromJson(JSONObject(record.localJson)),fromJson(JSONObject(record.remoteJson)),record.serverRevision,record.serverUpdatedAt,record.createdAt)
    }
    fun resolveConflict(id:String,useRemote:Boolean) {
        db.runInTransaction {
            val conflict=db.persistence().conflict(id) ?: return@runInTransaction
            val meta=db.persistence().metadata(conflict.itemId) ?: return@runInTransaction
            db.persistence().clearConflict(conflict.itemId)
            db.persistence().removeOutbox(conflict.itemId)
            if(useRemote){
                // Keep the displaced local version in the immutable audit trail.
                val local=find(conflict.itemId)
                val remote=fromJson(JSONObject(conflict.remoteJson))
                val incoming=preserveBinaryWhenSameAttachment(local,remote)
                val revision=Math.addExact(meta.localRevision,1)
                db.workspace().save(ItemRecord.from(incoming));index(incoming)
                if(incoming.type=="cloud_settings" && incoming.deletedAt==0L)incoming.fields.filterKeys{it in SYNC_PREFERENCES}.forEach{(key,value)->preference(key,value)}
                db.persistence().metadata(ItemMetadataRecord(incoming.id,revision,conflict.serverRevision,conflict.serverUpdatedAt,conflict.operationId))
                db.persistence().audit(AuditEventRecord(UUID.randomUUID().toString(),incoming.id,"conflict_remote",local?.let{toJson(itemWithoutBinary(it)).toString()}.orEmpty(),toJson(itemWithoutBinary(incoming)).toString(),System.currentTimeMillis(),revision))
            }else{
                db.persistence().metadata(meta.copy(serverRevision=conflict.serverRevision))
                find(conflict.itemId)?.let{saveLocal(it,true)}
            }
        }
    }
    fun syncCursor(collection:String):String?=db.persistence().cursor(collection)
    fun setSyncCursor(collection:String,cursor:String){require(collection.length in 1..100 && cursor.length<=100_000);db.persistence().cursor(SyncCursorRecord(collection,cursor))}
    fun clearCacheWhenSynced() {
        require(uid!=null){"O espaço local não pode ser removido como cache de nuvem"}
        db.runInTransaction {
            require(pendingSyncCount()==0 && conflicts().isEmpty()){ "Sincronize e resolva os conflitos antes de limpar o cache" }
            listOf("items","item_summaries","item_search","finance_index","item_metadata","sync_cursors","audit_events").forEach{db.openHelper.writableDatabase.execSQL("DELETE FROM $it")}
        }
    }
    fun deleteAllData() { val now=System.currentTimeMillis();saveAll(all().filter{it.deletedAt==0L}.map{it.copy(deletedAt=now)}) }
    fun exportJson():String {
        val records=all()
        return JSONObject().put("schema",3).put("workspace",JSONObject().put("uid",uid ?: JSONObject.NULL))
            .put("items",JSONArray().apply{records.forEach{put(toJson(it))}})
            .put("preferences",JSONObject(preferences().filterKeys{it in BACKUP_PREFERENCES})).toString()
    }
    /** Merge IDs in one transaction; account identity, secrets and server revisions are never imported. */
    fun importJson(text:String) {
        require(text.toByteArray().size<=30_000_000){"Backup excede 30 MB"}
        val root=JSONObject(text);val version=root.getInt("schema")
        require(version in 1..3){"Versão de backup incompatível"}
        val array=root.getJSONArray(if(version==1) "entries" else "items");require(array.length()<=30_000)
        val items=(0 until array.length()).map { n ->
            val o=array.getJSONObject(n)
            if(version>=2)fromJson(o) else Item(o.getString("id"),o.getString("kind").lowercase(),o.getString("title"),o.getString("body"),o.getString("date"),o.getBoolean("done"),
                fields=if(o.getString("kind") in listOf("INCOME","EXPENSE"))mapOf("amount" to BigDecimal.valueOf(o.getLong("amount"),2).toPlainString())else emptyMap(),createdAt=o.getLong("created"))
        }
        require(items.map{it.id}.distinct().size==items.size){"Identificadores duplicados"}
        items.forEach(::validate)
        val prefs=if(version>=2 && root.has("preferences"))jsonMap(root.getJSONObject("preferences"))else emptyMap()
        require(prefs.all{it.key.length<=100 && it.value.length<=100_000})
        db.runInTransaction {items.forEach{saveLocal(it)};prefs.filterKeys{it in BACKUP_PREFERENCES}.forEach{(k,v)->preference(k,v)}}
    }
    private fun validate(item:Item) {
        require(item.id.isNotBlank() && item.id.length<=512 && item.title.isNotBlank() && item.title.length<=200 && item.notes.length<=100_000)
        require(item.type.matches(Regex("[a-z_]{1,40}")))
        if(item.date.isNotBlank())LocalDate.parse(item.date)
        require(item.fields.size<=100 && item.fields.all{(key,value)->key.length<=100 && value.length<=if(key=="attachment")14_000_000 else 500_000})
        if(item.type in financeTypes && item.deletedAt==0L){
            listOf("amountMinor","openingMinor","limitMinor","currentMinor","savedMinor","paidMinor","paymentMinor").forEach{key->
                val value=item.value(key)
                if(value.isNotBlank()){
                    val minor=requireNotNull(value.toLongOrNull()){ "Valor monetário inválido: $key" }
                    require(minor in -900_000_000_000_000L..900_000_000_000_000L){"Valor monetário fora do limite"}
                }
            }
            listOf("dueDate","settledDate","invoiceDueDate","firstInstallment","endDate","startDate","targetDate","occurrenceDate").forEach{key->
                if(item.value(key).isNotBlank())require(runCatching{LocalDate.parse(item.value(key))}.isSuccess){"Data financeira inválida: $key"}
            }
            if(item.value("currency").isNotBlank())require(runCatching{java.util.Currency.getInstance(item.value("currency")).defaultFractionDigits in 0..3}.getOrDefault(false)){"Moeda financeira inválida"}
        }
        if(item.type in setOf("expense","income","transfer","budget","subscription") && item.deletedAt==0L){
            val amount=item.value("amountMinor").toLongOrNull() ?: item.cents();require(amount>=0)
        }
        require(item.createdAt>=0 && item.deletedAt>=0)
    }
    companion object {
        private fun isPurged(item:Item)=item.value("purged")=="yes" || (item.deletedAt!=0L && item.title=="Registro excluído" && item.fields.isEmpty())
        private fun jsonMember(key:String,value:String)=JSONObject().put(key,value).toString().removePrefix("{").removeSuffix("}")
        private fun placeholders(count:Int)=List(count){"?"}.joinToString(",")
        private fun searchContent(item:Item)=(listOf(item.title,item.notes,item.tags,item.type,item.date)+item.fields.filterKeys{it!="attachment"}.values).joinToString(" ").take(110_000)
        private val BACKUP_PREFERENCES=setOf("name","theme","profile","home","hidden","favoriteModules","focusMinutes","breakMinutes","weatherConsent","weatherMode","weatherCity","weatherTemperature","weatherCondition","aiEndpoint","aiModel","autoUpdateCheck","autoUpdateDownload","clockZones","autoBackup","financeCurrency","financeHideValues","financeHidden","financeFirstDay","financialDay","financeNotifications","financeBudgetAlerts","financeWidgetValues","widgetFinance","financeNotificationValues","financialMigrationVersion","financeRecentIncomeCategory","financeRecentExpenseCategory","financeRecentAccount","recentIncomeCategory","recentExpenseCategory","recentFinanceAccount")
        val SYNC_PREFERENCES=BACKUP_PREFERENCES-setOf("financialMigrationVersion","autoBackup","autoUpdateCheck","autoUpdateDownload","weatherConsent","aiEndpoint","aiModel","financeWidgetValues","widgetFinance","financeNotificationValues")
        fun itemWithoutBinary(item:Item):Item = if(item.value("attachment").isBlank())item else item.copy(fields=item.fields-"attachment"+mapOf("hasAttachment" to "yes"))
        private fun preserveBinaryWhenSameAttachment(local:Item?,remote:Item):Item {
            if(local==null || local.value("attachment").isBlank() || remote.value("attachment").isNotBlank())return remote
            val remoteHash=remote.value("attachmentHash")
            return if(remoteHash.isNotBlank() && remoteHash==local.value("attachmentHash"))remote.copy(fields=remote.fields+("attachment" to local.value("attachment")))else remote
        }
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
                    val kind=c.getString(c.getColumnIndexOrThrow("kind")).lowercase();val amount=c.getLong(c.getColumnIndexOrThrow("amount"))
                    val fields=if(kind in listOf("income","expense"))JSONObject().put("amount",BigDecimal.valueOf(amount,2).toPlainString())else JSONObject()
                    db.execSQL("INSERT OR IGNORE INTO items VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",arrayOf<Any>(c.getString(0),kind,c.getString(2),c.getString(3),c.getString(5),c.getInt(6),0,"","",fields.toString(),0,c.getLong(7)))
                }}
            }
        }
        val MIGRATION_2_3=object:Migration(2,3) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS item_summaries (id TEXT NOT NULL PRIMARY KEY,type TEXT NOT NULL,title TEXT NOT NULL,notes TEXT NOT NULL,date TEXT NOT NULL,done INTEGER NOT NULL,favorite INTEGER NOT NULL,tags TEXT NOT NULL,parentId TEXT NOT NULL,payload TEXT NOT NULL,deletedAt INTEGER NOT NULL,createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_item_summaries_type_date ON item_summaries(type,date)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS item_search USING FTS4(itemId TEXT NOT NULL,content TEXT NOT NULL,tokenize=unicode61)")
                db.execSQL("CREATE TABLE IF NOT EXISTS finance_index (id TEXT NOT NULL PRIMARY KEY,type TEXT NOT NULL,date TEXT NOT NULL,bookedDate TEXT NOT NULL,dueDate TEXT NOT NULL,category TEXT NOT NULL,accountId TEXT NOT NULL,destinationId TEXT NOT NULL,cardId TEXT NOT NULL,status TEXT NOT NULL,currency TEXT NOT NULL,amountMinor INTEGER NOT NULL,cashDelta INTEGER NOT NULL,sourceDelta INTEGER NOT NULL,destinationDelta INTEGER NOT NULL,deletedAt INTEGER NOT NULL,ruleId TEXT NOT NULL,installment INTEGER NOT NULL,searchText TEXT NOT NULL,invoiceDueDate TEXT NOT NULL,invoiceId TEXT NOT NULL,cardChargeMinor INTEGER NOT NULL,cardPaidMinor INTEGER NOT NULL)")
                listOf(listOf("date","id"),listOf("type","date","id"),listOf("category","date","id"),listOf("accountId","date","id"),listOf("cardId","dueDate","id"),listOf("status","dueDate","id"),listOf("ruleId","date","id"),listOf("deletedAt","date","id")).forEach{columns->db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_index_${columns.joinToString("_")} ON finance_index(${columns.joinToString(",")})")}
                db.execSQL("CREATE TABLE IF NOT EXISTS item_metadata (id TEXT NOT NULL PRIMARY KEY,localRevision INTEGER NOT NULL,serverRevision INTEGER NOT NULL,updatedAt INTEGER NOT NULL,operationId TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (itemId TEXT NOT NULL PRIMARY KEY,operationId TEXT NOT NULL,baseServerRevision INTEGER NOT NULL,localRevision INTEGER NOT NULL,queuedAt INTEGER NOT NULL,payload TEXT NOT NULL,suspended INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_operationId ON sync_outbox(operationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_queuedAt ON sync_outbox(queuedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS audit_events (id TEXT NOT NULL PRIMARY KEY,itemId TEXT NOT NULL,action TEXT NOT NULL,beforeJson TEXT NOT NULL,afterJson TEXT NOT NULL,changedAt INTEGER NOT NULL,revision INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_itemId_changedAt ON audit_events(itemId,changedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_conflicts (id TEXT NOT NULL PRIMARY KEY,itemId TEXT NOT NULL,operationId TEXT NOT NULL,localJson TEXT NOT NULL,remoteJson TEXT NOT NULL,serverRevision INTEGER NOT NULL,serverUpdatedAt INTEGER NOT NULL,createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_conflicts_itemId ON sync_conflicts(itemId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_cursors (collection TEXT NOT NULL PRIMARY KEY,cursor TEXT NOT NULL)")
                db.query("SELECT * FROM items").use{c->while(c.moveToNext()){
                    fun string(name:String)=c.getString(c.getColumnIndexOrThrow(name))
                    fun long(name:String)=c.getLong(c.getColumnIndexOrThrow(name))
                    val item=Item(string("id"),string("type"),string("title"),string("notes"),string("date"),long("done")!=0L,long("favorite")!=0L,string("tags"),string("parentId"),jsonMap(JSONObject(string("payload"))),long("deletedAt"),long("createdAt"))
                    val summary=ItemSummaryRecord.from(item)
                    db.execSQL("INSERT INTO item_search(itemId,content) VALUES(?,?)",arrayOf(item.id,searchContent(item)))
                    db.execSQL("INSERT OR REPLACE INTO item_summaries VALUES(${placeholders(12)})",arrayOf<Any>(summary.id,summary.type,summary.title,summary.notes,summary.date,if(summary.done)1 else 0,if(summary.favorite)1 else 0,summary.tags,summary.parentId,summary.payload,summary.deletedAt,summary.createdAt))
                    db.execSQL("INSERT OR IGNORE INTO item_metadata VALUES(?,?,?,?,?)",arrayOf<Any>(item.id,1L,0L,item.createdAt,"legacy"))
                    if(item.type in financeTypes){val f=FinanceIndexRecord.from(item);db.execSQL("INSERT OR REPLACE INTO finance_index VALUES(${placeholders(23)})",arrayOf<Any>(f.id,f.type,f.date,f.bookedDate,f.dueDate,f.category,f.accountId,f.destinationId,f.cardId,f.status,f.currency,f.amountMinor,f.cashDelta,f.sourceDelta,f.destinationDelta,f.deletedAt,f.ruleId,if(f.installment)1 else 0,f.searchText,f.invoiceDueDate,f.invoiceId,f.cardChargeMinor,f.cardPaidMinor))}
                }}
            }
        }
        val MIGRATIONS=arrayOf(MIGRATION,MIGRATION_2_3)
    }
}
