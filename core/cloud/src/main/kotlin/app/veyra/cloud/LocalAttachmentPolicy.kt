package app.veyra.cloud

import app.veyra.model.Item

/** Spark synchronizes records while binary attachments remain on their original device. */
internal object LocalAttachmentPolicy {
    fun forCloud(item: Item): Item {
        require(item.value("cloudAttachmentPath").isBlank()) {
            "Este registro contém um arquivo de uma nuvem com Storage. Exporte um backup completo antes de usar o modo gratuito."
        }
        if (item.value("attachment").isBlank() && item.value("hasAttachment") != "yes") return item
        return item.copy(fields = (item.fields - setOf("attachment", "attachmentHash")) +
            mapOf("hasAttachment" to "no", "attachmentLocalOnly" to "yes"))
    }
}
