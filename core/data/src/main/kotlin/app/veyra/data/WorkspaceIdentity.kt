package app.veyra.data

import android.content.Context
import java.security.MessageDigest

/** Accounts have separate physical databases. Signing out never exposes another user's cache. */
object WorkspaceIdentity {
    private const val PREFERENCES = "veyra-workspace-identity"
    private const val ACTIVE_UID = "activeUid"
    private val scopeFence = Any()

    fun activeUid(context: Context): String? = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(ACTIVE_UID, null)
        ?.takeIf { it.isNotBlank() }

    fun setActiveUid(context: Context, uid: String?) = synchronized(scopeFence) {
        require(uid == null || (uid.isNotBlank() && uid.length <= 128)) { "Identificador de conta inválido" }
        val editor = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
        if (uid == null) editor.remove(ACTIVE_UID) else editor.putString(ACTIVE_UID, uid)
        check(editor.commit()) { "Não foi possível trocar o espaço de dados" }
    }

    /** Prevents a scope switch between a background action's check and its side effect. */
    fun <T> withActiveUid(context: Context, uid: String?, action: () -> T): T? = synchronized(scopeFence) {
        if (activeUid(context) == uid) action() else null
    }

    fun database(context: Context): String = databaseForUid(activeUid(context))

    fun databaseForUid(uid: String?): String {
        if (uid.isNullOrBlank()) return "veyra.db"
        val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "veyra-user-$hash.db"
    }
}
