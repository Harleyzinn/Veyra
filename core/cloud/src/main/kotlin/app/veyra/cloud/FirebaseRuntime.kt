package app.veyra.cloud

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.storage.FirebaseStorage

/** No invented project, anonymous session, or fallback remote credentials. */
internal class FirebaseRuntime private constructor(val app: FirebaseApp) {
    init {
        // Install attestation before constructing SDKs that may refresh existing credentials.
        val factory: AppCheckProviderFactory = if (BuildConfig.DEBUG && BuildConfig.APP_CHECK_DEBUG) {
            @Suppress("UNCHECKED_CAST")
            Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                .getMethod("getInstance").invoke(null) as AppCheckProviderFactory
        } else PlayIntegrityAppCheckProviderFactory.getInstance()
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(factory)
    }
    val auth = FirebaseAuth.getInstance(app)
    val firestore = FirebaseFirestore.getInstance(app).apply {
        // Room is the durable, UID-isolated offline store and outbox. Avoid a second shared cache.
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    }
    // Auth/Firestore can still run if the owner has not provisioned a Storage bucket yet.
    val storage by lazy { FirebaseStorage.getInstance(app) }

    init { auth.setLanguageCode("pt-BR") }

    companion object {
        @Volatile private var instance: FirebaseRuntime? = null
        @Synchronized fun available(context: Context): FirebaseRuntime? {
            if (!BuildConfig.FIREBASE_CONFIGURED) return null
            instance?.let { return it }
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(context) ?: return null
            return FirebaseRuntime(app).also { instance = it }
        }
    }
}
