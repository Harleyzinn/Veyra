package app.veyra.cloud

import app.veyra.data.RemoteItem
import app.veyra.model.Item
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate

internal object CloudCodec {
    const val MAX_MINOR = CloudMoney.MAX_MINOR
    fun documentId(id: String): String = MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun encode(uid: String, item: Item, revision: Long, operationId: String): Map<String, Any> {
        require(item.id.length in 1..512 && item.title.length in 1..200)
        require(item.fields.size <= 100 && item.fields.keys.all { it.length in 1..100 })
        require(item.notes.length <= 100_000 && item.tags.length <= 10_000)
        require(item.fields.values.all { it.length <= 100_000 }) { "Um campo excede o limite de sincronização." }
        require(item.value("attachment").isBlank()) { "O anexo deve ser enviado ao Storage antes do documento." }
        if (item.date.isNotBlank()) LocalDate.parse(item.date)
        val amount = CloudMoney.amount(item)
        require(item.type !in CloudCollections.financeTypes || amount >= 0)
        val fields = if (CloudMoney.financial(item) && ("amountMinor" in item.fields || item.value("amount").isNotBlank()))
            item.fields + ("amountMinor" to amount.toString()) else item.fields
        require(fields.size <= 100)
        val map = mapOf("id" to item.id, "ownerUid" to uid, "type" to item.type,
            "title" to item.title, "notes" to item.notes, "date" to item.date,
            "done" to item.done, "favorite" to item.favorite, "tags" to item.tags,
            "parentId" to item.parentId, "fields" to fields,
            "amountMinor" to amount, "currency" to item.value("currency").ifBlank { "BRL" },
            "category" to item.value("category"), "accountId" to item.value("account"),
            "accountDocId" to item.value("account").takeIf(String::isNotBlank)?.let(::documentId).orEmpty(),
            "destinationId" to item.value("destination"), "destinationDocId" to item.value("destination").takeIf(String::isNotBlank)?.let(::documentId).orEmpty(),
            "cardId" to item.value("card"), "cardDocId" to item.value("card").takeIf(String::isNotBlank)?.let(::documentId).orEmpty(),
            "status" to if (item.type in CloudCollections.financeTypes) item.value("status") else "",
            "recurrenceId" to item.value("recurrenceRuleId").ifBlank { item.value("recurrenceId") }.ifBlank { item.value("source") },
            "createdAt" to item.createdAt, "deletedAt" to item.deletedAt,
            "revision" to revision, "operationId" to operationId,
            "updatedAt" to FieldValue.serverTimestamp())
        require(JSONObject(map - "updatedAt").toString().toByteArray(Charsets.UTF_8).size <= 600_000) {
            "Registro muito grande para sincronização. Exporte e divida o conteúdo antes de enviar."
        }
        return map
    }

    fun decode(snapshot: DocumentSnapshot, uid: String): RemoteItem {
        val data = snapshot.data ?: error("Documento vazio no Firestore.")
        check(data["ownerUid"] == uid) { "Documento de outro usuário foi rejeitado." }
        @Suppress("UNCHECKED_CAST") val raw = data["fields"] as? Map<String, Any?> ?: error("Campos remotos inválidos.")
        check(raw.all { it.value is String }) { "Tipos remotos inválidos." }
        val item = Item(id = data["id"] as? String ?: error("ID remoto inválido."),
            type = data["type"] as? String ?: error("Tipo remoto inválido."),
            title = data["title"] as? String ?: error("Descrição remota inválida."),
            notes = data["notes"] as? String ?: "", date = data["date"] as? String ?: "",
            done = data["done"] as? Boolean ?: false, favorite = data["favorite"] as? Boolean ?: false,
            tags = data["tags"] as? String ?: "", parentId = data["parentId"] as? String ?: "",
            fields = raw.mapValues { it.value as String },
            deletedAt = (data["deletedAt"] as? Number)?.toLong() ?: 0,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0)
        check(documentId(item.id) == snapshot.id) { "ID remoto não corresponde ao documento." }
        val revision = (data["revision"] as? Number)?.toLong() ?: error("Revisão remota ausente.")
        check(revision >= 1)
        return RemoteItem(item, revision, (data["updatedAt"] as? Timestamp)?.toDate()?.time ?: 0,
            data["operationId"] as? String ?: error("Operação remota ausente."))
    }
}
