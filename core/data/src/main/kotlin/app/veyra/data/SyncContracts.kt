package app.veyra.data

import app.veyra.model.Item

data class StoredItem(val item: Item, val localRevision: Long, val serverRevision: Long, val updatedAt: Long)

/** Coalesced per item, with the original acknowledged server revision for compare-and-swap. */
data class PendingSyncOperation(
    val operationId: String,
    val item: Item,
    val baseServerRevision: Long,
    val localRevision: Long,
    val queuedAt: Long,
)

data class RemoteItem(
    val item: Item,
    val serverRevision: Long,
    val updatedAt: Long,
    val operationId: String,
)

data class SyncConflict(
    val id: String,
    val itemId: String,
    val operationId: String,
    val localItem: Item,
    val remoteItem: Item,
    val serverRevision: Long,
    val serverUpdatedAt: Long,
    val createdAt: Long,
)

data class AuditEvent(
    val id: String,
    val itemId: String,
    val action: String,
    val before: Item?,
    val after: Item?,
    val changedAt: Long,
    val revision: Long,
)

data class FinanceQuery(
    val from: String = "0001-01-01",
    val to: String = "9999-12-31",
    val type: String = "",
    val category: String = "",
    val account: String = "",
    val card: String = "",
    val status: String = "",
    val currency: String = "",
    val minAmountMinor: Long = 0,
    val maxAmountMinor: Long = Long.MAX_VALUE,
    val search: String = "",
    val recurringOnly: Boolean = false,
    val installmentOnly: Boolean = false,
    val deletedOnly: Boolean = false,
)

data class FinancePageCursor(val date: String, val id: String)
data class FinancePage(val items: List<Item>, val nextCursor: FinancePageCursor?)
