package app.veyra.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.veyra.model.*
import app.veyra.feature.finance.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun TodayScreen(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,create:(String)->Unit,navigate:(String)->Unit={}){
    val today=LocalDate.now()
    val financialItems by vm.financeItems.collectAsState()
    val currency=vm.preferences["financeCurrency"] ?: "BRL"
    val locked=vm.preferences["financeLock"]=="Sim" && !financeAccessUnlocked(LocalContext.current)
    val hidden=vm.preferences["financeHidden"]=="Sim" || locked
    val favorites=items.filter{it.favorite && (!locked || !financeSensitive(it,items))}
    val financialDay=(vm.preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31)
    val summary by produceState<FinancialSummary?>(null,financialItems,currency,financialDay){value=withContext(Dispatchers.Default){runCatching{FinanceEngine.summary(financialItems,YearMonth.now(),today,currency,financialDay)}.getOrNull()}}
    val tasks=items.filter{it.type=="task" && (it.date.isBlank() || it.date<=today.toString())};val pending=tasks.filter{!it.done}
    val cards=(vm.preferences["home"] ?: "tasks,balance,weather,habits,focus,favorites").split(',')
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
        item{PageHeading(today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM",Brazilian)),"${when(LocalTime.now().hour){in 5..11->"Bom dia";in 12..17->"Boa tarde";else->"Boa noite"}}, ${vm.preferences["name"] ?: "você"}.","Um pouco de ordem. Muito mais possibilidades.")}
        item{Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFBDA6FF),Color(0xFF9782DD))))){
            Canvas(Modifier.matchParentSize()){drawCircle(Color.White.copy(alpha=.13f),140.dp.toPx(),Offset(size.width+35.dp.toPx(),size.height/2),style=Stroke(25.dp.toPx()));drawCircle(Color.White.copy(alpha=.14f),85.dp.toPx(),Offset(size.width,size.height),style=Stroke(1.dp.toPx()))}
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Text("SEU DIA, NO SEU RITMO",Modifier.weight(1f),color=Color(0xFF30214F),style=MaterialTheme.typography.labelMedium);Icon(Icons.Default.AutoAwesome,null,tint=Color(0xFF30214F))}
                Text(if(pending.isEmpty())"Abra espaço para\no que importa."else "Um passo de\ncada vez.",color=Color(0xFF211733),style=MaterialTheme.typography.headlineLarge)
                Text(if(pending.isEmpty())"Comece com uma tarefa, ideia ou plano."else "${pending.size} tarefas pedindo sua atenção hoje.",color=Color(0xFF443363),style=MaterialTheme.typography.bodyMedium)
                Button(onClick={create("task")},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF251B3B),contentColor=Color.White)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Planejar meu dia")}
            }
        }}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("task" to "Tarefas","finance" to "Finanças","notes" to "Notas","city" to "Clima","routine" to "Rotina").forEach{(type,label)->Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable{navigate(type)}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){IconBadge(moduleIcon(when(type){"finance"->"account";"notes"->"note";else->type}),size=48);Text(label,style=MaterialTheme.typography.labelLarge)}}}}
        if("tasks" in cards)item{StudioCard{SectionTitle("Na sua lista","Ver tudo"){navigate("task")};if(pending.isEmpty())Text("Seu dia está livre. Adicione algo que você quer tirar do papel.",color=MaterialTheme.colorScheme.onSurfaceVariant)else pending.sortedWith(compareBy<Item>{if(it.value("priority")=="Urgente")0 else if(it.value("priority")=="Alta")1 else 2}.thenBy{it.date}).take(3).forEach{CompactTask(it,vm,open)};if(tasks.isNotEmpty()){LinearProgressIndicator(progress={tasks.count{it.done}.toFloat()/tasks.size},modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)));Text("${tasks.count{it.done}} de ${tasks.size} concluídas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
        if("balance" in cards)item{StudioCard{Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon("account"),MaterialTheme.colorScheme.secondary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Seu dinheiro",style=MaterialTheme.typography.titleMedium);Text(today.format(DateTimeFormatter.ofPattern("MMMM yyyy",Brazilian)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={navigate("finance")}){Icon(Icons.Default.ArrowOutward,"Abrir finanças")}};summary?.let{s->Text(financeMoney(s.currentBalance,currency,hidden),style=MaterialTheme.typography.headlineLarge);Text("Saldo realizado das suas contas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("↙ Receitas do mês",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelMedium);Text(financeMoney(s.incomeMinor,currency,hidden),style=MaterialTheme.typography.titleSmall)};Column{Text("↗ Gastos do mês",color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.labelMedium);Text(financeMoney(s.expenseMinor,currency,hidden),style=MaterialTheme.typography.titleSmall)}}} ?: Text("Carregando resumo financeiro…")}}
        if("weather" in cards)item{WeatherHome(items,vm){navigate("city")}}
        if("habits" in cards)item{StudioCard{SectionTitle("Pequenas constâncias","+ Hábito"){create("habit")};val habits=items.filter{it.type=="habit"};if(habits.isEmpty())Text("Ler, treinar, dormir melhor. Construa sua rotina com um hábito de cada vez.",color=MaterialTheme.colorScheme.onSurfaceVariant);habits.take(3).forEach{h->val checked=items.any{it.type=="checkin" && it.parentId==h.id && it.date==today.toString()};Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon("habit"),MaterialTheme.colorScheme.tertiary,size=36);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(h.title,style=MaterialTheme.typography.titleSmall);Text("${Workspace.streak(items,h.id,today)} dias de sequência",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Checkbox(checked,{vm.checkin(h)})}}}}
        item{val water=items.filter{it.type=="water" && it.date==today.toString()}.sumOf{it.number("ml").toInt()};StudioCard{Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon("water"),Color(0xFF83BFEF));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Uma pausa para água",style=MaterialTheme.typography.titleSmall);Text("$water ml registrados hoje",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};FilledTonalButton(onClick={vm.save(Item(type="water",title="Copo de água",fields=mapOf("ml" to "250")))}){Text("+250 ml")}}}}
        if("focus" in cards)item{StudioCard{Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon("focus"));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Entre no modo foco",style=MaterialTheme.typography.titleMedium);Text("Uma coisa por vez. Você consegue.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};Button(onClick={navigate("focus")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Text("Abrir Pomodoro")}}}
        if("favorites" in cards && favorites.isNotEmpty()){item{SectionTitle("Sempre por perto")};items(favorites.take(4),key={it.id}){i->Card(onClick={open(i)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(i.type));Spacer(Modifier.width(12.dp));Text(i.title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);Icon(Icons.Default.Star,null,tint=MaterialTheme.colorScheme.tertiary)}}}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){MiniMetric("Notas guardadas",items.count{it.type=="note"}.toString(),moduleIcon("note"),MaterialTheme.colorScheme.primary,Modifier.weight(1f));MiniMetric("Minutos de foco",items.filter{it.type=="focus"}.sumOf{it.number("minutes").toInt()}.toString(),moduleIcon("focus"),MaterialTheme.colorScheme.secondary,Modifier.weight(1f))}}
        item{Text("FEITO PARA SUA VIDA REAL",Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=2.sp)}
    }
}
@Composable fun Summary(label:String,value:String,description:String){StudioCard{Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(value,style=MaterialTheme.typography.headlineMedium);Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
