package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.veyra.model.Item
import app.veyra.feature.finance.*
import java.time.LocalDate
import java.time.YearMonth

@Composable internal fun FinanceManagement(section:String,all:List<Item>,vm:VeyraViewModel,month:YearMonth,currency:String,hidden:Boolean,open:(Item)->Unit,edit:(Item)->Unit,create:(String)->Unit){
    val today=LocalDate.now()
    val type=when(section){"Contas"->"account";"Cartões"->"card";"Orçamentos"->"budget";"Metas"->"savings_goal";"Dívidas"->"debt";"Patrimônio"->"financial_asset";else->"recurring_rule"}
    val entries=all.filter{it.deletedAt==0L && FinancialDomain.currency(it)==currency && when(section){"Recorrências"->RecurrenceEngine.isRule(it);"Assinaturas"->RecurrenceEngine.isRule(it) && (it.type=="subscription" || it.value("category")=="Assinaturas");"Patrimônio"->it.type in setOf("financial_asset","investment");"Modelos e regras"->it.type in setOf("financial_template","financial_rule","financial_category");else->it.type==type}}
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        SectionTitle(section,"Adicionar"){
            if(section=="Recorrências" || section=="Assinaturas")edit(Item(type="expense",title="",fields=mapOf("currency" to currency,"frequency" to "MONTHLY","recurrence" to "yes","status" to "pending","category" to if(section=="Assinaturas")"Assinaturas"else"Outros")))
            else if(section=="Modelos e regras")create("financial_rule")else create(type)
        }
        if(section=="Patrimônio")StudioCard{
            Text("PATRIMÔNIO LÍQUIDO",style=MaterialTheme.typography.labelMedium);Text(financeMoney(FinanceEngine.netWorth(all,today,currency),currency,hidden),style=MaterialTheme.typography.headlineLarge)
            Text("Saldos + ativos − dívidas e faturas abertas.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={vm.saveNetWorth(currency)}){Text("Guardar fotografia do patrimônio")}
            all.filter{it.type=="networth_snapshot" && FinancialDomain.currency(it)==currency}.sortedByDescending{it.date}.take(12).forEach{Text("${dateLabel(it.date)} · ${financeMoney(it.value("netWorthMinor").toLongOrNull() ?: 0,currency,hidden)}")}
        }
        if(section=="Metas")StudioCard{
            var months by remember{mutableStateOf("6")};Choice("Reserva em meses",listOf("3","6","12","24"),months,{months=it})
            val recommended=FinanceEngine.emergencyReserve(all,today,months.toInt(),currency)
            Text("Reserva para $months meses: ${financeMoney(recommended,currency,hidden)}",style=MaterialTheme.typography.titleMedium)
            Text("Calculada com a média dos meses com gastos nos três meses anteriores. Não substitui planejamento profissional.",style=MaterialTheme.typography.bodySmall)
            if(recommended>0)TextButton(onClick={edit(Item(type="savings_goal",title="Reserva de emergência",fields=mapOf("currency" to currency,"amount" to FinancialDomain.decimal(recommended,currency),"amountMinor" to recommended.toString(),"saved" to "0","reserveMonths" to months,"goalType" to "Reserva de emergência")))}){Text("Criar meta da reserva")}
        }
        if(section=="Assinaturas"){
            val subscriptions=FinanceEngine.subscriptions(all,today,currency)
            StudioCard{Text("TOTAL DE ASSINATURAS",style=MaterialTheme.typography.labelMedium);Text("Mensal: ${financeMoney(subscriptions.fold(0L){s,i->Math.addExact(s,i.monthlyMinor)},currency,hidden)}");Text("Próximos 12 meses: ${financeMoney(subscriptions.fold(0L){s,i->Math.addExact(s,i.annualMinor)},currency,hidden)}")}
        }
        if(section=="Modelos e regras")Row{TextButton(onClick={create("financial_rule")}){Text("Nova regra")};TextButton(onClick={create("financial_category")}){Text("Nova categoria")}}
        if(entries.isEmpty())EmptyCard("Ainda não há registros","Toque em Adicionar para cadastrar. Os valores são calculados a partir dos seus dados.")
        entries.forEach{entry->StudioCard{
            Row(verticalAlignment=Alignment.CenterVertically){
                if(entry.type in setOf("account","card","financial_category")){
                    val icon=when(entry.value("icon")){"bank"->Icons.Default.AccountBalance;"cash"->Icons.Default.Payments;"savings"->Icons.Default.Savings;"wallet"->Icons.Default.AccountBalanceWallet;else->moduleIcon(entry.type)}
                    val tint=runCatching{Color(android.graphics.Color.parseColor(entry.value("color")))}.getOrDefault(MaterialTheme.colorScheme.primary)
                    IconBadge(icon,tint,size=40);Spacer(Modifier.width(12.dp))
                }
                Text(entry.title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={edit(entry)}){Text("Editar")}
            }
            when{
                entry.type=="account"->{
                    Text(financeMoney(FinanceEngine.accountBalance(all,entry,today,currency),currency,hidden),style=MaterialTheme.typography.headlineMedium)
                    Text("${entry.value("bank")} · ${entry.value("accountType").ifBlank{"Conta"}}",style=MaterialTheme.typography.bodySmall)
                    val expected=FinanceEngine.accountProjection(all,entry,today,today.plusDays(30))
                    Text("Previsto em 30 dias: ${financeMoney(expected,currency,hidden)}")
                    Row{TextButton(onClick={edit(Item(type="transfer",title="Transferência",fields=mapOf("currency" to currency,"account" to entry.id,"status" to "paid")))}){Text("Transferir")};TextButton(onClick={open(entry)}){Text("Histórico")}}
                }
                entry.type=="card"->FinanceCardPanel(entry,all,vm,currency,hidden,open)
                entry.type=="budget"->{val period=FinanceEngine.period(month,(vm.preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31));val p=FinanceEngine.budgets(all,period.start,period.end,currency).firstOrNull{it.budget.id==entry.id};if(p==null)Text("Este orçamento não se aplica ao período selecionado.")else{Text("${entry.value("category")} · ${financeMoney(p.usedMinor,currency,hidden)} / ${financeMoney(p.limitMinor,currency,hidden)}");LinearProgressIndicator(progress={(p.percent/100).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth());Text("${p.percent.toInt()}% utilizado${if(p.threshold>0)" · faixa de alerta ${p.threshold}%"else""}");Text("Restam ${financeMoney((p.limitMinor-p.usedMinor).coerceAtLeast(0),currency,hidden)}");if(p.usedMinor>p.limitMinor)Text("Orçamento excedido",color=MaterialTheme.colorScheme.error)}}
                entry.type=="savings_goal"->{val p=FinanceEngine.goals(all,today,currency).first{it.goal.id==entry.id};Text("${financeMoney(p.currentMinor,currency,hidden)} / ${financeMoney(p.targetMinor,currency,hidden)}");LinearProgressIndicator(progress={(p.percent/100).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth());Text("${p.percent.toInt()}% · faltam ${financeMoney(p.remainingMinor,currency,hidden)}");p.estimatedCompletion?.let{Text("Conclusão estimada: ${dateLabel(it.toString())}")};TextButton(onClick={open(entry)}){Text("Ver meta e histórico")}}
                entry.type=="debt"->FinanceDebtPanel(entry,all,vm,currency,hidden)
                RecurrenceEngine.isRule(entry)->{
                    Text("${financeMoney(FinancialDomain.amount(entry),currency,hidden)} · ${RecurrenceEngine.frequency(entry).label}")
                    Text("Desde ${dateLabel(entry.value("startDate").ifBlank{entry.date})} · ${if(entry.done || entry.value("paused")=="yes")"Pausada"else"Ativa"}",style=MaterialTheme.typography.bodySmall)
                    val next=RecurrenceEngine.dates(entry,today,today.plusYears(1)).firstOrNull();next?.let{Text("Próxima: ${dateLabel(it.toString())}")}
                    Row{TextButton(onClick={vm.commitFinance(listOf(RecurrenceEngine.pause(entry,!(entry.done || entry.value("paused")=="yes"))))}){Text(if(entry.done || entry.value("paused")=="yes")"Retomar"else"Pausar")};TextButton(onClick={open(entry)}){Text("Ver série")}}
                }
                entry.type=="financial_template"->{Text("${entry.value("category")} · ${financeMoney(FinancialDomain.amount(entry),currency,hidden)}");Button(onClick={edit(FinanceActions.useTemplate(entry))}){Text("Usar lançamento rápido")}}
                entry.type=="financial_rule"->{Text("Descrição contém “${entry.value("contains")}” → ${entry.value("category")}");TextButton(onClick={vm.commitFinance(listOf(entry.copy(fields=entry.fields+("enabled" to if(entry.value("enabled")=="no")"yes"else"no"))))}){Text(if(entry.value("enabled")=="no")"Ativar"else"Pausar")}}
                entry.type=="financial_category"->Text("${if(entry.value("kind")=="income")"Entradas"else"Gastos"}${entry.value("parent").takeIf{it.isNotBlank()}?.let{" · subcategoria de $it"}.orEmpty()}")
                else->{Text(financeMoney(FinancialDomain.amount(entry,"current"),currency,hidden),style=MaterialTheme.typography.headlineMedium);Text(entry.value("institution"));Text("Valor informado por você · aquisição ${financeMoney(FinancialDomain.amount(entry),currency,hidden)}",style=MaterialTheme.typography.bodySmall)}
            }
            TextButton(onClick={open(entry)}){Text("Detalhes e opções")}
        }}
        if(section=="Cartões")all.filter{it.type=="installment_plan" && it.deletedAt==0L}.forEach{plan->StudioCard{
            Text(plan.title,style=MaterialTheme.typography.titleMedium);Text("${plan.value("count")} parcelas · ${financeMoney(FinancialDomain.amount(plan),currency,hidden)}")
            var count by remember(plan.id){mutableStateOf("1")};FinanceField("Parcelas a antecipar",count,{count=it},true)
            Button(onClick={vm.advanceFinance(plan.id,count.toIntOrNull() ?: 0)},enabled=!vm.busy){Text("Antecipar vencimentos")}
        }}
    }
}

@Composable internal fun FinanceCardPanel(card:Item,all:List<Item>,vm:VeyraViewModel,currency:String,hidden:Boolean,open:(Item)->Unit){
    val today=LocalDate.now();val used=FinanceEngine.cardUsedLimit(all,card,today);val available=FinanceEngine.cardAvailableLimit(all,card,today)
    Text("${card.value("brand")} · •••• ${card.value("lastFour")}",style=MaterialTheme.typography.bodySmall)
    Text("Disponível: ${financeMoney(available,currency,hidden)}",style=MaterialTheme.typography.headlineSmall)
    Text("Usado: ${financeMoney(used,currency,hidden)} · limite ${financeMoney(FinancialDomain.amount(card,"limit"),currency,hidden)}")
    Text("Fecha dia ${card.value("closing")} · vence dia ${card.value("due")}",style=MaterialTheme.typography.bodySmall)
    val accounts=all.filter{it.type=="account" && FinancialDomain.currency(it)==currency && it.deletedAt==0L}
    var paymentAccount by remember(card.id){mutableStateOf(card.value("account").ifBlank{accounts.firstOrNull()?.id.orEmpty()})}
    Choice("Pagar pela conta",listOf("")+accounts.map{it.id},paymentAccount,{paymentAccount=it}){id->accounts.firstOrNull{it.id==id}?.title ?: "Selecionar"}
    val invoices=FinanceEngine.cardInvoices(all,card,today.minusMonths(12),today.plusMonths(2))
    if(invoices.isEmpty())Text("As compras aparecem nas faturas pelo fechamento do cartão.")
    invoices.take(8).forEach{invoice->
        HorizontalDivider();Text("Fatura · ${dateLabel(invoice.dueDate.toString())}",style=MaterialTheme.typography.titleMedium)
        Text("Total ${financeMoney(invoice.totalMinor,currency,hidden)} · em aberto ${financeMoney(invoice.outstandingMinor,currency,hidden)}")
        if(invoice.outstandingMinor>0)Button(onClick={vm.payInvoice(invoice,paymentAccount)},enabled=paymentAccount.isNotBlank() && !vm.busy){Text("Registrar pagamento da fatura")}
        invoice.purchases.filter{it.type=="expense"}.take(5).forEach{TextButton(onClick={open(it)}){Text("${it.title} · ${financeMoney(FinancialDomain.amount(it),currency,hidden)}")}}
    }
}

@Composable private fun FinanceDebtPanel(debt:Item,all:List<Item>,vm:VeyraViewModel,currency:String,hidden:Boolean){
    val total=FinancialDomain.amount(debt);val paid=FinancialDomain.amount(debt,"paid");val remaining=(total-paid).coerceAtLeast(0)
    Text("${debt.value("direction")} · ${debt.value("person")}");Text("Saldo: ${financeMoney(remaining,currency,hidden)}",style=MaterialTheme.typography.headlineSmall)
    LinearProgressIndicator(progress={if(total>0)paid.toFloat()/total else 0f},modifier=Modifier.fillMaxWidth());Text("Quitado: ${financeMoney(paid,currency,hidden)}")
    var amount by remember(debt.id){mutableStateOf("")};var account by remember(debt.id){mutableStateOf("")}
    FinanceSection("Registrar quitação"){
        FinanceField("Valor pago / recebido",amount,{amount=it},true);Choice("Conta",listOf("")+all.filter{it.type=="account" && FinancialDomain.currency(it)==currency}.map{it.id},account,{account=it}){id->all.firstOrNull{it.id==id}?.title ?: "Selecionar"}
        Button(onClick={vm.payDebt(debt,amount,account){amount=""}},enabled=account.isNotBlank() && amount.isNotBlank() && !vm.busy){Text("Registrar pagamento")}
    }
    if(debt.value("interest").isNotBlank())Text("Juros registrados: ${debt.value("interest")}% ao mês · não altera o saldo sem um lançamento.",style=MaterialTheme.typography.bodySmall)
}
