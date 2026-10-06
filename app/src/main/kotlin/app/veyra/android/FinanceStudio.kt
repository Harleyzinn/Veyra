package app.veyra.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import app.veyra.feature.finance.Statement
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val chartColors=listOf(Color(0xFFB7A1FF),Color(0xFF88D5BA),Color(0xFFF3C58A),Color(0xFF83BFEF),Color(0xFFEB9AC4),Color(0xFFBBC2CE))
@Composable fun FinanceScreen(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,create:(String)->Unit){
    var mode by remember{mutableStateOf("Resumo")};var month by remember{mutableStateOf(YearMonth.now())};var query by remember{mutableStateOf("")}
    var kind by remember{mutableStateOf("Todos")};var status by remember{mutableStateOf("Todos")};var category by remember{mutableStateOf("Todas")};var sort by remember{mutableStateOf("Recentes")}
    val data=FinanceInsights.transactions(items,month);val income=Workspace.income(data);val expense=Workspace.expenses(data);val categories=FinanceInsights.categories(data)
    val shown=data.filter{(kind=="Todos" || it.type==if(kind=="Receitas")"income"else if(kind=="Despesas")"expense"else "transfer") && (status=="Todos" || (it.value("planned")=="Sim" && !it.done)==(status=="Previstos")) && (category=="Todas" || it.value("category").ifBlank{"Sem categoria"}==category) && (query.isBlank() || Workspace.matches(it,query) || it.value("category").contains(query,true))}.let{list->when(sort){"Maior valor"->list.sortedByDescending{it.cents()};"Menor valor"->list.sortedBy{it.cents()};"Antigos"->list.sortedBy{it.date};else->list.sortedByDescending{it.date}}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->uri?.let{vm.operation{vm.getApplication<android.app.Application>().contentResolver.openOutputStream(it)?.bufferedWriter()?.use{writer->writer.write("\uFEFF"+Statement.report(shown,items))} ?: error("Arquivo indisponível")}}}
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{PageHeading("CONTROLE FINANCEIRO","Cada real, com destino.","Tudo que entra, sai e está nos seus planos.")}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,"Mês anterior")};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Brazilian)).replaceFirstChar{it.titlecase(Brazilian)},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,"Próximo mês")};IconButton(onClick={export.launch("veyra-$month.csv")}){Icon(Icons.Default.FileDownload,"Exportar planilha CSV")}}}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Resumo","Planilha","Contas","Orçamentos","Cartões","Assinaturas","Investimentos").forEach{FilterChip(selected=mode==it,onClick={mode=it},label={Text(it)})}}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={create("expense")},modifier=Modifier.weight(1f)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Text("Despesa")};FilledTonalButton(onClick={create("income")},modifier=Modifier.weight(1f)){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Text("Receita")}}}
        when(mode){
            "Resumo"->{
                item{StudioCard{Text("FLUXO LÍQUIDO DO MÊS",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(money(Workspace.balance(data)),style=MaterialTheme.typography.headlineLarge);Text("Receitas menos despesas realizadas",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);HorizontalDivider();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("↙ Entradas",color=MaterialTheme.colorScheme.secondary,style=MaterialTheme.typography.labelLarge);Text(money(income),style=MaterialTheme.typography.titleMedium)};Column{Text("↗ Saídas",color=MaterialTheme.colorScheme.tertiary,style=MaterialTheme.typography.labelLarge);Text(money(expense),style=MaterialTheme.typography.titleMedium)}}}}
                item{StudioCard{SectionTitle("Olhando à frente");Text("Projeção: ${money(FinanceInsights.projection(data))}",style=MaterialTheme.typography.titleMedium);Text("A receber: ${money(FinanceInsights.planned(data,"income"))}  ·  A pagar: ${money(FinanceInsights.planned(data,"expense"))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(FinanceInsights.savingsRate(data)?.let{"${"%.1f".format(Brazilian,it)}% das receitas ficaram no mês"} ?: "Registre receitas para acompanhar a taxa de economia.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.secondary)}}
                item{StudioCard{SectionTitle("Para onde foi?");if(categories.isEmpty())Text("Suas categorias aparecem aqui ao registrar despesas.",color=MaterialTheme.colorScheme.onSurfaceVariant)else{
                    Box(Modifier.fillMaxWidth().height(160.dp),contentAlignment=Alignment.Center){Canvas(Modifier.size(150.dp)){var start=-90f;categories.forEachIndexed{index,e->val sweep=(e.value.toDouble()/expense*360).toFloat();drawArc(chartColors[index%chartColors.size],start,sweep,false,Offset(12.dp.toPx(),12.dp.toPx()),Size(size.width-24.dp.toPx(),size.height-24.dp.toPx()),style=Stroke(20.dp.toPx(),cap=StrokeCap.Butt));start+=sweep}};Column(horizontalAlignment=Alignment.CenterHorizontally){Text("SAÍDAS",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${categories.size} categorias",style=MaterialTheme.typography.labelLarge)}}
                    categories.take(8).forEachIndexed{index,e->Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Circle,null,Modifier.size(8.dp),tint=chartColors[index%chartColors.size]);Spacer(Modifier.width(10.dp));Text(e.key,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(money(e.value),style=MaterialTheme.typography.titleSmall)};LinearProgressIndicator(progress={if(expense==0L)0f else e.value.toFloat()/expense},modifier=Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),color=chartColors[index%chartColors.size])}
                }}}
                item{StudioCard{SectionTitle("Ritmo do mês");CashflowChart(FinanceInsights.dailyFlow(items,month));Text("Fluxo acumulado por dia · ${month.lengthOfMonth()} dias",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                item{SectionTitle("Últimos lançamentos","Planilha"){mode="Planilha"}}
                if(data.isEmpty())item{EmptyCard("Seu controle começa aqui","Adicione receitas, despesas e contas. Os valores são seus, sem dados fictícios.")}
                items(data.sortedByDescending{it.date}.take(5),key={it.id}){TransactionRow(it,items,open)}
            }
            "Planilha"->{
                item{OutlinedTextField(query,{query=it},label={Text("Buscar lançamento ou categoria")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                item{Row(Modifier.horizontalScroll(rememberScrollState())){Choice("Tipo",listOf("Todos","Receitas","Despesas","Transferências"),kind,{kind=it});Choice("Status",listOf("Todos","Realizados","Previstos"),status,{status=it});Choice("Categoria",listOf("Todas")+data.map{it.value("category").ifBlank{"Sem categoria"}}.distinct(),category,{category=it});Choice("Ordem",listOf("Recentes","Antigos","Maior valor","Menor valor"),sort,{sort=it})}}
                item{Text("${shown.size} lançamentos · líquido ${money(Workspace.balance(shown))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                item{FinancialTable(shown,items,open)}
                item{TextButton(onClick={export.launch("veyra-planilha-$month.csv")}){Icon(Icons.Default.FileDownload,null);Text("Exportar seleção em CSV")};Text("Toque em uma linha para editar, parcelar ou marcar como pago. Deslize a planilha para ver todas as colunas.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            else->{
                val type=when(mode){"Contas"->"account";"Orçamentos"->"budget";"Cartões"->"card";"Assinaturas"->"subscription";else->"investment"}
                item{SectionTitle(mode,"Adicionar"){create(type)}}
                val entries=items.filter{it.type==type}
                if(entries.isEmpty())item{EmptyCard("Um lugar para seus ${mode.lowercase()}","Toque em Adicionar para começar.")}
                items(entries,key={it.id}){entry->Card(onClick={open(entry)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(type));Spacer(Modifier.width(12.dp));Text(entry.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);Icon(Icons.Default.ChevronRight,null)}
                    when(type){
                        "account"->{Text(money(Workspace.accountBalance(items,entry)),style=MaterialTheme.typography.headlineSmall);Text(entry.value("bank"),color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={create("transfer")}){Text("Transferir entre contas")}}
                        "budget"->{val used=categories.filter{it.key.equals(entry.value("category"),true)}.sumOf{it.value};val limit=entry.cents();Text("${entry.value("category")} · ${money(used)} / ${money(limit)}");LinearProgressIndicator(progress={if(limit>0)(used.toDouble()/limit).coerceIn(0.0,1.0).toFloat()else 0f},modifier=Modifier.fillMaxWidth(),color=if(used>limit)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);Text(if(used>limit)"Limite ultrapassado em ${money(used-limit)}"else "Disponível: ${money((limit-used).coerceAtLeast(0))}",style=MaterialTheme.typography.bodySmall)}
                        "card"->{val purchases=data.filter{it.type=="expense" && it.value("card")==entry.id}.sumOf{it.cents()};Text(money(purchases),style=MaterialTheme.typography.headlineSmall);Text("Compras registradas neste mês · limite ${money(entry.cents("limit"))}",style=MaterialTheme.typography.bodySmall);Text("Fecha dia ${entry.value("closing")} · vence dia ${entry.value("due")}",style=MaterialTheme.typography.bodySmall);Text("Resumo por mês de compra. Não calcula fatura por ciclo.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        "subscription"->{Text("${money(entry.cents())} / mês",style=MaterialTheme.typography.titleLarge);Text("Desde ${dateLabel(entry.date)} · ${if(entry.done)"Pausada"else "Ativa"}",style=MaterialTheme.typography.bodySmall)}
                        "investment"->{val invested=entry.cents();val current=entry.cents("current");Text(money(current),style=MaterialTheme.typography.headlineSmall);Text("Aporte: ${money(invested)} · variação: ${money(current-invested)}",style=MaterialTheme.typography.bodySmall);Text("Valores informados manualmente · ${entry.value("institution")}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }}}
                if(type=="subscription")item{Button(onClick=vm::recurring){Text("Gerar vencimentos até hoje")}}
            }
        }
    }
}
@Composable private fun TransactionRow(item:Item,all:List<Item>,open:(Item)->Unit){Card(onClick={open(item)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){IconBadge(moduleIcon(item.type),if(item.type=="income")MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,size=36);Column(Modifier.weight(1f)){Text(item.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${dateLabel(item.date)} · ${item.value("category").ifBlank{"Sem categoria"}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Column(horizontalAlignment=Alignment.End){Text((if(item.type=="income")"+ "else if(item.type=="expense")"− "else "")+money(item.cents()),style=MaterialTheme.typography.labelLarge,color=if(item.type=="income")MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface);Text(if(item.value("planned")=="Sim" && !item.done)"Previsto"else "Realizado",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
@Composable private fun FinancialTable(entries:List<Item>,all:List<Item>,open:(Item)->Unit){
    Column(Modifier.horizontalScroll(rememberScrollState()).width(760.dp)){
        Row(Modifier.padding(vertical=12.dp)){listOf("DATA" to 88,"DESCRIÇÃO" to 180,"CATEGORIA" to 120,"CONTA" to 120,"STATUS" to 100,"VALOR" to 135).forEach{(label,width)->Text(label,Modifier.width(width.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}}
        if(entries.isEmpty())Text("Nenhum lançamento com esses filtros.",Modifier.padding(vertical=20.dp))
        entries.forEach{item->Surface(onClick={open(item)},color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(vertical=15.dp),verticalAlignment=Alignment.CenterVertically){listOf(dateLabel(item.date) to 88,item.title to 180,item.value("category").ifBlank{"—"} to 120,(all.firstOrNull{it.id==item.value("account")}?.title ?: "—") to 120,(if(item.value("planned")=="Sim" && !item.done)"Previsto"else "Realizado") to 100,((if(item.type=="income")"+ "else if(item.type=="expense")"− "else "↔ ")+money(item.cents())) to 135).forEach{(text,width)->Text(text,Modifier.width(width.dp).padding(end=10.dp),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}}};HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)}
    }
}
@Composable private fun CashflowChart(values:List<Long>){val color=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(125.dp)){
        val min=minOf(0L,values.minOrNull() ?: 0L).toDouble()
        val max=maxOf(0L,values.maxOrNull() ?: 0L).toDouble()
        val range=(max-min).coerceAtLeast(1.0)
        val h=size.height-16.dp.toPx()
        val pad=8.dp.toPx()
        fun y(v:Double):Float { return pad+((max-v)/range*h).toFloat() }
        drawLine(grid,Offset(0f,y(0.0)),Offset(size.width,y(0.0)),1.dp.toPx())
        if(values.size>1){val path=Path();values.forEachIndexed{index,v->val x=size.width*index/(values.size-1);if(index==0)path.moveTo(x,y(v.toDouble()))else path.lineTo(x,y(v.toDouble()))};drawPath(path,color,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))}
    }
}
