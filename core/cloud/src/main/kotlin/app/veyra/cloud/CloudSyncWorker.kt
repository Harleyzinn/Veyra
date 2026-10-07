package app.veyra.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.model.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal object CloudSyncLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun forUid(uid: String): Mutex = locks.getOrPut(uid) { Mutex() }
}

/** Persistent WorkManager work resumes queued writes after connectivity or process restart. */
class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val runtime = FirebaseRuntime.available(applicationContext) ?: return@withContext Result.success()
        val uid = inputData.getString("uid") ?: return@withContext Result.failure()
        if (runtime.auth.currentUser?.uid != uid || WorkspaceIdentity.activeUid(applicationContext) != uid)
            return@withContext Result.success()
        if (runtime.auth.currentUser?.isEmailVerified != true) return@withContext Result.success()
        CloudSyncLocks.forUid(uid).withLock {
            try {
                WorkspaceStore(applicationContext, WorkspaceIdentity.databaseForUid(uid)).use { store ->
                    if (store.preferences()["cloudSyncEnabled"] == "no") return@withLock Result.success()
                    val engine = CloudSyncEngine(runtime)
                    engine.ensureProfile(uid)
                    if (store.preferences()["cloudBootstrapped"] != "yes") {
                        if (!engine.pull(uid, store) {}) store.preference("cloudBootstrapped", "yes")
                    }
                    val prefs = store.syncablePreferences()
                    if (prefs.isNotEmpty()) store.save(Item(id = "workspace-settings", type = "cloud_settings", title = "Preferências da conta",
                        date = "", fields = prefs, createdAt = store.find("workspace-settings")?.createdAt ?: System.currentTimeMillis()))
                    store.pendingSync(100).forEach { operation ->
                        engine.pushWithDependencies(uid, store, operation)
                    }
                    val marker = engine.marker(uid)
                    val more = if (marker.isBlank() || marker != store.preferences()["cloudRemoteMarker"])
                        engine.pull(uid, store) {} else false
                    if (!more) store.preference("cloudRemoteMarker", marker)
                    if (more || store.pendingSync(1).isNotEmpty()) Result.retry()
                    else {
                        if (store.conflicts().isEmpty()) store.preference("cloudLastSync", System.currentTimeMillis().toString())
                        Result.success()
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (exception: Exception) {
                // Permission/configuration failures are surfaced when the app next opens; no
                // false synchronized state, no deletion of the durable outbox, no endless retries.
                val code = (exception as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
                if (code in setOf(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.INVALID_ARGUMENT)) Result.failure()
                else Result.retry()
            }
        }
    }
}
