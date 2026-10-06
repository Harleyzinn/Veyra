package app.veyra.android
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.veyra.data.BackupCrypto
import android.net.Uri
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import kotlinx.coroutines.*

private fun pinKey(pin:String,salt:ByteArray)=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(),salt,210000,256)).encoded
private fun pinHash(pin:String):String{require(pin.length>=8 && pin.all(Char::isDigit)){"Use pelo menos 8 dígitos"};val salt=ByteArray(16).also{java.security.SecureRandom().nextBytes(it)};return Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(pinKey(pin,salt),Base64.NO_WRAP)}
@Composable fun LockScreen(hash:String,unlock:()->Unit,biometric:()->Unit){var pin by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope();var checking by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center){Text("Seu espaço pessoal",style=MaterialTheme.typography.headlineLarge);OutlinedTextField(pin,{pin=it},label={Text("PIN")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Button(onClick={scope.launch{checking=true;val ok=withContext(Dispatchers.Default){runCatching{val parts=hash.split(':');java.security.MessageDigest.isEqual(pinKey(pin,Base64.decode(parts[0],Base64.NO_WRAP)),Base64.decode(parts[1],Base64.NO_WRAP))}.getOrDefault(false)};checking=false;if(ok)unlock()else error="PIN incorreto"}},enabled=!checking){Text("Desbloquear")};OutlinedButton(onClick=biometric){Text("Usar biometria")};Text(error,color=MaterialTheme.colorScheme.error)}
}
@Composable fun SettingsScreen(vm:VeyraViewModel,permission:()->Unit){
    var password by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")};var pinError by remember{mutableStateOf("")};var restoreUri by remember{mutableStateOf<Uri?>(null)};var exportMode by remember{mutableStateOf("json")};var importOfx by remember{mutableStateOf(false)};var clear by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){it?.let{uri->if(exportMode=="pdf")vm.pdf(uri)else vm.export(uri,password,exportMode)}}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){restoreUri=it}
    val statement=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{uri->vm.previewStatement(uri,importOfx)}}
    val scan=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){it?.let{uri->vm.scan(uri)}}
    val context=androidx.compose.ui.platform.LocalContext.current
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Seu app, seu jeito",style=MaterialTheme.typography.headlineMedium);Text("Veyra ${GitHubUpdater.currentVersion(context)} • sua vida em um só lugar")}
        item{UpdateStudio(vm)}
        item{Choice("Tema",listOf("Sistema","Claro","Escuro","AMOLED","Dinâmico"),vm.preferences["theme"] ?: "Escuro",{vm.pref("theme",it)});Choice("Perfil",listOf("Essencial","Produtividade","Estudos","Finanças","Completo","Personalizado"),vm.preferences["profile"] ?: "Completo",{vm.pref("profile",it)})}
        item{Text("Cards da Home",style=MaterialTheme.typography.titleLarge);val current=(vm.preferences["home"] ?: "tasks,balance,weather,habits,focus,favorites").split(',').filter{it.isNotBlank()}
            listOf("tasks","balance","habits","focus","favorites","weather").forEach{key->Row{Checkbox(key in current,{enabled->vm.pref("home",if(enabled)(current+key).joinToString(",")else current.filter{it!=key}.joinToString(","))});Text(mapOf("tasks" to "Meu Dia","balance" to "Finanças","habits" to "Hábitos","focus" to "Foco","favorites" to "Favoritos","weather" to "Clima").getValue(key),Modifier.weight(1f).padding(top=12.dp));TextButton(onClick={val order=current.toMutableList();val index=order.indexOf(key);if(index>0){java.util.Collections.swap(order,index,index-1);vm.pref("home",order.joinToString(","))}}){Text("↑")}}}
        }
        item{Choice("Ocultar / reexibir módulo",Registry.specs.map{it.type},"",{type->val hidden=vm.preferences["hidden"].orEmpty().split(',').filter{it.isNotBlank()};vm.pref("hidden",if(type in hidden)hidden.filter{it!=type}.joinToString(",")else (hidden+type).joinToString(","))}){Registry.spec(it).label};Text("Ocultos: "+vm.preferences["hidden"].orEmpty().split(',').filter{it.isNotBlank()}.joinToString{Registry.spec(it).label},style=MaterialTheme.typography.bodySmall)}
        item{HorizontalDivider();Text("Importar, exportar e proteger",style=MaterialTheme.typography.titleLarge);OutlinedTextField(password,{password=it},label={Text("Senha de backup • opcional, mínimo 8 caracteres")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Row{Button(onClick={exportMode="json";save.launch("veyra-backup.json")}){Text("Backup JSON")};TextButton(onClick={restore.launch(arrayOf("application/json","application/octet-stream"))}){Text("Restaurar")}};Text("Senha preenchida usa AES-GCM e PBKDF2. Backup sem senha é legível. Importação mescla por ID e inclui anexos.",style=MaterialTheme.typography.bodySmall)}
        item{Row{TextButton(onClick={exportMode="csv";save.launch("veyra-financas.csv")}){Text("Exportar CSV")};TextButton(onClick={exportMode="pdf";save.launch("veyra-relatorio.pdf")}){Text("Relatório PDF")}}}
        item{Row{Button(onClick={importOfx=false;statement.launch(arrayOf("text/*","application/octet-stream"))}){Text("Importar CSV")};TextButton(onClick={importOfx=true;statement.launch(arrayOf("*/*"))}){Text("Importar OFX")}};Text("CSV: date,title,amount,category. Datas AAAA-MM-DD e valor negativo para despesa. Revise a prévia antes de confirmar.",style=MaterialTheme.typography.bodySmall)}
        item{Button(onClick={scan.launch("image/*")}){Text("Ler recibo por OCR local")}}
        item{Row{Checkbox(vm.preferences["autoBackup"]=="Sim",{vm.pref("autoBackup",if(it)"Sim"else "Não")});Text("Backup automático local",Modifier.padding(top=12.dp))};Text("Cópia horária privada no aparelho, sem senha; não protege contra perda do celular. Use exportação com senha para guardar fora dele.",style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.operation{val file=java.io.File(context.filesDir,"automatic-backup.json");require(file.exists()){ "Ainda não existe backup automático" };vm.store.importJson(file.readText())}}){Text("Restaurar cópia automática")}}
        item{HorizontalDivider();Text("Privacidade",style=MaterialTheme.typography.titleLarge);Text("Banco e anexos ficam no espaço privado do app. Rede: clima, IA quando solicitada e consultas de atualização ao GitHub (podem ser desativadas). Sem anúncios ou analytics. O PIN bloqueia a interface; o banco ainda não tem criptografia adicional.")}
        item{OutlinedTextField(pin,{pin=it},label={Text("Novo PIN • pelo menos 8 dígitos")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Button(onClick={scope.launch{runCatching{withContext(Dispatchers.Default){pinHash(pin)}}.onSuccess{vm.pref("lock",it);pin=""}.onFailure{pinError=it.message.orEmpty()}}}){Text("Ativar bloqueio")};if(vm.preferences["lock"].orEmpty().isNotBlank())TextButton(onClick={vm.pref("lock","")}){Text("Desativar bloqueio")};Text(pinError,color=MaterialTheme.colorScheme.error)}
        item{Button(onClick=permission){Text("Permitir notificações Android")};Text("Lembretes usam o agendador do Android e podem atrasar conforme bateria e sistema. Adicione o widget Veyra na tela inicial e o bloco Capturar nas configurações rápidas.",style=MaterialTheme.typography.bodySmall)}
        item{Choice("Apagar dados de um módulo",Registry.specs.map{it.type},"",{clear=it}){Registry.spec(it).label}}
        item{Spacer(Modifier.height(70.dp))}
    }
    restoreUri?.let{uri->AlertDialog(onDismissRequest={restoreUri=null},title={Text("Mesclar backup?")},text={Text("Itens com o mesmo ID serão substituídos. Exporte uma cópia antes. Use a senha informada acima se o arquivo estiver criptografado.")},confirmButton={TextButton(onClick={vm.restore(uri,password);restoreUri=null}){Text("Importar")}},dismissButton={TextButton(onClick={restoreUri=null}){Text("Cancelar")}})}
    if(vm.importPreview.isNotEmpty())AlertDialog(onDismissRequest=vm::cancelStatement,title={Text("${vm.importPreview.size} lançamentos novos")},text={LazyColumn{vm.importPreview.take(100).forEach{i->item{Text("${i.date} • ${i.title} • ${money(i.cents())}")}}}},confirmButton={TextButton(onClick=vm::confirmStatement){Text("Confirmar importação")}},dismissButton={TextButton(onClick=vm::cancelStatement){Text("Cancelar")}})
    if(clear.isNotBlank())AlertDialog(onDismissRequest={clear=""},title={Text("Apagar ${Registry.spec(clear).label}?")},text={Text("Esta ação remove os dados desse módulo. Exporte um backup antes de continuar.")},confirmButton={TextButton(onClick={vm.clearModule(clear);clear=""}){Text("Apagar")}},dismissButton={TextButton(onClick={clear=""}){Text("Cancelar")}})
}
