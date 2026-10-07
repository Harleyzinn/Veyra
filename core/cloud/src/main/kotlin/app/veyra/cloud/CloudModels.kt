package app.veyra.cloud

data class CloudUser(
    val uid: String,
    val name: String,
    val email: String,
    val photo: String,
    val verified: Boolean,
    val createdAt: Long,
    val lastSignInAt: Long,
    val passwordProvider: Boolean
)

enum class SyncStatus(val label: String) {
    LOCAL("Somente neste aparelho"), READY("Sincronizado"), SYNCING("Sincronizando"),
    OFFLINE("Offline · alterações protegidas neste aparelho"), ERROR("Erro de sincronização"),
    CONFLICT("Alterações precisam de revisão"), DISABLED("Sincronização pausada")
}

data class CloudState(
    val configured: Boolean = false,
    val cloudAttachmentsEnabled: Boolean = false,
    val user: CloudUser? = null,
    val status: SyncStatus = SyncStatus.LOCAL,
    val busy: Boolean = false,
    val message: String = "",
    val error: String? = null,
    val lastSync: Long = 0,
    val pending: Int = 0,
    val conflictCount: Int = 0,
    val localCount: Int = 0,
    val migrationAvailable: Int = 0,
    val migrationComplete: Boolean = false,
    val syncEnabled: Boolean = true
)

/** Explicit routing prevents an arbitrary Item.type from writing to an arbitrary collection. */
object CloudCollections {
    val financeTypes = setOf("income", "expense", "transfer", "invoice_payment", "bill", "receivable")
    val buckets = listOf("transactions", "accounts", "creditCards", "recurringTransactions", "budgets",
        "goals", "subscriptions", "debts", "automationRules", "categories", "templates", "assets",
        "notes", "tasks", "settings", "workspace")
    fun forType(type: String): String = when (type) {
        in financeTypes -> "transactions"
        "account" -> "accounts"
        "card" -> "creditCards"
        "recurrence", "finance_recurrence", "recurring_rule", "recurrence_exception", "installment_plan" -> "recurringTransactions"
        "budget" -> "budgets"
        "savings_goal", "finance_goal", "emergency_reserve" -> "goals"
        "subscription" -> "subscriptions"
        "debt", "loan" -> "debts"
        "finance_rule", "financial_rule", "automation" -> "automationRules"
        "finance_category", "financial_category" -> "categories"
        "finance_template", "financial_template" -> "templates"
        "investment", "asset", "financial_asset", "networth_snapshot", "month_close", "cash_carry", "card_carry" -> "assets"
        "note" -> "notes"
        "task" -> "tasks"
        "cloud_settings" -> "settings"
        else -> "workspace"
    }
}
