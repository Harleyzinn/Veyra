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
import android.content.Intent
import android.speech.RecognizerIntent

@Composable fun AssistantScreen(vm:VeyraViewModel){var question by remember{mutableStateOf("")};var endpoint by remember{mutableStateOf(vm.preferences["aiEndpoint"].orEmpty())};var model by remember{mutableStateOf(vm.preferences["aiModel"].orEmpty())};var confirm by remember{mutableStateOf(false)}
    val voice=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let{question=it}}
    val context=androidx.compose.ui.platform.LocalContext.current
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("Um assistente sob seu controle",style=MaterialTheme.typography.headlineMedium);Text("O modo local consulta seus registros por comandos conhecidos; funciona sem IA ou internet.")}
        item{OutlinedTextField(question,{question=it},label={Text("Pergunte sobre sua vida")},modifier=Modifier.fillMaxWidth(),minLines=2);Row{Button(onClick={vm.localAssistant(question)}){Text("Consultar localmente")};TextButton(onClick={val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR");if(intent.resolveActivity(context.packageManager)!=null)voice.launch(intent)else vm.assistantReply="Reconhecimento de voz indisponível neste aparelho."}){Text("Usar voz")}}}
        item{Text("Exemplos: quanto gastei este mês; tarefas amanhã; assinaturas; próxima prova; resumo.",style=MaterialTheme.typography.bodySmall)}
        if(vm.assistantReply.isNotBlank())item{EmptyCard("Resposta",vm.assistantReply)}
        item{HorizontalDivider();Text("IA online opcional",style=MaterialTheme.typography.titleLarge);Text("Configure um provedor compatível. Somente a pergunta digitada é enviada; a chave dura esta sessão. O serviço de voz é fornecido pelo Android e pode usar a rede.")}
        item{OutlinedTextField(endpoint,{endpoint=it},label={Text("URL HTTPS de chat completions")},modifier=Modifier.fillMaxWidth());OutlinedTextField(model,{model=it},label={Text("Nome do modelo")},modifier=Modifier.fillMaxWidth());OutlinedTextField(vm.sessionKey,{vm.sessionKey=it},label={Text("Chave • não será salva")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth());Button(onClick={vm.pref("aiEndpoint",endpoint);vm.pref("aiModel",model);confirm=true},enabled=!vm.busy && question.isNotBlank()){Text("Revisar envio à IA")}}
        item{Spacer(Modifier.height(100.dp))}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Enviar esta pergunta?")},text={Text("Destino: $endpoint\n\n$question\n\nO provedor receberá este texto e sua chave de acesso. Nenhum registro do app será incluído automaticamente.")},confirmButton={TextButton(onClick={vm.onlineAssistant(question);confirm=false}){Text("Enviar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
}
