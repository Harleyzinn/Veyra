package app.veyra.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.veyra.feature.finance.CashPoint
import app.veyra.model.Item
import app.veyra.feature.finance.FinancialDomain
import app.veyra.feature.finance.FinanceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private val financeColors=listOf(Color(0xFFB7A1FF),Color(0xFF88D5BA),Color(0xFFF3C58A),Color(0xFF83BFEF),Color(0xFFEB9AC4),Color(0xFFBBC2CE))

@Composable internal fun FinanceMonthlyChart(all:List<Item>,month:YearMonth,financialDay:Int,currency:String,hidden:Boolean){
    if(hidden){Text("Comparação mensal oculta pelo modo privacidade.");return}
    val months=remember(month){(3L downTo 0L).map{month.minusMonths(it)}}
    var selected by remember(month){mutableStateOf(month)}
    val comparisons by produceState<List<Triple<YearMonth,Long,Long>>>(emptyList(),all,months,currency,financialDay){value=withContext(Dispatchers.Default){months.map{period->val summary=FinanceEngine.summary(all,period,LocalDate.now(),currency,financialDay);Triple(period,summary.incomeMinor,summary.expenseMinor)}}}
    val maximum=comparisons.maxOfOrNull{maxOf(it.second,it.third)}?.coerceAtLeast(1) ?: 1
    comparisons.forEach{(period,income,expense)->
        OutlinedCard(onClick={selected=period},modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(period.format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy",Brazilian)),style=MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress={income.toFloat()/maximum},modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.secondary)
                LinearProgressIndicator(progress={expense.toFloat()/maximum},modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.tertiary)
            }
        }
    }
    comparisons.firstOrNull{it.first==selected}?.let{(_,income,expense)->
        Text("${selected}: entradas ${financeMoney(income,currency)} · gastos ${financeMoney(expense,currency)}",style=MaterialTheme.typography.titleSmall)
        Text("Economia: ${financeMoney(Math.subtractExact(income,expense),currency)}",style=MaterialTheme.typography.bodySmall)
    }
    Text("Verde: entradas recebidas. Dourado: gastos reconhecidos. Toque em um mês para ver os valores.",style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun FinanceFlowChart(points:List<CashPoint>,currency:String,hidden:Boolean){
    var selected by remember(points){mutableIntStateOf(0)}
    val line=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant
    if(hidden){Text("Gráfico oculto pelo modo privacidade.");return}
    if(points.isEmpty()){Text("Sem dados para esse período.");return}
    val selectedPoint=points[selected.coerceIn(points.indices)]
    Text("${dateLabel(selectedPoint.date.toString())} · ${financeMoney(selectedPoint.balanceMinor,currency)}",style=MaterialTheme.typography.titleMedium)
    Text(selectedPoint.descriptions.joinToString(" · ").ifBlank{"Saldo projetado"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    val first=points.first().date.toEpochDay();val distance=(points.last().date.toEpochDay()-first).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(155.dp).semantics{contentDescription="Fluxo de caixa projetado. Selecione uma data nos botões abaixo."}.pointerInput(points){detectTapGestures{position->val target=first+distance*position.x/size.width;selected=points.indices.minByOrNull{abs(points[it].date.toEpochDay()-target)} ?: 0}}){
        val min=minOf(0L,points.minOf{it.balanceMinor}).toDouble();val max=maxOf(0L,points.maxOf{it.balanceMinor}).toDouble();val range=(max-min).coerceAtLeast(1.0)
        val top=10.dp.toPx();val bottom=size.height-10.dp.toPx()
        fun x(p:CashPoint)=((p.date.toEpochDay()-first).toDouble()/distance*size.width).toFloat()
        fun y(amount:Long)=(top+(max-amount)/range*(bottom-top)).toFloat()
        drawLine(grid,Offset(0f,y(0)),Offset(size.width,y(0)),1.dp.toPx())
        val path=Path();points.forEachIndexed{i,p->if(i==0)path.moveTo(x(p),y(p.balanceMinor))else path.lineTo(x(p),y(p.balanceMinor))}
        val area=Path().apply{addPath(path);lineTo(x(points.last()),bottom);lineTo(x(points.first()),bottom);close()}
        drawPath(area,line.copy(alpha=.12f));drawPath(path,line,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
        val point=points[selected.coerceIn(points.indices)];drawCircle(line,5.dp.toPx(),Offset(x(point),y(point.balanceMinor)))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={selected=(selected-1).coerceAtLeast(0)},enabled=selected>0){Text("Data anterior")}
        OutlinedButton(onClick={selected=(selected+1).coerceAtMost(points.lastIndex)},enabled=selected<points.lastIndex){Text("Próxima data")}
    }
}

@Composable internal fun FinanceCategoryChart(categories:List<Pair<String,Long>>,currency:String,hidden:Boolean){
    if(categories.isEmpty()){Text("Registre gastos para comparar categorias.");return}
    if(hidden){Text("Categorias ocultas pelo modo privacidade.");return}
    val total=categories.fold(0L){sum,value->Math.addExact(sum,value.second)}
    var selected by remember(categories){mutableIntStateOf(0)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){
        Canvas(Modifier.size(160.dp).semantics{contentDescription="Gastos por categoria. Os valores estão listados abaixo."}){
            var angle=-90f;categories.forEachIndexed{i,entry->val sweep=if(total>0)entry.second.toDouble()/total*360 else 0.0
                drawArc(financeColors[i%financeColors.size],angle,sweep.toFloat(),false,Offset(14.dp.toPx(),14.dp.toPx()),Size(size.width-28.dp.toPx(),size.height-28.dp.toPx()),style=Stroke(if(selected==i)24.dp.toPx() else 18.dp.toPx()));angle+=sweep.toFloat()
            }
        }
    }
    categories.take(20).forEachIndexed{i,(label,value)->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable{selected=i}.padding(vertical=8.dp)){
            Row{Text(label,Modifier.weight(1f));Text(financeMoney(value,currency),style=MaterialTheme.typography.labelLarge)}
            LinearProgressIndicator(progress={if(total==0L)0f else value.toFloat()/total},modifier=Modifier.fillMaxWidth().padding(top=8.dp),color=financeColors[i%financeColors.size])
        }
    }
}

@Composable internal fun FinanceCalendar(month:YearMonth,entries:List<Item>,currency:String,hidden:Boolean,open:(Item)->Unit){
    var day by remember(month){mutableStateOf(month.atDay(1))}
    val grouped=entries.groupBy{entry->runCatching{FinancialDomain.dueDate(entry)}.getOrElse{LocalDate.parse(entry.date)}}
    Row(Modifier.fillMaxWidth()){listOf("S","T","Q","Q","S","S","D").forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)}}
    val offset=month.atDay(1).dayOfWeek.value-1
    val slots=List(offset){0}+(1..month.lengthOfMonth()).toList()
    slots.chunked(7).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){
        (week+List(7-week.size){0}).forEach{number->
            if(number==0)Spacer(Modifier.weight(1f))else{
                val date=month.atDay(number);val count=grouped[date].orEmpty().size
                Surface(onClick={day=date},modifier=Modifier.weight(1f).height(57.dp),shape=RoundedCornerShape(12.dp),color=if(day==date)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)){
                    Column(Modifier.padding(5.dp)){Text(number.toString(),style=MaterialTheme.typography.labelLarge);if(count>0)Text("$count itens",style=MaterialTheme.typography.labelSmall)}
                }
            }
        }
    }}
    Text("${dateLabel(day.toString())} · vencimentos",style=MaterialTheme.typography.titleMedium)
    if(grouped[day].isNullOrEmpty())Text("Nenhum vencimento registrado neste dia.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    grouped[day].orEmpty().forEach{FinanceTransactionRow(it,currency,hidden,open)}
}

@Composable internal fun FinanceTransactionRow(item:Item,currency:String,hidden:Boolean,open:(Item)->Unit){
    OutlinedCard(onClick={open(item)},modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            IconBadge(moduleIcon(item.type),size=36)
            Column(Modifier.weight(1f)){Text(item.title,style=MaterialTheme.typography.titleSmall);Text("${dateLabel(item.date)} · ${item.value("category").ifBlank{"Sem categoria"}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(FinancialDomain.status(item).label,style=MaterialTheme.typography.labelSmall)}
            Text((if(item.type=="income")"+ "else if(item.type=="expense")"− "else"↔ ")+financeMoney(FinancialDomain.amount(item),currency,hidden),style=MaterialTheme.typography.labelLarge)
        }
    }
}
