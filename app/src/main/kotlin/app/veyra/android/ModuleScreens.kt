package app.veyra.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import java.time.*

@Composable fun ModuleScreen(type:String,items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,edit:(Item)->Unit,delete:(Item)->Unit,create:(String)->Unit){
    var onlyPending by remember{mutableStateOf(false)};val data=items.filter{when(type){"favorites"->it.favorite;"timeline"->it.type !in setOf("checkin","city");else->it.type==type} && (!onlyPending || !it.done)}
    Column{
        Row(Modifier.padding(horizontal=20.dp)){Text(when(type){"favorites"->"Favoritos";"timeline"->"Timeline";else->Registry.spec(type).label},Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);if(Registry.spec(type).checkable)TextButton(onClick={onlyPending=!onlyPending}){Text(if(onlyPending)"Pendentes"else "Todas")}}
        if(type=="shopping")Text("Total da lista: ${money(Workspace.shoppingTotal(data.filter{!it.done}))}",Modifier.padding(20.dp))
        if(type=="grade")Text("Média ponderada: ${Workspace.weightedGrade(data)}",Modifier.padding(20.dp))
        if(type=="focus")FocusPanel(items,vm)
        if(type=="water")Button(onClick={vm.save(Item(type="water",title="Copo de água",fields=mapOf("ml" to "250")))},modifier=Modifier.padding(20.dp)){Text("+ 250 ml")}
        if(type=="flashcard")Flashcards(data,vm)
        ItemList(data,open,edit,delete,vm)
    }
}
@Composable fun Flashcards(cards:List<Item>,vm:VeyraViewModel){var revealed by remember{mutableStateOf(false)};val card=cards.filter{it.date<=LocalDate.now().toString()}.minByOrNull{it.date}
    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){if(card==null)EmptyCard("Revisões em dia","Adicione cartões ou volte na próxima data de revisão.")else{
        Text(card.title,style=MaterialTheme.typography.headlineMedium);if(revealed)Text(card.value("answer"))
        Button(onClick={revealed=!revealed}){Text(if(revealed)"Ocultar resposta"else "Mostrar resposta")}
        if(revealed)Row{TextButton(onClick={vm.review(card,false);revealed=false}){Text("Revisar amanhã")};Button(onClick={vm.review(card,true);revealed=false}){Text("Lembrei bem")}}
    }}
}
