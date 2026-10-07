package app.veyra.android
import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import app.veyra.designsystem.VeyraTheme
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance

class MainActivity:FragmentActivity(){
    private var unlocked by mutableStateOf(false)
    private var launchIntent by mutableStateOf<Intent?>(null)
    private var entryRevision by mutableIntStateOf(0)
    override fun onStop(){super.onStop();if(!FinanceAuthenticationUi.active)unlocked=false}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);launchIntent=intent;entryRevision++}
    fun biometric(){val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),object:BiometricPrompt.AuthenticationCallback(){override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){unlocked=true}});prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear Veyra").setSubtitle("Sua central pessoal").setNegativeButtonText("Usar PIN").build())}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge()
        val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
        launchIntent=intent
        setContent{val vm:VeyraViewModel=viewModel();VeyraTheme(vm.preferences["theme"] ?: "Escuro"){
            val entry=launchIntent ?: intent
            val accepted=!entry.getBooleanExtra("scopeBound",false) || AndroidJobs.isCurrentScope(this,entry.getStringExtra("scopeUid")?.takeIf{it.isNotBlank()})
            val shared=if(accepted && entry.action==Intent.ACTION_SEND)entry.getStringExtra(Intent.EXTRA_TEXT) else null
            @Suppress("DEPRECATION") val image=if(accepted && entry.action==Intent.ACTION_SEND && entry.type?.startsWith("image/")==true)entry.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) else null
            val lightBars=MaterialTheme.colorScheme.background.luminance()>.5f
            SideEffect{WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=lightBars;isAppearanceLightNavigationBars=lightBars}}
            if(!vm.loaded)Box(Modifier.fillMaxSize().padding(48.dp)){CircularProgressIndicator()}
            else if(vm.preferences["lock"].orEmpty().isNotBlank() && !unlocked) LockScreen(vm.preferences.getValue("lock"),{unlocked=true},::biometric)
            else VeyraApp(vm,shared,image,if(accepted)entry.getStringExtra("type")else null,accepted && entry.getBooleanExtra("capture",false),if(accepted)entry.getStringExtra("item")else null,{if(android.os.Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)},openUpdates=accepted && entry.getBooleanExtra("updates",false),openFinance=accepted && entry.getBooleanExtra("finance",false),entryRevision=entryRevision)
        }}
    }
}
