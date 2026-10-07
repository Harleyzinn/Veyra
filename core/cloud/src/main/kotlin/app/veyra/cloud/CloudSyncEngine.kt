package app.veyra.cloud

import app.veyra.data.PendingSyncOperation
import app.veyra.data.RemoteItem
import app.veyra.data.WorkspaceStore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date

internal sealed interface PushResult {
    data class Accepted(val revision: Long, val updatedAt: Long, val attachmentFields: Map<String, String> = emptyMap()) : PushResult
    data class Conflict(val remote: RemoteItem) : PushResult
}

/** Room queues durable offline operations. Server transactions enforce optimistic revisions. */
internal class CloudSyncEngine(private val runtime: FirebaseRuntime) {
    private val db get() = runtime.firestore
    private val attachments = CloudAttachments(runtime)

    fun requireUid(uid: String) {
        check(runtime.auth.currentUser?.uid == uid) { "A conta mudou. A operação foi interrompida." }
    }

    suspend fun ensureProfile(uid: String) {
        requireUid(uid)
        val user = runtime.auth.currentUser!!
        val ref = db.collection("users").document(uid)
        val existing = ref.get(Source.SERVER).await()
        check(existing.getBoolean("deleting") != true) { "A exclusão desta conta está em andamento. Conclua a exclusão em Privacidade e Dados." }
        val values = mapOf("ownerUid" to uid, "name" to user.displayName.orEmpty(),
            "email" to user.email.orEmpty(), "photo" to user.photoUrl?.toString().orEmpty(),
            "emailVerified" to user.isEmailVerified, "lastAccessAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp())
        if (existing.exists()) {
            val access = existing.getTimestamp("lastAccessAt")?.toDate()?.time ?: 0
            if (System.currentTimeMillis() - access >= 86_400_000 || existing.getString("name") != user.displayName.orEmpty() ||
                existing.getString("email") != user.email.orEmpty() || existing.getBoolean("emailVerified") != user.isEmailVerified)
                ref.update(values).await()
        }
        else ref.set(values + mapOf("createdAt" to FieldValue.serverTimestamp(),
            "latestChangeAt" to FieldValue.serverTimestamp(), "deleting" to false)).await()
    }

    suspend fun marker(uid: String): String {
        requireUid(uid)
        val profile = db.collection("users").document(uid).get(Source.SERVER).await()
        check(profile.getBoolean("deleting") != true) { "Conclua a exclusão desta conta em Privacidade e Dados." }
        val value = profile.getTimestamp("latestChangeAt") ?: return ""
        return "${value.seconds}:${value.nanoseconds}"
    }

    suspend fun push(uid: String, operation: PendingSyncOperation): PushResult {
        requireUid(uid)
        val item = attachments.upload(uid, operation.item)
        requireUid(uid)
        val root = db.collection("users").document(uid)
        val ref = root.collection(CloudCollections.forType(item.type)).document(CloudCodec.documentId(item.id))
        val result = db.runTransaction { transaction ->
            val profile = transaction.get(root)
            check(profile.exists() && profile.getBoolean("deleting") != true) { "Conta indisponível para sincronização." }
            val snapshot = transaction.get(ref)
            if (snapshot.exists() && snapshot.getString("operationId") == operation.operationId) {
                return@runTransaction PushResult.Accepted(snapshot.getLong("revision")!!,
                    snapshot.getTimestamp("updatedAt")?.toDate()?.time ?: 0, CloudCodec.decode(snapshot, uid).item.fields)
            }
            val currentRevision = snapshot.getLong("revision") ?: 0
            if (currentRevision != operation.baseServerRevision) {
                check(snapshot.exists()) { "O registro foi excluído definitivamente na nuvem. Exporte a versão local antes de decidir restaurar." }
                return@runTransaction PushResult.Conflict(CloudCodec.decode(snapshot, uid))
            }
            val revision = Math.addExact(currentRevision, 1)
            transaction.set(ref, CloudCodec.encode(uid, item, revision, operation.operationId))
            transaction.update(root, "latestChangeAt", FieldValue.serverTimestamp(), "updatedAt", FieldValue.serverTimestamp())
            PushResult.Accepted(revision, 0, item.fields)
        }.await()
        requireUid(uid)
        if (result is PushResult.Accepted && result.updatedAt == 0L) {
            val confirmed = ref.get(Source.SERVER).await()
            // A second device may have edited immediately after our commit. Our own revision still
            // acknowledged successfully; pull will compare the subsequent revision independently.
            return result.copy(updatedAt = confirmed.getTimestamp("updatedAt")?.toDate()?.time ?: 0)
        }
        return result
    }

    suspend fun pushWithDependencies(uid: String, store: WorkspaceStore, operation: PendingSyncOperation, visited: MutableSet<String> = mutableSetOf()) {
        if (!visited.add(operation.item.id)) return
        // Coalescing may have replaced an operation after the page was read. Leave the latest
        // intent queued rather than uploading a displaced snapshot.
        if (store.pendingSyncForItem(operation.item.id)?.operationId != operation.operationId) return
        for (key in listOf("account", "destination", "card")) {
            val id = operation.item.value(key)
            if (id.isNotBlank()) store.pendingSyncForItem(id)?.let { prerequisite ->
                check(prerequisite.item.type in setOf("account", "card")) { "Referência financeira inválida." }
                pushWithDependencies(uid, store, prerequisite, visited)
            }
        }
        when (val result = push(uid, operation)) {
            is PushResult.Accepted -> {
                store.acknowledgeSync(operation.operationId, result.revision, result.updatedAt)
                CloudController.cacheAttachment(store, operation.item.id, result)
            }
            is PushResult.Conflict -> store.recordConflict(operation.operationId, result.remote)
        }
    }

    /** Watermarks retain full timestamp nanoseconds and document ID to avoid equal-time losses. */
    suspend fun pull(uid: String, store: WorkspaceStore, onChanged: () -> Unit): Boolean {
        requireUid(uid)
        var remaining = false
        for (bucket in CloudCollections.buckets) {
            var cursor = store.syncCursor(bucket).orEmpty()
            var pages = 0
            while (pages++ < MAX_PAGES_PER_RUN) {
                requireUid(uid)
                var query = db.collection("users").document(uid).collection(bucket)
                    .orderBy("updatedAt").orderBy(FieldPath.documentId()).limit(PAGE_SIZE)
                if (cursor.isNotBlank()) {
                    val saved = JSONObject(cursor)
                    query = query.startAfter(Timestamp(saved.getLong("seconds"), saved.getInt("nanoseconds")), saved.getString("id"))
                }
                val page = query.get(Source.SERVER).await()
                requireUid(uid)
                page.documents.forEach { document ->
                    val remote = CloudCodec.decode(document, uid)
                    check(CloudCollections.forType(remote.item.type) == bucket) { "Coleção incompatível com o registro remoto." }
                    store.applyRemote(remote)
                    val timestamp = document.getTimestamp("updatedAt") ?: error("Data de sincronização ausente.")
                    cursor = JSONObject().put("seconds", timestamp.seconds).put("nanoseconds", timestamp.nanoseconds)
                        .put("id", document.id).toString()
                    // Apply first, then advance the durable cursor. A crash can retry safely.
                    store.setSyncCursor(bucket, cursor)
                }
                if (page.documents.isNotEmpty()) onChanged()
                if (page.size() < PAGE_SIZE.toInt()) break
                if (pages == MAX_PAGES_PER_RUN) remaining = true
            }
        }
        return remaining
    }

    suspend fun deleteAccountData(uid: String) {
        requireUid(uid)
        val root = db.collection("users").document(uid)
        // Permanent lock makes deletion resumable and prevents another signed-in device uploading
        // new records while collection batches and Storage objects are being removed.
        root.update("deleting", true, "updatedAt", FieldValue.serverTimestamp()).await()
        attachments.deleteAll(uid)
        for (bucket in CloudCollections.buckets) {
            while (true) {
                requireUid(uid)
                val page = root.collection(bucket).limit(200).get(Source.SERVER).await()
                if (page.isEmpty) break
                val batch = db.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        // Keep only an ownership tombstone: a still-valid old ID token cannot recreate the profile
        // after Firebase Auth deletion. Configure expiresAt TTL for tombstones in the console.
        root.set(mapOf("ownerUid" to uid, "deleting" to true,
            "updatedAt" to FieldValue.serverTimestamp(), "expiresAt" to Timestamp(Date(System.currentTimeMillis() + 86_400_000)))).await()
    }

    companion object {
        const val PAGE_SIZE = 100L
        const val MAX_PAGES_PER_RUN = 3
    }
}
