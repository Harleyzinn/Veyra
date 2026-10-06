package app.veyra.integration
import app.veyra.model.EntityRef

/** Ports only: implementations must request consent and expose availability. */
interface ReceiptOcr { suspend fun extract(document: ByteArray): ReceiptDraft }
data class ReceiptDraft(val merchant: String?, val amountCents: Long?, val rawText: String)
interface StatementImporter { fun preview(content: String): List<ReceiptDraft> }
interface AssistantProvider { val local: Boolean; suspend fun suggest(prompt: String): String }
interface ReminderScheduler { fun schedule(ref: EntityRef, atMillis: Long); fun cancel(ref: EntityRef) }
interface AutomationEngine { suspend fun evaluate(event: DomainEvent) }
data class DomainEvent(val name: String, val ref: EntityRef, val atMillis: Long)
