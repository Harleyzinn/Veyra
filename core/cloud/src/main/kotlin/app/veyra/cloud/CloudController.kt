package app.veyra.cloud

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Base64
import androidx.work.*
import app.veyra.data.*
import app.veyra.model.Item
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The activity ViewModel owns this controller and closes it from onCleared. */
class CloudController(
    context: Context,
    private val onScopeChanged: () -> Job? = { null },
    private val onDataChanged: () -> Unit = {},
) : AutoCloseable {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runtime = FirebaseRuntime.available(this.context)
    private val auth = runtime?.let(::CloudAuthentication)
    private val syncMutex = Mutex()
    private val accountMutex = Mutex()
    private var debounce: Job? = null
    private var scopeChangeJob: Job? = null
    private val mutableState = MutableStateFlow(CloudState(configured = runtime != null, cloudAttachmentsEnabled = BuildConfig.CLOUD_ATTACHMENTS_ENABLED))
    val state = mutableState.asStateFlow()
    private val connectivity = this.context.getSystemService(ConnectivityManager::class.java)
    private val networkListener = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { scope.launch { onLocalChanged() } }
        override fun onLost(network: Network) { scope.launch {
            if (!online() && state.value.user != null) mutableState.value = state.value.copy(status = SyncStatus.OFFLINE)
        } }
    }
    private val listener = FirebaseAuth.AuthStateListener { reconcileAccount() }

    init {
        runtime?.auth?.addAuthStateListener(listener)
        if (runtime == null) reconcileAccount()
        runCatching { connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkListener) }
    }

    private fun reconcileAccount(): Job = scope.launch {
        accountMutex.withLock {
            val user = runtime?.auth?.currentUser
            val uid = user?.uid
            if (WorkspaceIdentity.activeUid(context) != uid) {
                debounce?.cancel()
                WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
                if (user != null) withContext(Dispatchers.IO) {
                    WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { store ->
                        if (store.preferences()["name"].isNullOrBlank()) store.preference("name",
                            user.displayName.orEmpty().ifBlank { user.email.orEmpty().substringBefore('@').ifBlank { "Minha conta" } })
                    }
                }
                // Auth may change while the profile name is being read. Discard that old callback.
                if (runtime?.auth?.currentUser?.uid != uid) return@withLock
                WorkspaceIdentity.setActiveUid(context, uid)
                // UI callback clears the prior snapshot and reopens its store for this physical DB.
                scopeChangeJob = onScopeChanged()
            }
            if (runtime?.auth?.currentUser?.uid != uid) return@withLock
            mutableState.value = CloudState(configured = runtime != null, cloudAttachmentsEnabled = BuildConfig.CLOUD_ATTACHMENTS_ENABLED, busy = state.value.busy, user = user?.let {
                CloudUser(it.uid, it.displayName.orEmpty(), it.email.orEmpty(), it.photoUrl?.toString().orEmpty(),
                    it.isEmailVerified, it.metadata?.creationTimestamp ?: 0, it.metadata?.lastSignInTimestamp ?: 0,
                    it.providerData.any { provider -> provider.providerId == "password" })
            }, status = if (uid == null) SyncStatus.LOCAL else if (online()) SyncStatus.SYNCING else SyncStatus.OFFLINE)
            refreshLocalState()
            if (uid != null && runtime?.auth?.currentUser?.uid == uid) { schedule(); onLocalChanged() }
        }
    }

    private suspend fun refreshLocalState() {
        val uid = state.value.user?.uid
        val snapshot = withContext(Dispatchers.IO) {
            WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { store ->
                val prefs = store.preferences()
                val guestCount = if (uid == null) 0 else WorkspaceStore(context, "veyra.db").use { guest -> guest.visibleCount() }
                state.value.copy(lastSync = prefs["cloudLastSync"]?.toLongOrNull() ?: 0,
                    pending = if (uid == null) 0 else store.pendingSyncCount(), conflictCount = if (uid == null) 0 else store.conflicts().size,
                    localCount = store.visibleCount(), migrationAvailable = guestCount,
                    migrationComplete = prefs["cloudMigrationComplete"] == "yes", syncEnabled = prefs["cloudSyncEnabled"] != "no")
            }
        }
        if (state.value.user?.uid == uid) mutableState.value = snapshot
    }

    /** Called after any successful local write. Room commits first; network work is debounced. */
    fun onLocalChanged() {
        if (runtime == null || state.value.user == null) return
        debounce?.cancel()
        debounce = scope.launch { delay(1_500); syncNow() }
        schedule()
    }

    private fun schedule() {
        val uid = state.value.user?.uid ?: return
        val work = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(WORK_TAG).setInputData(workDataOf("uid" to uid)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("veyra-cloud-${CloudCodec.documentId(uid)}", ExistingWorkPolicy.KEEP, work)
        val periodic = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_TAG).setInputData(workDataOf("uid" to uid)).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("veyra-cloud-periodic-${CloudCodec.documentId(uid)}", ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    suspend fun syncNow(): Unit = syncMutex.withLock {
        val live = runtime ?: return@withLock
        val uid = state.value.user?.uid ?: return@withLock
        if (!state.value.syncEnabled) { mutableState.value = state.value.copy(status = SyncStatus.DISABLED); return@withLock }
        if (live.auth.currentUser?.isEmailVerified != true) {
            mutableState.value = state.value.copy(status = SyncStatus.DISABLED, message = "Confirme seu e-mail e toque em Já verifiquei para iniciar a sincronização.")
            return@withLock
        }
        if (!online()) { mutableState.value = state.value.copy(status = SyncStatus.OFFLINE); refreshLocalState(); return@withLock }
        mutableState.value = state.value.copy(status = SyncStatus.SYNCING, error = null)
        try {
            var changed = false
            val remaining = withContext(Dispatchers.IO) { CloudSyncLocks.forUid(uid).withLock {
                WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { store ->
                    val engine = CloudSyncEngine(live)
                    engine.ensureProfile(uid)
                    if (store.preferences()["cloudBootstrapped"] != "yes") {
                        val initialMore = engine.pull(uid, store) { changed = true }
                        if (!initialMore) store.preference("cloudBootstrapped", "yes")
                    }
                    captureSettings(store)
                    store.pendingSync(100).forEach { operation ->
                        engine.pushWithDependencies(uid, store, operation)
                    }
                    val marker = engine.marker(uid)
                    val more = if (marker.isBlank() || marker != store.preferences()["cloudRemoteMarker"])
                        engine.pull(uid, store) { changed = true } else false
                    if (!more) store.preference("cloudRemoteMarker", marker)
                    if (store.pendingSyncCount() == 0 && store.conflicts().isEmpty() && !more) {
                        val now = System.currentTimeMillis()
                        store.preference("cloudLastSync", now.toString())
                        finishMigrationIfAcknowledged(store)
                    }
                    more || store.pendingSync(1).isNotEmpty()
                }
            } }
            if (state.value.user?.uid != uid) return@withLock
            refreshLocalState()
            mutableState.value = state.value.copy(status = if (state.value.conflictCount > 0) SyncStatus.CONFLICT else if (remaining) SyncStatus.SYNCING else SyncStatus.READY,
                message = if (remaining) "Carregando mais alterações em lotes…" else state.value.message)
            if (changed) onDataChanged()
            if (remaining) { schedule(); debounce = scope.launch { delay(2_000); syncNow() } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) {
            if (state.value.user?.uid == uid) {
                refreshLocalState()
                mutableState.value = state.value.copy(status = if (state.value.conflictCount > 0) SyncStatus.CONFLICT else if (online()) SyncStatus.ERROR else SyncStatus.OFFLINE, error = friendly(exception))
            }
        }
    }

    private fun captureSettings(store: WorkspaceStore) {
        val preferences = store.syncablePreferences()
        if (preferences.isNotEmpty()) store.save(Item(id = "workspace-settings", type = "cloud_settings", title = "Preferências da conta",
            date = "", fields = preferences, createdAt = store.find("workspace-settings")?.createdAt ?: System.currentTimeMillis()))
    }

    private fun finishMigrationIfAcknowledged(store: WorkspaceStore) {
        val prefs = store.preferences()
        if (prefs["cloudMigrationState"] != "pending") return
        val ids = WorkspaceStore(context, "veyra.db").use { it.ids() }.sorted()
        val hash = CloudAttachments.sha256(ids.joinToString("\n").toByteArray(Charsets.UTF_8))
        if (ids.isNotEmpty() && hash == prefs["cloudMigrationSourceIdsHash"] && store.acknowledgedCount(ids) == ids.size) {
            store.preference("cloudMigrationComplete", "yes")
            store.preference("cloudMigrationState", "complete")
        }
    }

    fun launch(action: suspend CloudController.() -> Unit) { scope.launch { perform { action() } } }
    private suspend fun perform(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null, message = "")
        try { action(); refreshLocalState() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) { mutableState.value = state.value.copy(error = friendly(exception)) }
        finally { mutableState.value = state.value.copy(busy = false) }
    }

    suspend fun signIn(email: String, password: String) { requireAuth().signIn(email, password) }
    suspend fun register(name: String, email: String, password: String) {
        requireAuth().register(name, email, password)
        val uid = requireAuth().user().uid
        withContext(Dispatchers.IO) { WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.preference("name", name.trim()) } }
        reconcileAccount(); onDataChanged(); notice("Cadastro criado. Confira o e-mail de verificação.")
    }
    suspend fun google(activity: Activity) { requireAuth().google(activity) }
    suspend fun signOut(activity: Activity?) { requireAuth().signOut(activity); reconcileAccount() }
    suspend fun resetPassword(email: String) { requireAuth().reset(email); notice("Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.") }
    suspend fun sendVerification() { requireAuth().verify(); notice("E-mail de verificação enviado.") }
    suspend fun reloadUser() { requireAuth().refresh(); reconcileAccount() }
    suspend fun rename(name: String) {
        val uid = requireAuth().user().uid
        requireAuth().rename(name)
        withContext(Dispatchers.IO) {
            checkScope(uid)
            WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.preference("name", name.trim()) }
        }
        checkScope(uid); reconcileAccount(); onDataChanged(); onLocalChanged(); notice("Nome atualizado.")
    }
    suspend fun changePassword(current: String, replacement: String) {
        requireAuth().reauthenticate(current); requireAuth().changePassword(replacement); notice("Senha atualizada.")
    }
    suspend fun changeEmail(activity: Activity, current: String, email: String) {
        if (state.value.user?.passwordProvider == true) requireAuth().reauthenticate(current) else requireAuth().google(activity, true)
        requireAuth().changeEmail(email); notice("Confirme o link enviado ao novo e-mail para concluir a alteração.")
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        val uid = state.value.user?.uid
        withContext(Dispatchers.IO) { checkScope(uid); WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.preference("cloudSyncEnabled", if (enabled) "yes" else "no") } }
        checkScope(uid)
        mutableState.value = state.value.copy(syncEnabled = enabled, status = if (enabled) SyncStatus.SYNCING else SyncStatus.DISABLED)
        if (enabled) { schedule(); syncNow() } else WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    suspend fun importGuest() {
        val uid = state.value.user?.uid ?: error("Entre em uma conta para importar o espaço local.")
        syncNow()
        checkScope(uid)
        check(state.value.status == SyncStatus.READY && state.value.lastSync > 0) { "Conclua a primeira sincronização e resolva os conflitos antes de importar o espaço local." }
        withContext(Dispatchers.IO) {
            val legacy = WorkspaceStore(context, "veyra.db").use { it.all().filter { item -> item.type != "cloud_settings" } }
            checkScope(uid)
            WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { store ->
                // Existing IDs are compared after the first cloud pull; never silently replace a
                // different record that already belongs to this account.
                val accepted = legacy.filter { item -> store.find(item.id).let { existing -> existing == null || existing == item } }
                check(accepted.size == legacy.size) { "Há registros de mesmo ID com conteúdo diferente. Exporte os dois espaços e resolva antes de importar." }
                store.saveAll(accepted)
                store.preference("cloudMigrationSourceIdsHash", CloudAttachments.sha256(accepted.map { it.id }.sorted().joinToString("\n").toByteArray(Charsets.UTF_8)))
                store.preference("cloudMigrationCount", accepted.size.toString())
                store.preference("cloudMigrationState", "pending")
                store.preference("cloudMigrationComplete", "no")
            }
        }
        checkScope(uid); onDataChanged(); notice("Dados locais preservados. A importação será concluída após confirmação da nuvem."); onLocalChanged()
    }

    suspend fun conflicts(): List<SyncConflict> {
        val uid = state.value.user?.uid
        val result = withContext(Dispatchers.IO) { checkScope(uid); WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.conflicts() } }
        checkScope(uid); return result
    }
    suspend fun resolveConflict(id: String, useRemote: Boolean) {
        val uid = state.value.user?.uid
        withContext(Dispatchers.IO) { checkScope(uid); WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.resolveConflict(id, useRemote) } }
        checkScope(uid); onDataChanged(); onLocalChanged(); notice("Versão escolhida. A versão substituída continua no histórico local.")
    }
    suspend fun clearCache() {
        val uid = state.value.user?.uid ?: error("O espaço local não pode ser removido como cache de nuvem.")
        syncMutex.withLock { CloudSyncLocks.forUid(uid).withLock { withContext(Dispatchers.IO) { checkScope(uid); WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.clearCacheWhenSynced(); it.preference("cloudRemoteMarker", ""); it.preference("cloudBootstrapped", "no") } } } }
        checkScope(uid); onDataChanged(); notice("Cache local limpo. Seus dados continuam na nuvem."); onLocalChanged()
    }
    suspend fun trashAllData() {
        val uid = state.value.user?.uid
        withContext(Dispatchers.IO) { checkScope(uid); WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.deleteAllData() } }
        checkScope(uid); onDataChanged(); onLocalChanged(); notice("Dados movidos para a lixeira. Você pode restaurá-los antes da exclusão definitiva.")
    }
    suspend fun deleteAccount(activity: Activity, password: String) = syncMutex.withLock {
        val authentication = requireAuth()
        val uid = authentication.user().uid
        if (state.value.user?.passwordProvider == true) authentication.reauthenticate(password) else authentication.google(activity, true)
        CloudSyncLocks.forUid(uid).withLock {
            withContext(Dispatchers.IO) { CloudSyncEngine(runtime!!).deleteAccountData(uid) }
            authentication.user().delete().await()
            try { authentication.signOut(activity) }
            finally {
                // Wait for the ViewModel to close the old Room handle before removing its files.
                reconcileAccount().join()
                scopeChangeJob?.join()
                withContext(Dispatchers.IO) { context.deleteDatabase(WorkspaceIdentity.databaseForUid(uid)) }
            }
        }
        notice("Conta e dados da nuvem excluídos.")
    }

    suspend fun attachmentBytes(item: Item): ByteArray = withContext(Dispatchers.IO) {
        if (item.value("attachment").isNotBlank()) Base64.decode(item.value("attachment"), Base64.NO_WRAP)
        else CloudAttachments(runtime ?: error("Configure Firebase para baixar este anexo.")).download(state.value.user?.uid ?: error("Entre na conta do anexo."), item)
    }
    suspend fun exportBackup(uri: Uri, password: String = "") {
        val uid = state.value.user?.uid
        withContext(Dispatchers.IO) {
            checkScope(uid)
            var json = WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.exportJson() }
            val root = JSONObject(json)
            state.value.user?.takeIf { it.uid == uid }?.let { user ->
                root.put("accountProfile", JSONObject().put("uid", user.uid).put("name", user.name)
                    .put("email", user.email).put("photo", user.photo).put("emailVerified", user.verified)
                    .put("createdAt", user.createdAt).put("lastSignInAt", user.lastSignInAt))
            }
            val array = root.getJSONArray("items")
            for (index in 0 until array.length()) {
                val item = WorkspaceStore.fromJson(array.getJSONObject(index))
                if (item.value("attachment").isBlank() && item.value("cloudAttachmentPath").isNotBlank()) {
                    val bytes = attachmentBytes(item)
                    val portable = item.copy(fields = (item.fields - setOf("cloudAttachmentPath", "cloudAttachmentSha256", "cloudAttachmentSize")) +
                        ("attachment" to Base64.encodeToString(bytes, Base64.NO_WRAP)))
                    array.put(index, WorkspaceStore.toJson(portable))
                }
            }
            json = root.toString()
            require(json.toByteArray().size <= 30_000_000) { "Backup excede 30 MB. Exporte anexos separadamente ou divida o histórico." }
            val result = if (password.isBlank()) json else BackupCrypto.encrypt(json, password)
            checkScope(uid)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(result) } ?: error("Arquivo indisponível.")
        }
        notice("Backup exportado com anexos disponíveis e IDs preservados.")
    }
    suspend fun importBackup(uri: Uri, password: String = "") {
        val uid = state.value.user?.uid
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = java.io.ByteArrayOutputStream(); val block = ByteArray(32_768)
                while (true) { val count = input.read(block); if (count < 0) break; check(buffer.size() + count <= 40_000_000) { "Backup muito grande." }; buffer.write(block, 0, count) }
                buffer.toByteArray()
            } ?: error("Arquivo indisponível.")
            var text = bytes.toString(Charsets.UTF_8)
            if (JSONObject(text).has("encrypted")) text = BackupCrypto.decrypt(text, password)
            checkScope(uid); WorkspaceStore(context, WorkspaceIdentity.databaseForUid(uid)).use { it.importJson(text) }
        }
        checkScope(uid); onDataChanged(); onLocalChanged(); notice("Backup validado e importado. IDs iguais foram mesclados no espaço atual.")
    }

    private fun checkScope(uid: String?) {
        check(WorkspaceIdentity.activeUid(context) == uid && runtime?.auth?.currentUser?.uid == uid) { "A conta mudou. A operação anterior foi interrompida." }
    }

    private fun requireAuth() = auth ?: error("O Firebase ainda não foi configurado neste APK. Veja docs/FIREBASE-SETUP.md.")
    private fun notice(message: String) { mutableState.value = state.value.copy(message = message, error = null) }
    private fun online(): Boolean = connectivity.activeNetwork?.let { network -> connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } == true
    override fun close() { debounce?.cancel(); runtime?.auth?.removeAuthStateListener(listener); runCatching { connectivity.unregisterNetworkCallback(networkListener) }; scope.cancel() }

    companion object {
        const val WORK_TAG = "veyra-cloud-sync"
        internal fun cacheAttachment(store: WorkspaceStore, itemId: String, result: PushResult.Accepted) {
            val fields = result.attachmentFields
            val path = fields["cloudAttachmentPath"].orEmpty()
            val hash = fields["cloudAttachmentSha256"].orEmpty()
            val size = fields["cloudAttachmentSize"]?.toLongOrNull() ?: 0
            if (path.isNotBlank() && hash.isNotBlank() && size > 0) store.cacheAttachmentReference(itemId, hash, path, size)
        }
        internal fun friendly(exception: Exception): String = when ((exception as? FirebaseAuthException)?.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "E-mail ou senha inválidos. Confira os dados ou recupere sua senha."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Esse e-mail já possui uma conta. Entre ou recupere sua senha."
            "ERROR_INVALID_EMAIL" -> "Informe um e-mail válido."
            "ERROR_WEAK_PASSWORD" -> "A senha não atende à política de segurança do Firebase."
            "ERROR_REQUIRES_RECENT_LOGIN" -> "Confirme sua identidade novamente antes de alterar ou excluir a conta."
            "ERROR_TOO_MANY_REQUESTS" -> "Muitas tentativas. Aguarde um pouco antes de tentar novamente."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Ative esse provedor de login no Console do Firebase."
            else -> exception.message?.take(500) ?: "Não foi possível concluir. Seus dados locais continuam protegidos."
        }
    }
}
