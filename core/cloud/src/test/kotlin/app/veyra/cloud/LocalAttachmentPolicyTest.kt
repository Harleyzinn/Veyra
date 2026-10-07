package app.veyra.cloud

import app.veyra.model.Item
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class LocalAttachmentPolicyTest {
    @Test fun recordSyncOmitsBinaryWithoutMutatingOriginal() {
        val item = Item(type = "expense", title = "Compra", fields = mapOf("attachment" to "bG9jYWw=", "amountMinor" to "3500", "mime" to "application/pdf"))
        val remote = LocalAttachmentPolicy.forCloud(item)
        assertFalse("attachment" in remote.fields)
        assertEquals("yes", remote.value("attachmentLocalOnly"))
        assertEquals("no", remote.value("hasAttachment"))
        assertEquals("3500", remote.value("amountMinor"))
        assertEquals("bG9jYWw=", item.value("attachment"))
    }
    @Test fun recordWithoutAttachmentIsUnchanged() {
        val item = Item(type = "note", title = "Texto", notes = "Minha nota")
        assertEquals(item, LocalAttachmentPolicy.forCloud(item))
    }
    @Test fun existingStorageReferencesCannotSilentlyLoseTheirFiles() {
        assertFailsWith<IllegalArgumentException> {
            LocalAttachmentPolicy.forCloud(Item(type = "note", title = "Arquivo", fields = mapOf("cloudAttachmentPath" to "users/old/attachments/hash")))
        }
    }
}
