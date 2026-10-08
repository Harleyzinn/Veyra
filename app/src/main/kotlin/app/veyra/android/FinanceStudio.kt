package app.veyra.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import app.veyra.model.Item
import app.veyra.feature.finance.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal data class FinanceDashboardData(val summary:FinancialSummary,val projection:CashProjection,val transactions:List<Item>,val obligations:List<Item>,val health:FinancialHealth,val alerts:List<FinancialAlert>,val insights:List<FinancialInsight>)
private val financeSections=listOf("Resumo","Movimentações","A pagar","A receber","Fluxo futuro","Simulador","Conferência","Calendário","Contas","Cartões","Recorrências","Orçamentos","Metas","Assinaturas","Dívidas","Patrimônio","Relatórios","Alertas","Modelos e regras","Configurações","Lixeira")

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FinanceScreen(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,create:(String)->Unit){
    val all by vm.financeItems.collectAsState()
    val currency=vm.preferences["financeCurrency"] ?: "BRL";val hidden=vm.preferences["financeHidden"]=="Sim";val financialDay=(vm.preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31)
    var section by remember{mutableStateOf("Resumo")};var month by remember{mutableStateOf(YearMonth.now())};var horizon by remember{mutableStateOf("30 dias")}
    var reportBasis by remember{mutableStateOf(ReportBasis.PURCHASE)}
    var query by remember{mutableStateOf("")};var kind by remember{mutableStateOf("")};var status by remember{mutableStateOf("")};var category by remember{mutableStateOf("")};var account by remember{mutableStateOf("")};var card by remember{mutableStateOf("")}
    var minValue by remember{mutableStateOf("")};var maxValue by remember{mutableStateOf("")};var recurrent by remember{mutableStateOf("Todos")};var installments by remember{mutableStateOf("Todos")}
    val period=FinanceEngine.period(month,financialDay)
    var fromText by remember(month,financialDay){mutableStateOf(period.start.toString())};var throughText by remember(month,financialDay){mutableStateOf(period.end.toString())}
    var displayLimit by remember(section,month,query,kind,status,category,account,card,minValue,maxValue,recurrent,installments,fromText,throughText){mutableIntStateOf(100)}
    var editor by remember{mutableStateOf<Item?>(null)};var selected by remember{mutableStateOf<Item?>(null)};var deleting by remember{mutableStateOf<Item?>(null)};var seriesTarget by remember{mutableStateOf<Item?>(null)};var seriesDelete by remember{mutableStateOf(false)};var editScope by remember{mutableStateOf<SeriesScope?>(null)}
    var quickActions by remember{mutableStateOf(false)};var cloudOpen by remember{mutableStateOf(false)}
    val today=LocalDate.now();val from=runCatching{LocalDate.parse(fromText)}.getOrDefault(period.start);val through=runCatching{LocalDate.parse(throughText)}.getOrDefault(period.end)
    val validRange=runCatching{LocalDate.parse(fromText) to LocalDate.parse(throughText)}.getOrNull()?.let{(a,b)->!b.isBefore(a) && !b.isAfter(a.plusYears(10))} ?: false
    val projectedThrough=when(horizon){"Fim do mês"->maxOf(today,month.atEndOfMonth());"Fim do ano"->today.withMonth(12).withDayOfMonth(31);else->today.plusDays(horizon.substringBefore(' ').toLongOrNull() ?: 30)}
    LaunchedEffect(month,horizon,from,through){vm.loadFinance(minOf(today.minusMonths(3).withDayOfMonth(1),month.minusMonths(3).atDay(1),from),maxOf(today.plusYears(1),month.plusMonths(1).atEndOfMonth(),through,projectedThrough))}
    val dashboard by produceState<Result<FinanceDashboardData>?>(null,all,month,currency,financialDay,projectedThrough,from,through){value=withContext(Dispatchers.Default){runCatching{val projection=FinanceEngine.projection(all,today,projectedThrough,currency);FinanceDashboardData(FinanceEngine.summary(all,month,today,currency,financialDay),projection,FinanceEngine.transactions(all,from,minOf(maxOf(from,through),from.plusYears(10)),currency),FinanceEngine.transactions(all,projection.virtualLookbackStart,maxOf(today,through),currency),FinanceEngine.health(all,month,today,currency,financialDay),FinanceEngine.alerts(all,today,currency,financialDay),FinanceEngine.insights(all,month,today,currency,financialDay))}}}
    val data=dashboard?.getOrNull()
    val invoiceEntries=remember(all,currency,through){financeInvoiceEntries(all,today.minusYears(1),maxOf(today,through),currency)}
    val records=when(section){"A pagar"->(data?.obligations.orEmpty()+all.filter{it.type=="expense" && FinancialDomain.active(it) && FinancialDomain.currency(it)==currency && FinancialDomain.status(it)==TransactionStatus.OVERDUE}+invoiceEntries).distinctBy{it.id}.filter{it.type=="expense" && !FinancialDomain.settled(it) && (it.value("card").isBlank() || it.value("cardInvoice")=="yes")};"A receber"->(data?.obligations.orEmpty()+all.filter{it.type=="income" && FinancialDomain.active(it) && FinancialDomain.currency(it)==currency && FinancialDomain.status(it)==TransactionStatus.OVERDUE}).distinctBy{it.id}.filter{it.type=="income" && !FinancialDomain.settled(it)};else->data?.transactions.orEmpty()}
    val minMinor=if(minValue.isBlank())null else runCatching{FinancialDomain.parseMinor(minValue,currency)}.getOrNull()
    val maxMinor=if(maxValue.isBlank())null else runCatching{FinancialDomain.parseMinor(maxValue,currency)}.getOrNull()
    val validValues=(minValue.isBlank() || minMinor!=null && minMinor>=0) && (maxValue.isBlank() || maxMinor!=null && maxMinor>=0) && (minMinor==null || maxMinor==null || minMinor<=maxMinor)
    val filtered=if(validRange && validValues)FinanceEngine.search(FinanceEngine.filter(records,FinancialFilter(type=kind,category=category,account=account,card=card,minMinor=minMinor,maxMinor=maxMinor,status=TransactionStatus.entries.firstOrNull{it.name==status},recurring=if(recurrent=="Todos")null else recurrent=="Sim",installment=if(installments=="Todos")null else installments=="Sim")),query,all)else emptyList()
    val report=remember(all,from,through,currency,reportBasis,account,card,category,query,validRange,section){if(section=="Relatórios" && validRange)runCatching{FinanceReports.select(all,from,through,currency,reportBasis,FinancialFilter(account=account,card=card,category=category),query,today)}.getOrNull()else null}
    val reportEntries=if(section=="Relatórios")report?.entries.orEmpty()else if(section in listOf("Movimentações","A pagar","A receber"))filtered else data?.transactions.orEmpty()
    fun launchNew(type:String){editScope=null;editor=Item(type=type,title="",fields=mapOf("currency" to currency,"status" to if(type=="income")"received"else"paid"))}
    fun show(item:Item){val target=if(item.value("cardInvoice")=="yes")all.firstOrNull{it.id==item.value("card")} ?: item else item;vm.loadFullItem(target){selected=it}}
    val exportCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null && data!=null)vm.operation{vm.getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(FinanceReportWriter.csv(reportEntries,all))} ?: error("Arquivo indisponível")}}
    val exportPdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->if(uri!=null && data!=null)vm.operation{val selectedReport=if(section=="Relatórios")report ?: error("Confira o período do relatório")else FinanceReports.totals(reportEntries,today);FinanceReportWriter.pdf(vm.getApplication(),uri,"$from a $through · ${reportEntries.size} registros selecionados",listOf(if(section=="Relatórios")reportBasis.label else "Gastos registrados da seleção","Entradas: ${financeMoney(selectedReport.incomeMinor,currency)}","Gastos: ${financeMoney(selectedReport.expenseMinor,currency)}","Resultado: ${financeMoney(selectedReport.netMinor,currency)}"),reportEntries,section=="Relatórios" && reportBasis==ReportBasis.CASH)}}
    if(cloudOpen){CloudAccountScreen(vm.cloud){cloudOpen=false};return}
    Box(Modifier.fillMaxSize()){
        LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            item{PageHeading("SUA CENTRAL FINANCEIRA","Dinheiro com clareza.","Registre rápido. Planeje com controle.")}
            item{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,"Mês anterior")};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Brazilian)).replaceFirstChar{it.titlecase(Brazilian)},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconButton(onClick={month=month.plusMonths(1)}){Icon(Icons.Default.ChevronRight,"Próximo mês")};IconButton(onClick={vm.pref("financeHidden",if(hidden)"Não"else"Sim")}){Icon(if(hidden)Icons.Default.VisibilityOff else Icons.Default.Visibility,if(hidden)"Mostrar valores"else"Ocultar valores")}}}
            item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={launchNew("income")},modifier=Modifier.weight(1f).height(52.dp)){Text("+ Entrada")};FilledTonalButton(onClick={launchNew("expense")},modifier=Modifier.weight(1f).height(52.dp)){Text("− Gasto")}}}
            item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Resumo","Movimentações","A pagar","A receber").forEach{FilterChip(section==it,{section=it},label={Text(it)})}};Choice("Explorar",financeSections,section,{section=it})}
            if(vm.busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(dashboard==null)item{StudioCard{Text("Carregando seus registros…");LinearProgressIndicator(Modifier.fillMaxWidth())}}
            dashboard?.exceptionOrNull()?.let{failure->item{EmptyCard("Não foi possível calcular",failure.message ?: "Confira os valores registrados e tente novamente.")}}
            if(data!=null)when(section){
                "Resumo"->{
                    item{FinanceOverview(data.summary,data.health,currency,hidden)}
                    item{StudioCard{SectionTitle("Vencimentos em foco");val upcoming=FinanceAgenda.upcoming(data.obligations+all.filter{it.type=="expense"}+invoiceEntries,today,currency);if(upcoming.isEmpty())Text("Nenhuma conta ou fatura nos próximos sete dias.")else{Text("${upcoming.size} ${if(upcoming.size==1)"pendência"else"pendências"} · ${financeMoney(upcoming.fold(0L){sum,i->Math.addExact(sum,FinancialDomain.amount(i))},currency,hidden)}",style=MaterialTheme.typography.titleMedium);upcoming.take(3).forEach{FinanceTransactionRow(it,currency,hidden,::show)}};TextButton(onClick={section="A pagar"}){Text("Ver contas e faturas")}}}
                    item{StudioCard{SectionTitle("O que vem pela frente");Text(financeMoney(data.projection.projectedBalance,currency,hidden),style=MaterialTheme.typography.headlineMedium);Text("Em $horizon · entradas menos vencimentos",style=MaterialTheme.typography.bodySmall);Text("A receber: ${financeMoney(data.projection.expectedIncome,currency,hidden)}");Text("A pagar: ${financeMoney(data.projection.expectedExpense,currency,hidden)}");data.projection.firstNegativeDate?.let{Text("Atenção: saldo negativo previsto em ${dateLabel(it.toString())}.",color=MaterialTheme.colorScheme.error)};TextButton(onClick={section="Fluxo futuro"}){Text("Explorar fluxo de caixa")}}}
                    item{StudioCard{SectionTitle("Gastos por categoria");val categories=data.transactions.filter{FinanceEngine.recognizedExpense(it,today)}.groupBy{it.value("category").ifBlank{"Sem categoria"}}.map{(name,rows)->name to rows.fold(0L){sum,i->Math.addExact(sum,FinancialDomain.amount(i))}}.sortedByDescending{it.second};FinanceCategoryChart(categories,currency,hidden)}}
                    if(data.insights.isNotEmpty())item{StudioCard{SectionTitle("Insights dos seus registros");if(hidden)Text("Insights ocultos pelo modo privacidade.")else data.insights.take(5).forEach{Text(it.text)}}}
                    item{SectionTitle("Últimos lançamentos","Ver todos"){section="Movimentações"}}
                    if(data.transactions.isEmpty())item{EmptyCard("Comece com uma entrada ou gasto","Seu painel é calculado com os seus registros. Não há exemplos misturados aos seus dados.")}
                    items(data.transactions.take(5),key={it.id}){FinanceTransactionRow(it,currency,hidden,::show)}
                }
                "Movimentações","A pagar","A receber"->{
                    item{FinanceField("Buscar descrição, valor, tag, conta ou categoria",query,{query=it})}
                    item{FinanceSection("Filtros de período e detalhes"){
                        FinanceField("De • AAAA-MM-DD",fromText,{fromText=it});FinanceField("Até • AAAA-MM-DD",throughText,{throughText=it})
                        if(!validRange)Text("Use datas válidas em ordem crescente, com intervalo de até dez anos.",color=MaterialTheme.colorScheme.error)
                        Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Hoje" to today,"Semana" to today.minusDays(6),"Mês" to month.atDay(1),"Ano" to today.withDayOfYear(1)).forEach{(label,date)->TextButton(onClick={fromText=date.toString();throughText=if(label=="Mês")month.atEndOfMonth().toString()else today.toString()}){Text(label)}}}
                        Choice("Tipo",listOf("","income","expense","transfer"),kind,{kind=it}){when(it){"income"->"Entrada";"expense"->"Gasto";"transfer"->"Transferência";else->"Todos"}}
                        Choice("Status",listOf("")+TransactionStatus.entries.map{it.name},status,{status=it}){if(it.isBlank())"Todos"else financeStatusLabel(it)}
                        Choice("Categoria",listOf("")+data.transactions.map{it.value("category")}.filter{it.isNotBlank()}.distinct(),category,{category=it}){it.ifBlank{"Todas"}}
                        Choice("Conta",listOf("")+all.filter{it.type=="account"}.map{it.id},account,{account=it}){id->all.firstOrNull{it.id==id}?.title ?: "Todas"}
                        Choice("Cartão",listOf("")+all.filter{it.type=="card"}.map{it.id},card,{card=it}){id->all.firstOrNull{it.id==id}?.title ?: "Todos"}
                        FinanceField("Valor mínimo",minValue,{minValue=it},true);FinanceField("Valor máximo",maxValue,{maxValue=it},true)
                        if(!validValues)Text("Informe valores válidos, sem negativos, com mínimo menor ou igual ao máximo.",color=MaterialTheme.colorScheme.error)
                        Choice("Recorrência",listOf("Todos","Sim","Não"),recurrent,{recurrent=it});Choice("Parcelado",listOf("Todos","Sim","Não"),installments,{installments=it})
                    }}
                    item{Text("${filtered.size} movimentações · $from a $through",style=MaterialTheme.typography.bodySmall);if(section!="Movimentações")Text("Inclui vencimentos anteriores desde ${dateLabel(data.projection.virtualLookbackStart.toString())}.",style=MaterialTheme.typography.bodySmall);Row{TextButton(onClick={exportCsv.launch("veyra-financas-$month.csv")},enabled=validRange && validValues){Text("Exportar CSV")};TextButton(onClick={exportPdf.launch("veyra-financas-$month.pdf")},enabled=validRange && validValues){Text("Relatório PDF")}}}
                    if(filtered.isEmpty())item{EmptyCard("Nenhum lançamento encontrado","Altere os filtros ou adicione uma movimentação.")}
                    items(filtered.take(displayLimit),key={it.id}){FinanceTransactionRow(it,currency,hidden,::show)}
                    if(filtered.size>displayLimit)item{OutlinedButton(onClick={displayLimit+=100},modifier=Modifier.fillMaxWidth()){Text("Carregar mais 100")}}
                }
                "Fluxo futuro"->item{StudioCard{SectionTitle("Seu saldo ao longo do tempo");Choice("Horizonte",listOf("7 dias","15 dias","30 dias","60 dias","90 dias","Fim do mês","Fim do ano"),horizon,{horizon=it});FinanceFlowChart(data.projection.points,currency,hidden);Text("Previsto: ${financeMoney(data.projection.projectedBalance,currency,hidden)}");data.projection.firstNegativeDate?.let{Text("Saldo abaixo de zero em ${dateLabel(it.toString())}",color=MaterialTheme.colorScheme.error)}}}
                "Calendário"->item{StudioCard{SectionTitle("Calendário financeiro");val calendarEntries=FinanceEngine.transactions(all,month.atDay(1).minusMonths(1),month.atEndOfMonth().plusMonths(1),currency).filter{it.value("card").isBlank() && FinancialDomain.dueDate(it) in month.atDay(1)..month.atEndOfMonth()}+financeInvoiceEntries(all,month.atDay(1),month.atEndOfMonth(),currency);FinanceCalendar(month,calendarEntries,currency,hidden,::show)}}
                "Simulador"->item{FinancePlanningPanel(data.projection,currency,hidden,horizon,{horizon=it})}
                "Conferência"->item{FinanceReviewPanel(all,period,currency,hidden,::show)}
                "Contas","Cartões","Recorrências","Orçamentos","Metas","Assinaturas","Dívidas","Patrimônio","Modelos e regras"->item{FinanceManagement(section,all,vm,month,currency,hidden,::show,{editScope=null;editor=it},::launchNew)}
                "Relatórios"->{item{FinanceReportPanel(all,report,reportBasis,{reportBasis=it},fromText,throughText,{fromText=it},{throughText=it},account,card,category,query,{account=it},{card=it},{category=it},{query=it},currency,hidden,{exportCsv.launch("veyra-$month.csv")},{exportPdf.launch("veyra-$month.pdf")},{account="";card="";category="";query="";fromText=period.start.toString();throughText=period.end.toString()},::show)};item{FinanceReportsPanel(all,data.summary,month,currency,hidden,vm)}}
                "Alertas"->{if(data.alerts.isEmpty())item{EmptyCard("Tudo em ordem nos registros","Não há alertas financeiros para os dados disponíveis.")};items(data.alerts,key={it.id}){alert->StudioCard{Text(alert.priority.label.uppercase(),style=MaterialTheme.typography.labelMedium,color=if(alert.priority==AlertPriority.URGENT)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);Text(alert.title,style=MaterialTheme.typography.titleMedium);if(!hidden)Text(alert.description);all.firstOrNull{it.id==alert.itemId}?.let{target->TextButton(onClick={show(target)}){Text("Ver registro")}}}}}
                "Configurações"->item{FinanceSettingsPanel(vm,{cloudOpen=true})}
                "Lixeira"->item{FinanceTrashPanel(vm,currency,hidden)}
            }
        }
        FloatingActionButton(onClick={quickActions=true},modifier=Modifier.align(Alignment.BottomEnd).padding(18.dp),containerColor=MaterialTheme.colorScheme.primary){Icon(Icons.Default.Add,"Ações rápidas financeiras")}
    }
    if(quickActions)ModalBottomSheet(onDismissRequest={quickActions=false}){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Registrar agora",style=MaterialTheme.typography.headlineSmall);listOf("income" to "Entrada","expense" to "Gasto","transfer" to "Transferência","account" to "Conta ou carteira").forEach{(type,label)->FilledTonalButton(onClick={quickActions=false;launchNew(type)},modifier=Modifier.fillMaxWidth()){Text(label)}};Spacer(Modifier.height(24.dp))}}
    selected?.let{record->FinanceDetail(record,all,vm,currency,hidden,{selected=null},{target->selected=null;if(target.value("source").isNotBlank() && all.any{it.id==target.value("source") && RecurrenceEngine.isRule(it)}){seriesTarget=target;seriesDelete=false}else{editScope=null;editor=target}},{target->selected=null;if(target.value("source").isNotBlank() && all.any{it.id==target.value("source") && RecurrenceEngine.isRule(it)}){seriesTarget=target;seriesDelete=true}else deleting=target})}
    editor?.let{draft->if(draft.type in setOf("income","expense","transfer","recurring_rule","subscription"))FinanceEditor(draft,all,vm,{editor=null;editScope=null},submit=editScope?.let{scope->{changed,_,done->vm.changeFinanceSeries(draft.value("source"),LocalDate.parse(draft.value("occurrenceDate").ifBlank{draft.date}),scope,changed,done)}})else FinanceEntityEditor(draft,all,vm,{editor=null})}
    deleting?.let{target->AlertDialog(onDismissRequest={deleting=null},title={Text("Mover para a lixeira?")},text={Text(target.title)},confirmButton={TextButton(onClick={vm.delete(target);deleting=null}){Text("Mover")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancelar")}})}
    seriesTarget?.let{target->AlertDialog(onDismissRequest={seriesTarget=null},title={Text(if(seriesDelete)"Excluir recorrência"else"Editar recorrência")},text={Column{Text(target.title);Text("As alterações da série preservam pagamentos já registrados.",style=MaterialTheme.typography.bodySmall);SeriesScope.entries.forEach{scope->TextButton(onClick={if(seriesDelete){vm.changeFinanceSeries(target.value("source"),LocalDate.parse(target.value("occurrenceDate").ifBlank{target.date}),scope)}else{editScope=scope;editor=target};seriesTarget=null}){Text(scope.label)}}}},confirmButton={TextButton(onClick={seriesTarget=null}){Text("Cancelar")}})}
}

private fun financeInvoiceEntries(all:List<Item>,from:LocalDate,through:LocalDate,currency:String):List<Item> = all.filter{it.type=="card" && it.deletedAt==0L && FinancialDomain.currency(it)==currency}.flatMap{card->
    FinanceEngine.cardInvoices(all,card,from,through).filter{it.outstandingMinor>0}.map{invoice->Item(id=invoice.id,type="expense",title="Fatura • ${card.title}",date=invoice.dueDate.toString(),fields=mapOf("currency" to currency,"amountMinor" to invoice.outstandingMinor.toString(),"status" to "pending","dueDate" to invoice.dueDate.toString(),"category" to "Cartão","card" to card.id,"cardInvoice" to "yes","paymentType" to "card_payment"))}
}

@Composable private fun FinanceOverview(s:FinancialSummary,health:FinancialHealth,currency:String,hidden:Boolean){StudioCard{
    Text("SALDO ATUAL",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(financeMoney(s.currentBalance,currency,hidden),style=MaterialTheme.typography.displaySmall);Text("Contas + dinheiro realizado até hoje",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);HorizontalDivider()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Column(Modifier.weight(1f)){Text("↙ Entradas do mês",style=MaterialTheme.typography.labelMedium);Text(financeMoney(s.incomeMinor,currency,hidden),style=MaterialTheme.typography.titleLarge)};Column(Modifier.weight(1f)){Text("↗ Gastos do mês",style=MaterialTheme.typography.labelMedium);Text(financeMoney(s.expenseMinor,currency,hidden),style=MaterialTheme.typography.titleLarge)}}
    s.expenseChangePercent?.let{Text("${if(it>0)"↑"else"↓"} ${"%.1f".format(Brazilian,kotlin.math.abs(it))}% nos gastos versus o período anterior",style=MaterialTheme.typography.bodySmall)}
    Text("Economia: ${financeMoney(s.savingsMinor,currency,hidden)}${s.savingsRate?.let{" · ${"%.1f".format(Brazilian,it)}%"}.orEmpty()}")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Faturas",style=MaterialTheme.typography.labelMedium);Text(financeMoney(s.cardInvoicesMinor,currency,hidden))};Column{Text("Patrimônio líquido",style=MaterialTheme.typography.labelMedium);Text(financeMoney(s.netWorthMinor,currency,hidden))}}
    Text("Orçamento: ${financeMoney(s.budgetUsedMinor,currency,hidden)} / ${financeMoney(s.budgetLimitMinor,currency,hidden)}",style=MaterialTheme.typography.bodySmall);Text("Saúde financeira: ${health.score?.let{"$it/100 · "}.orEmpty()}${health.label}",style=MaterialTheme.typography.labelLarge)
}}
