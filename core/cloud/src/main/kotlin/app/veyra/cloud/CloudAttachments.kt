package app.veyra.cloud

import android.util.Base64
import app.veyra.model.Item
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

internal class CloudAttachments(private val runtime: FirebaseRuntime) {
    suspend fun upload(uid: String, item: Item): Item {
        if (!BuildConfig.CLOUD_ATTACHMENTS_ENABLED) return LocalAttachmentPolicy.forCloud(item)
        val encoded = item.value("attachment")
        if (encoded.isBlank()) {
            require(item.value("hasAttachment") != "yes" || item.value("cloudAttachmentPath").isNotBlank()) {
                "O arquivo deste anexo não está disponível. Restaure o arquivo antes de sincronizar."
            }
            if (item.value("cloudAttachmentPath").isNotBlank()) {
                require(item.value("cloudAttachmentPath").matches(Regex("users/${Regex.escape(uid)}/(receipts|attachments|profile)/[a-f0-9]{64}"))) {
                    "O anexo pertence a outra conta. Exporte um backup completo na conta original antes de importar."
                }
            }
            return item
        }
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size in 1..MAX_BYTES) { "O anexo deve ter entre 1 byte e 10 MB." }
        val mime = item.value("mime").ifBlank { "application/octet-stream" }
        require(mime in ALLOWED_MIMES) { "Formato de anexo não permitido para sincronização." }
        val hash = sha256(bytes)
        val folder = if (item.type in CloudCollections.financeTypes) "receipts" else "attachments"
        val path = "users/$uid/$folder/$hash"
        val reference = runtime.storage.reference.child(path)
        val metadata = StorageMetadata.Builder().setContentType(mime)
            .setCustomMetadata("ownerUid", uid).setCustomMetadata("sha256", hash).build()
        // Hash paths make retries idempotent, including a retry after process death.
        val stored = runCatching { reference.metadata.await() }.getOrNull()
        if (stored?.getCustomMetadata("sha256") != hash || stored.sizeBytes != bytes.size.toLong())
            reference.putBytes(bytes, metadata).await()
        return item.copy(fields = (item.fields - "attachment") + mapOf(
            "cloudAttachmentPath" to path, "cloudAttachmentSha256" to hash,
            "cloudAttachmentSize" to bytes.size.toString(), "attachmentHash" to hash, "mime" to mime))
    }

    suspend fun download(uid: String, item: Item): ByteArray {
        val path = item.value("cloudAttachmentPath")
        require(path.matches(Regex("users/${Regex.escape(uid)}/(receipts|attachments|profile)/[a-f0-9]{64}"))) {
            "O anexo pertence a outro espaço de dados."
        }
        val bytes = runtime.storage.reference.child(path).getBytes(MAX_BYTES.toLong()).await()
        check(sha256(bytes) == item.value("cloudAttachmentSha256")) { "O anexo não passou na conferência de integridade." }
        return bytes
    }

    suspend fun deleteAll(uid: String) {
        if (!BuildConfig.CLOUD_ATTACHMENTS_ENABLED) return
        suspend fun erase(path: String) {
            var token: String? = null
            do {
                val page = if (token == null) runtime.storage.reference.child(path).list(100).await()
                    else runtime.storage.reference.child(path).list(100, token!!).await()
                page.items.forEach { it.delete().await() }
                // Only the three controlled folders are used; never recursively delete arbitrary prefixes.
                check(page.prefixes.isEmpty()) { "Há uma pasta desconhecida no Storage; revise no Console antes de excluir a conta." }
                token = page.pageToken
            } while (token != null)
        }
        listOf("profile", "receipts", "attachments").forEach { erase("users/$uid/$it") }
    }

    companion object {
        const val MAX_BYTES = 10_000_000
        val ALLOWED_MIMES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain", "application/octet-stream")
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
