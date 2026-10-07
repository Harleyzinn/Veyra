package app.veyra.data

import androidx.room.*
import app.veyra.model.Item
import java.util.Locale

internal val ledgerTypes = setOf("income", "expense", "transfer")
internal val financeTypes = ledgerTypes + setOf(
    "account", "card", "budget", "subscription", "installment_plan", "investment", "debt",
    "savings_goal", "bill", "receivable", "recurring_rule", "recurrence_exception", "financial_category", "financial_rule", "financial_asset",
    "financial_template", "category", "automation_rule", "financial_close", "asset", "loan", "finance_settings", "networth_snapshot", "month_close",
)

/** A compact projection prevents a list query from reading multi-megabyte receipt payloads. */
@Entity(tableName = "item_summaries", indices = [Index(value = ["type", "date"])])
data class ItemSummaryRecord(
    @PrimaryKey val id: String, val type: String, val title: String, val notes: String,
    val date: String, val done: Boolean, val favorite: Boolean, val tags: String,
    val parentId: String, val payload: String, val deletedAt: Long, val createdAt: Long,
) {
    companion object {
        fun from(item: Item): ItemSummaryRecord {
            val i = WorkspaceStore.itemWithoutBinary(item)
            return ItemSummaryRecord(i.id,i.type,i.title,i.notes,i.date,i.done,i.favorite,i.tags,i.parentId,
                org.json.JSONObject(i.fields).toString(),i.deletedAt,i.createdAt)
        }
    }
}

@Fts4(tokenizer = "unicode61")
@Entity(tableName = "item_search")
data class ItemSearchRecord(val itemId: String, val content: String)

@Entity(tableName = "finance_index", indices = [
    Index(value = ["date", "id"]), Index(value = ["type", "date", "id"]),
    Index(value = ["category", "date", "id"]), Index(value = ["accountId", "date", "id"]),
    Index(value = ["cardId", "dueDate", "id"]), Index(value = ["status", "dueDate", "id"]),
    Index(value = ["ruleId", "date", "id"]), Index(value = ["deletedAt", "date", "id"]),
])
data class FinanceIndexRecord(
    @PrimaryKey val id: String,
    val type: String,
    val date: String,
    val bookedDate: String,
    val dueDate: String,
    val category: String,
    val accountId: String,
    val destinationId: String,
    val cardId: String,
    val status: String,
    val currency: String,
    val amountMinor: Long,
    val cashDelta: Long,
    val sourceDelta: Long,
    val destinationDelta: Long,
    val deletedAt: Long,
    val ruleId: String,
    val installment: Boolean,
    val searchText: String,
    val invoiceDueDate: String,
    val invoiceId: String,
    val cardChargeMinor: Long,
    val cardPaidMinor: Long,
) {
    companion object {
        fun from(item: Item): FinanceIndexRecord {
            val amount = item.value("amountMinor").toLongOrNull() ?: item.cents()
            val rawStatus = item.value("status").ifBlank {
                if (item.value("planned") == "Sim" && !item.done) "pending"
                else if (item.type == "income") "received" else "paid"
            }.lowercase(Locale.ROOT)
            val status = when (rawStatus) {
                "recebido", "recebida" -> "received"
                "pago", "paga", "realizado", "realizada", "settled" -> if (item.type == "income") "received" else "paid"
                "pendente" -> "pending"
                "previsto", "prevista" -> "expected"
                "atrasado", "atrasada" -> "overdue"
                "cancelado", "cancelada", "canceled" -> "cancelled"
                else -> rawStatus
            }
            val cancelled = status in setOf("cancelled", "canceled", "cancelado", "cancelada")
            val settled = !cancelled && status in setOf("received", "paid", "settled", "recebido", "recebida", "pago", "paga", "realizado", "realizada")
            val cardPurchase = item.type == "expense" && item.value("card").isNotBlank() &&
                item.value("legacyCardCash") != "yes" && item.value("financialVersion") == "3" &&
                item.value("paymentType") != "card_payment"
            val cash = if (!settled || cardPurchase) 0 else when (item.type) {
                "income" -> amount
                "expense" -> Math.negateExact(amount)
                else -> 0
            }
            val source = if (settled && item.type == "transfer") Math.negateExact(amount) else cash
            val destination = if (settled && item.type == "transfer") amount else 0
            val creditCharge = item.type == "expense" && item.value("card").isNotBlank() &&
                item.value("financialVersion") == "3" && item.value("legacyCardCash") != "yes" && item.value("paymentType") != "card_payment"
            val invoiceDue = item.value("invoiceDueDate").ifBlank { item.value("dueDate").ifBlank { item.date } }
            return FinanceIndexRecord(
                item.id, item.type, item.date, item.value("settledDate").ifBlank { item.date }, item.value("dueDate").ifBlank { item.date },
                item.value("category"), item.value("account"), item.value("destination"), item.value("card"),
                status, item.value("currency").ifBlank { "BRL" }, amount, cash, source, destination,
                item.deletedAt, item.value("recurrenceRuleId").ifBlank { item.value("source") },
                item.value("installmentPlanId").isNotBlank() || item.value("installmentNumber").isNotBlank() || item.value("installmentIndex").isNotBlank() || item.value("installment").isNotBlank(),
                (listOf(item.title, item.notes, item.tags, item.date) + item.fields.filterKeys { it != "attachment" }.values)
                    .joinToString(" ").take(110_000).lowercase(Locale.ROOT),
                invoiceDue, item.value("invoiceId").ifBlank { "invoice:${item.value("card")}:$invoiceDue" },
                if (creditCharge && !cancelled) amount else 0,
                if (item.value("paymentType") == "card_payment" && settled) amount else 0,
            )
        }
    }
}

@Entity(tableName = "item_metadata")
data class ItemMetadataRecord(
    @PrimaryKey val id: String,
    val localRevision: Long,
    val serverRevision: Long,
    val updatedAt: Long,
    val operationId: String,
)

@Entity(tableName = "sync_outbox", indices = [Index(value = ["operationId"], unique = true), Index(value = ["queuedAt"])])
data class SyncOutboxRecord(
    @PrimaryKey val itemId: String,
    val operationId: String,
    val baseServerRevision: Long,
    val localRevision: Long,
    val queuedAt: Long,
    val payload: String,
    val suspended: Boolean,
)

@Entity(tableName = "audit_events", indices = [Index(value = ["itemId", "changedAt"])])
data class AuditEventRecord(
    @PrimaryKey val id: String,
    val itemId: String,
    val action: String,
    val beforeJson: String,
    val afterJson: String,
    val changedAt: Long,
    val revision: Long,
)

@Entity(tableName = "sync_conflicts", indices = [Index(value = ["itemId"], unique = true)])
data class SyncConflictRecord(
    @PrimaryKey val id: String,
    val itemId: String,
    val operationId: String,
    val localJson: String,
    val remoteJson: String,
    val serverRevision: Long,
    val serverUpdatedAt: Long,
    val createdAt: Long,
)

@Entity(tableName = "sync_cursors")
data class SyncCursorRecord(@PrimaryKey val collection: String, val cursor: String)

data class AccountCarryRecord(val currency: String, val accountId: String, val amountMinor: Long)
data class CardCarryRecord(val cardId: String, val currency: String, val dueDate: String, val amountMinor: Long, val paidMinor: Long)

@Dao
interface PersistenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun summary(record: ItemSummaryRecord)
    @Insert fun search(record: ItemSearchRecord)
    @Query("DELETE FROM item_search WHERE itemId=:id") fun removeSearch(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun index(record: FinanceIndexRecord)
    @Query("DELETE FROM finance_index WHERE id=:id") fun removeIndex(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun metadata(record: ItemMetadataRecord)
    @Query("SELECT * FROM item_metadata WHERE id=:id") fun metadata(id: String): ItemMetadataRecord?
    @Query("SELECT COUNT(*) FROM item_metadata WHERE id IN (:ids) AND serverRevision>0") fun acknowledgedCount(ids:List<String>):Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun outbox(record: SyncOutboxRecord)
    @Query("SELECT * FROM sync_outbox WHERE itemId=:id") fun outbox(id: String): SyncOutboxRecord?
    @Query("SELECT * FROM sync_outbox WHERE operationId=:operationId") fun operation(operationId: String): SyncOutboxRecord?
    @Query("SELECT * FROM sync_outbox WHERE suspended=0 ORDER BY queuedAt,itemId LIMIT :limit") fun pending(limit: Int): List<SyncOutboxRecord>
    @Query("SELECT COUNT(*) FROM sync_outbox") fun pendingCount(): Int
    @Query("DELETE FROM sync_outbox WHERE operationId=:id") fun acknowledge(id: String)
    @Query("DELETE FROM sync_outbox WHERE itemId=:id") fun removeOutbox(id: String)
    @Insert fun audit(record: AuditEventRecord)
    @Query("SELECT * FROM audit_events WHERE itemId=:id ORDER BY changedAt DESC,id DESC LIMIT :limit") fun audit(id: String, limit: Int): List<AuditEventRecord>
    @Query("SELECT * FROM audit_events WHERE id=:id") fun auditEvent(id: String): AuditEventRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun conflict(record: SyncConflictRecord)
    @Query("SELECT * FROM sync_conflicts ORDER BY createdAt DESC") fun conflicts(): List<SyncConflictRecord>
    @Query("SELECT * FROM sync_conflicts WHERE id=:id") fun conflict(id: String): SyncConflictRecord?
    @Query("SELECT * FROM sync_conflicts WHERE itemId=:id") fun conflictForItem(id: String): SyncConflictRecord?
    @Query("DELETE FROM sync_conflicts WHERE itemId=:id") fun clearConflict(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun cursor(record: SyncCursorRecord)
    @Query("SELECT cursor FROM sync_cursors WHERE collection=:collection") fun cursor(collection: String): String?
    @RawQuery fun page(query: androidx.sqlite.db.SupportSQLiteQuery): List<ItemRecord>
    @RawQuery fun cardCarry(query: androidx.sqlite.db.SupportSQLiteQuery): List<CardCarryRecord>
    @Query("SELECT currency, accountId, SUM(delta) AS amountMinor FROM (SELECT currency,accountId,sourceDelta AS delta FROM finance_index WHERE deletedAt=0 AND bookedDate<:before AND type IN ('income','expense','transfer') UNION ALL SELECT currency,destinationId AS accountId,destinationDelta AS delta FROM finance_index WHERE deletedAt=0 AND bookedDate<:before AND type='transfer') GROUP BY currency,accountId HAVING SUM(delta) != 0")
    fun carry(before: String): List<AccountCarryRecord>
    @Query("SELECT SUM(cashDelta) FROM finance_index WHERE deletedAt=0 AND bookedDate<=:through AND currency=:currency AND type IN ('income','expense')")
    fun cashNet(through: String, currency: String): Long?
    @Query("SELECT SUM(amountMinor) FROM finance_index WHERE deletedAt=0 AND date>=:from AND date<=:to AND type=:type AND currency=:currency AND status IN ('received','paid','settled','recebido','pago','realizado')")
    fun sum(from: String, to: String, type: String, currency: String): Long?
}
