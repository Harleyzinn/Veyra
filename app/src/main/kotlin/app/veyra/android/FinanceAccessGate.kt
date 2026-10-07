package app.veyra.android

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.veyra.data.WorkspaceIdentity
import java.util.WeakHashMap

/** MainActivity may retain its own PIN session during this trusted Android authentication UI. */
object FinanceAuthenticationUi { @Volatile var active:Boolean=false;internal set }

private class FinanceAccessSession {
    var scope by mutableStateOf("")
    var unlocked by mutableStateOf(false)
}

private object FinanceAccessSessions {
    private val sessions=WeakHashMap<FragmentActivity,FinanceAccessSession>()
    fun acquire(activity:FragmentActivity):FinanceAccessSession=sessions.getOrPut(activity){
        val session=FinanceAccessSession()
        lateinit var observer:LifecycleEventObserver
        observer=LifecycleEventObserver{owner,event->
            if(event==Lifecycle.Event.ON_STOP)session.unlocked=false
            if(event==Lifecycle.Event.ON_DESTROY){session.unlocked=false;owner.lifecycle.removeObserver(observer)}
        }
        activity.lifecycle.addObserver(observer)
        session
    }
}

private fun Context.fragmentActivity():FragmentActivity? {
    var candidate=this
    while(candidate is ContextWrapper){if(candidate is FragmentActivity)return candidate;candidate=candidate.baseContext}
    return candidate as? FragmentActivity
}

fun financeAccessUnlocked(context:Context):Boolean {
    val activity=context.fragmentActivity() ?: return false
    val session=FinanceAccessSessions.acquire(activity)
    return session.scope==AndroidJobs.scopeKey(WorkspaceIdentity.activeUid(context)) && session.unlocked
}

/** Android owns and verifies the credential; a local UI button can never grant access. */
@Composable fun FinanceAccessGate(vm:VeyraViewModel,content:@Composable ()->Unit){
    val context=LocalContext.current
    val activity=context.fragmentActivity()
    val uid=WorkspaceIdentity.activeUid(context)
    val scopeKey=AndroidJobs.scopeKey(uid)
    val enabled=vm.preferences["financeLock"]=="Sim"
    val session=remember(activity){activity?.let(FinanceAccessSessions::acquire)}
    var secure by remember{mutableStateOf(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)}
    var message by remember(scopeKey){mutableStateOf("")}
    var prompting by remember(scopeKey){mutableStateOf(false)}
    val prompt=remember(activity,scopeKey){activity?.let{host->
        BiometricPrompt(host,ContextCompat.getMainExecutor(context),object:BiometricPrompt.AuthenticationCallback(){
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){
                FinanceAuthenticationUi.active=false;prompting=false
                if(AndroidJobs.isCurrentScope(context,uid) && host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){
                    session?.scope=scopeKey;session?.unlocked=true;message=""
                }
            }
            override fun onAuthenticationFailed(){message="A biometria não foi reconhecida. Tente novamente ou use o bloqueio do aparelho."}
            override fun onAuthenticationError(errorCode:Int,errString:CharSequence){
                FinanceAuthenticationUi.active=false;prompting=false
                message=when(errorCode){BiometricPrompt.ERROR_USER_CANCELED,BiometricPrompt.ERROR_CANCELED,BiometricPrompt.ERROR_NEGATIVE_BUTTON->"Autenticação cancelada. Seus dados continuam protegidos.";else->errString.toString().take(200)}
            }
        })
    }}
    fun authenticate(){
        if(prompt==null || prompting)return
        secure=context.getSystemService(KeyguardManager::class.java).isDeviceSecure
        if(!secure){message="Configure um bloqueio de tela no Android para desbloquear o financeiro.";return}
        runCatching{
            prompting=true;FinanceAuthenticationUi.active=true;message=""
            // WEAK + DEVICE_CREDENTIAL is supported by AndroidX from API 26; STRONG +
            // DEVICE_CREDENTIAL alone has unsupported combinations on Android 9/10.
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear financeiro")
                .setSubtitle("Use sua biometria ou o bloqueio de tela do Android")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
        }.onFailure{prompting=false;FinanceAuthenticationUi.active=false;message=it.message ?: "Não foi possível abrir a autenticação do Android."}
    }
    DisposableEffect(activity,prompt){
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)secure=context.getSystemService(KeyguardManager::class.java).isDeviceSecure}
        activity?.lifecycle?.addObserver(observer)
        onDispose{activity?.lifecycle?.removeObserver(observer);prompt?.cancelAuthentication();FinanceAuthenticationUi.active=false}
    }
    val unlocked=session?.scope==scopeKey && session?.unlocked==true
    LaunchedEffect(enabled,scopeKey,secure){if(enabled && !unlocked && secure)authenticate()}
    if(!enabled || unlocked)content() else Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Icon(Icons.Default.Lock,"Financeiro protegido",Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp));Text("Seu financeiro está protegido",style=MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp));Text("Confirme sua identidade usando a autenticação do Android. A proteção é renovada quando o app vai para o segundo plano.")
        Spacer(Modifier.height(20.dp))
        if(secure)Button(onClick=::authenticate,enabled=!prompting && activity!=null,modifier=Modifier.fillMaxWidth()){Text(if(prompting)"Aguardando autenticação…"else"Desbloquear financeiro")}
        else Button(onClick={context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))},modifier=Modifier.fillMaxWidth()){Text("Configurar bloqueio do aparelho")}
        if(message.isNotBlank()){Spacer(Modifier.height(12.dp));Text(message,color=MaterialTheme.colorScheme.error)}
    }
}
