package app.veyra.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun AgendaScreen(items:List<Item>,open:(Item)->Unit,create:(String)->Unit,vm:VeyraViewModel){
    var mode by remember{mutableStateOf("Mês")};var month by remember{mutableStateOf(YearMonth.now())};var selected by remember{mutableStateOf(LocalDate.now())}
    val scheduled=items.filter{it.type in setOf("task","event","exam")}
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{PageHeading("SEU TEMPO É VALIOSO","Dê espaço aos planos.","Tarefas e compromissos, na mesma perspectiva.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Mês","Semana","Dia","Kanban").forEach{FilterChip(selected=mode==it,onClick={mode=it},label={Text(it)})}}}
        if(mode=="Mês")item{StudioCard{
            Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={month=month.minusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronLeft,"Mês anterior")};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Brazilian)).replaceFirstChar{it.titlecase(Brazilian)},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center);IconButton(onClick={month=month.plusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronRight,"Próximo mês")}}
            Row{listOf("S","T","Q","Q","S","S","D").forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            val offset=month.atDay(1).dayOfWeek.value-1;val cells=List(offset){0}+(1..month.lengthOfMonth()).toList()
            cells.chunked(7).forEach{week->Row{(week+List(7-week.size){0}).forEach{day->Box(Modifier.weight(1f).height(44.dp),contentAlignment=Alignment.Center){if(day>0){val date=month.atDay(day);Column(Modifier.size(38.dp).clip(CircleShape).background(if(date==selected)MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent).clickable{selected=date},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(day.toString(),style=MaterialTheme.typography.labelLarge,color=if(date==selected)MaterialTheme.colorScheme.onPrimary else if(date==LocalDate.now())MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface);if(scheduled.any{it.date==date.toString()})Box(Modifier.size(3.dp).background(if(date==selected)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,CircleShape))}}}}}
            }
        }}
        if(mode in listOf("Semana","Dia"))item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={selected=selected.minusDays(if(mode=="Semana")7 else 1)}){Icon(Icons.Default.ChevronLeft,"Período anterior")};Text(dateLabel(selected.toString()),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick={selected=selected.plusDays(if(mode=="Semana")7 else 1)}){Icon(Icons.Default.ChevronRight,"Próximo período")};TextButton(onClick={selected=LocalDate.now();month=YearMonth.now()}){Text("Hoje")}}}
        if(mode=="Kanban"){
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(16.dp)){listOf("A fazer","Fazendo","Concluído").forEach{stage->val tasks=items.filter{it.type=="task" && (if(it.done)"Concluído"else it.value("status").ifBlank{"A fazer"})==stage};Column(Modifier.width(265.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("$stage · ${tasks.size}",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary);if(tasks.isEmpty())EmptyCard("Sem tarefas","As tarefas nesta etapa aparecem aqui.");tasks.forEach{i->Card(onClick={open(i)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(i.title,style=MaterialTheme.typography.titleMedium);Text(dateLabel(i.date),style=MaterialTheme.typography.bodySmall);if(stage!="Concluído")TextButton(onClick={if(stage=="A fazer")vm.save(i.copy(fields=i.fields+("status" to "Fazendo")))else vm.complete(i)}){Text(if(stage=="A fazer")"Começar →"else "Concluir ✓")}}}}}}}}
        }else{
            val shown=scheduled.filter{if(mode=="Semana")it.date>=selected.toString() && it.date<=selected.plusDays(6).toString()else it.date==selected.toString()}.sortedWith(compareBy<Item>{it.date}.thenBy{it.value("time")})
            item{SectionTitle(if(mode=="Semana")"Nesta semana"else "${dateLabel(selected.toString())} · ${shown.size} planos","+ Evento"){create(selected.toString())}}
            if(shown.isEmpty())item{EmptyCard("Um respiro no calendário","Adicione um compromisso ou reserve espaço para algo importante.")}
            items(shown,key={it.id}){i->StudioCard{CompactTask(i,vm,open)}}
        }
    }
}
