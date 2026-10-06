package app.veyra.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import java.time.LocalDate

@Composable fun TasksStudio(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,create:(String)->Unit){
    var filter by remember{mutableStateOf("Pendentes")};var priority by remember{mutableStateOf("Todas")};var query by remember{mutableStateOf("")};var quick by remember{mutableStateOf("")}
    val today=LocalDate.now().toString();val tasks=items.filter{it.type=="task"};val data=tasks.filter{(query.isBlank() || Workspace.matches(it,query)) && (priority=="Todas" || it.value("priority")==priority) && when(filter){"Hoje"->it.date==today && !it.done;"Atrasadas"->it.date.isNotBlank() && it.date<today && !it.done;"Concluídas"->it.done;"Todas"->true;else->!it.done}}.sortedWith(compareBy<Item>{it.done}.thenBy{if(it.value("priority")=="Urgente")0 else if(it.value("priority")=="Alta")1 else 2}.thenBy{it.date})
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("PRODUTIVIDADE","Do plano à prática.","Organize, priorize e celebre cada passo.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){MiniMetric("Pendentes",tasks.count{!it.done}.toString(),moduleIcon("task"),MaterialTheme.colorScheme.primary,Modifier.weight(1f));MiniMetric("Concluídas",tasks.count{it.done}.toString(),Icons.Default.DoneAll,MaterialTheme.colorScheme.secondary,Modifier.weight(1f))}}
        item{StudioCard{Text("Tire da cabeça",style=MaterialTheme.typography.titleMedium);OutlinedTextField(quick,{quick=it},label={Text("O que você precisa fazer?")},modifier=Modifier.fillMaxWidth(),singleLine=true);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={vm.save(Item(type="task",title=quick.trim(),fields=mapOf("priority" to "Média","status" to "A fazer")));quick=""},enabled=quick.isNotBlank() && quick.trim().length<=200){Text("Adicionar hoje")};TextButton(onClick={create("task")}){Text("Mais opções")}}}}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Pendentes","Hoje","Atrasadas","Concluídas","Todas").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})}}}
        item{OutlinedTextField(query,{query=it},label={Text("Buscar tarefa")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true);Choice("Prioridade",listOf("Todas","Baixa","Média","Alta","Urgente"),priority,{priority=it})}
        if(data.isEmpty())item{EmptyCard("Tudo em ordem por aqui","Adicione uma tarefa ou escolha outro filtro.")}
        items(data,key={it.id}){i->StudioCard{CompactTask(i,vm,open);Row(verticalAlignment=Alignment.CenterVertically){Text(i.value("priority").ifBlank{"Sem prioridade"},Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(i.value("status").ifBlank{if(i.done)"Concluído"else "A fazer"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
    }
}
@Composable fun NotesStudio(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,create:(String)->Unit){
    var area by remember{mutableStateOf("note")};var query by remember{mutableStateOf("")};var folder by remember{mutableStateOf("Todas")};var archived by remember{mutableStateOf(false)}
    val notes=items.filter{it.type==area && (area!="note" || (it.value("archived")=="Sim")==archived) && (query.isBlank() || Workspace.matches(it,query)) && (folder=="Todas" || it.value("folder")==folder)}.sortedWith(compareByDescending<Item>{it.favorite}.thenByDescending{it.createdAt})
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("SEU SEGUNDO CÉREBRO","Ideias merecem espaço.","Anote agora. Encontre quando precisar.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("note","journal","link").forEach{type->FilterChip(selected=area==type,onClick={area=type;folder="Todas"},label={Text(when(type){"journal"->"Diário";"link"->"Links";else->"Notas"})})}}}
        item{OutlinedTextField(query,{query=it},label={Text("Buscar nas suas ideias")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Row(verticalAlignment=Alignment.CenterVertically){Button(onClick={create(area)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Text(if(area=="note")"Nova nota"else if(area=="journal")"Escrever no diário"else "Salvar link")};if(area=="note")IconButton(onClick={archived=!archived}){Icon(if(archived)Icons.Default.Unarchive else Icons.Default.Inventory2,if(archived)"Mostrar notas ativas"else "Mostrar arquivadas")}};if(area=="note")Choice("Pasta",listOf("Todas")+items.filter{it.type=="note"}.map{it.value("folder")}.filter{it.isNotBlank()}.distinct(),folder,{folder=it})}
        if(archived && area=="note")item{Text("Arquivo de notas",color=MaterialTheme.colorScheme.primary)}
        if(notes.isEmpty())item{EmptyCard("Uma página em branco","Guarde ideias, listas, histórias ou aquele link que vale revisitar.")}
        items(notes,key={it.id}){note->Card(onClick={open(note)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(note.type),size=32);Spacer(Modifier.width(12.dp));Text(dateLabel(note.date),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);IconButton(onClick={vm.save(note.copy(favorite=!note.favorite))},modifier=Modifier.size(32.dp)){Icon(if(note.favorite)Icons.Default.Star else Icons.Default.StarBorder,"Favoritar nota",tint=MaterialTheme.colorScheme.tertiary)}}
            Text(note.title,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(note.notes.isNotBlank())Text(note.notes,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=4,overflow=TextOverflow.Ellipsis)
            if(note.value("url").isNotBlank())Text(note.value("url"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(note.value("folder").isNotBlank() || note.tags.isNotBlank())Text(listOf(note.value("folder"),note.tags).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            if(note.value("mood").isNotBlank())Text("Hoje: ${note.value("mood")}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)
        }}}
    }
}
@Composable fun ModuleHub(vm:VeyraViewModel,select:(String)->Unit){
    var query by remember{mutableStateOf("")};val hidden=vm.preferences["hidden"].orEmpty().split(',');val favorites=vm.preferences["favoriteModules"].orEmpty().split(',')
    val groups=when(vm.preferences["profile"]){"Essencial"->setOf("Produtividade","Notas e diário","Rotina","Clima","Ferramentas");"Produtividade"->setOf("Produtividade","Notas e diário","Rotina","Sistema","Ferramentas","Clima");"Estudos"->setOf("Estudos","Produtividade","Notas e diário","Rotina","Clima","Ferramentas");"Finanças"->setOf("Finanças","Casa e veículo","Sistema","Clima","Ferramentas");else->Registry.specs.map{it.group}.toSet()}
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("TUDO CONECTADO","Seu universo pessoal.","${Registry.specs.size} módulos para simplificar a vida.")}
        item{OutlinedTextField(query,{query=it},label={Text("Encontrar um módulo")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Button(onClick={select("routine")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.DashboardCustomize,null);Spacer(Modifier.width(8.dp));Text("Abrir central de rotina")}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){AssistChip(onClick={select("favorites")},label={Text("Favoritos")},leadingIcon={Icon(Icons.Default.Star,null,Modifier.size(16.dp))});AssistChip(onClick={select("timeline")},label={Text("Linha do tempo")},leadingIcon={Icon(Icons.Default.History,null,Modifier.size(16.dp))})}}
        Registry.specs.filter{it.type !in hidden && it.group in groups && (query.isBlank() || it.label.contains(query,true) || it.group.contains(query,true))}.groupBy{it.group}.forEach{(group,specs)->
            item{SectionTitle(group)}
            specs.sortedBy{if(it.type in favorites)0 else 1}.chunked(2).forEach{row->item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{spec->Card(onClick={select(spec.type)},modifier=Modifier.weight(1f)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(spec.type),size=38);Spacer(Modifier.weight(1f));IconButton(onClick={vm.pref("favoriteModules",if(spec.type in favorites)favorites.filter{it!=spec.type}.joinToString(",")else (favorites+spec.type).joinToString(","))},modifier=Modifier.size(28.dp)){Icon(if(spec.type in favorites)Icons.Default.Star else Icons.Default.StarBorder,"Favoritar módulo",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}};Text(spec.label,style=MaterialTheme.typography.titleSmall,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)}}};if(row.size==1)Spacer(Modifier.weight(1f))}}}
        }
    }
}
