package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.model.*

@Composable fun DecisionStudio(vm:VeyraViewModel){
    var mode by rememberSaveable{mutableStateOf("Sortear opções")};var text by rememberSaveable{mutableStateOf("")};var count by rememberSaveable{mutableStateOf("1")};var sides by rememberSaveable{mutableStateOf("6")};var result by rememberSaveable{mutableStateOf("")};var error by remember{mutableStateOf("")}
    fun run(action:()->String){runCatching(action).onSuccess{result=it;error=""}.onFailure{error=it.message ?: "Confira as opções"}}
    LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("FERRAMENTAS DE DECISÃO","Deixe a sorte escolher.","Sorteios, equipes e dados para seus planos e jogos.")}
        item{Choice("Modo",listOf("Sortear opções","Dividir equipes","Dados","Cara ou coroa"),mode,{mode=it;result="";error=""})}
        if(mode in setOf("Sortear opções","Dividir equipes"))item{OutlinedTextField(text,{text=it.take(50000)},label={Text("Uma opção ou participante por linha")},minLines=5,maxLines=12,modifier=Modifier.fillMaxWidth());Text("Opções repetidas são consideradas uma vez.",style=MaterialTheme.typography.bodySmall)}
        if(mode!="Cara ou coroa")item{OutlinedTextField(count,{count=it},label={Text(if(mode=="Dividir equipes")"Número de equipes"else if(mode=="Dados")"Número de dados"else "Quantidade de resultados")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        if(mode=="Dados")item{OutlinedTextField(sides,{sides=it},label={Text("Faces por dado • 2 a 1000")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{Button(onClick={run{when(mode){"Sortear opções"->DecisionTools.draw(text,count.toInt()).joinToString("\n");"Dividir equipes"->DecisionTools.teams(text,count.toInt()).mapIndexed{index,team->"Equipe ${index+1}\n${team.joinToString(", ")}"}.joinToString("\n\n");"Dados"->{val dice=DecisionTools.dice(count.toInt(),sides.toInt());"${dice.joinToString(" + ")}\nTotal: ${dice.sum()}"};else->if(kotlin.random.Random.nextBoolean())"Cara"else "Coroa"}}}){Text("${if(mode=="Dividir equipes")"Montar equipes"else "Sortear"}")};if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}
        if(result.isNotBlank())item{StudioCard{Text("Resultado",style=MaterialTheme.typography.titleLarge);Text(result,style=MaterialTheme.typography.titleMedium);Button(onClick={vm.save(Item(type="note",title="Resultado • $mode",notes=result));vm.pref("lastDecision",result.take(5000))}){Text("Guardar nas notas")}}}
        item{Text("Para decisões pessoais e brincadeiras. Não é um serviço de sorteios regulamentados.",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(80.dp))}
    }
}
