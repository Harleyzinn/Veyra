package app.veyra.cloud

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

internal class CloudAuthentication(private val runtime: FirebaseRuntime) {
    suspend fun register(name: String, email: String, password: String) {
        require(name.trim().length in 1..100) { "Informe seu nome." }
        require(password.length >= 8) { "Use uma senha de pelo menos 8 caracteres." }
        val user = runtime.auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("O cadastro não retornou uma conta.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        user.sendEmailVerification().await()
    }

    suspend fun signIn(email: String, password: String) {
        runtime.auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun google(activity: Activity, reauthenticate: Boolean = false) {
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        check(id != 0) { "Ative Google no Firebase e baixe novamente google-services.json." }
        val option = GetSignInWithGoogleOption.Builder(activity.getString(id)).build()
        val response = CredentialManager.create(activity).getCredential(activity,
            GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "O Google não retornou uma credencial compatível."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebase = GoogleAuthProvider.getCredential(google.idToken, null)
        if (reauthenticate) user().reauthenticate(firebase).await()
        else runtime.auth.signInWithCredential(firebase).await()
    }

    suspend fun signOut(activity: Activity?) {
        runtime.auth.signOut()
        activity?.let { CredentialManager.create(it).clearCredentialState(ClearCredentialStateRequest()) }
    }

    suspend fun reset(email: String) { runtime.auth.sendPasswordResetEmail(email.trim()).await() }
    suspend fun verify() { user().sendEmailVerification().await() }
    suspend fun refresh() { user().reload().await(); user().getIdToken(true).await() }
    suspend fun reauthenticate(password: String) {
        val user = user()
        val email = user.email ?: error("A conta não possui e-mail.")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
    }
    suspend fun changePassword(password: String) {
        require(password.length >= 8) { "Use uma senha de pelo menos 8 caracteres." }
        user().updatePassword(password).await()
    }
    suspend fun changeEmail(email: String) { user().verifyBeforeUpdateEmail(email.trim()).await() }
    suspend fun rename(name: String) {
        require(name.trim().length in 1..100) { "Informe seu nome." }
        user().updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
    }
    fun user() = runtime.auth.currentUser ?: error("Entre em sua conta primeiro.")
}
