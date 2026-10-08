package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.veyra.data.AuditEvent
import app.veyra.data.FinanceQuery
import app.veyra.model.Item
import app.veyra.feature.finance.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@Composable internal fun FinanceSettingsPanel(vm:VeyraViewModel,account:()->Unit){
    val context=LocalContext.current;val secure=context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        StudioCard{SectionTitle("Preferências financeiras");Choice("Moeda padrão",listOf("BRL","USD","EUR","GBP"),vm.preferences["financeCurrency"] ?: "BRL",{vm.pref("financeCurrency",it)});Text("Moedas são acompanhadas separadamente; não há conversão cambial automática.",style=MaterialTheme.typography.bodySmall);Choice("Primeiro dia do mês",(1..31).map(Int::toString),vm.preferences["financialDay"] ?: "1",{vm.pref("financialDay",it)})}
        StudioCard{SectionTitle("Privacidade e proteção");FinancePreferenceSwitch("Ocultar valores",vm.preferences["financeHidden"]=="Sim"){vm.pref("financeHidden",if(it)"Sim"else"Não")};FinancePreferenceSwitch("Proteger financeiro pelo bloqueio do aparelho",vm.preferences["financeLock"]=="Sim",secure){vm.pref("financeLock",if(it)"Sim"else"Não")};if(!secure)Text("Configure o bloqueio de tela do Android para ativar essa proteção.",style=MaterialTheme.typography.bodySmall);FinancePreferenceSwitch("Exibir resumo financeiro no widget",vm.preferences["widgetFinance"]=="Sim"){vm.pref("widgetFinance",if(it)"Sim"else"Não");AndroidJobs.updateWidget(context)};Text("O resumo no widget fica oculto por padrão. Ative apenas se quiser expor esses dados na tela inicial.",style=MaterialTheme.typography.bodySmall)}
        StudioCard{SectionTitle("Alertas e lembretes");FinancePreferenceSwitch("Notificações financeiras",vm.preferences["financeNotifications"]=="Sim"){vm.pref("financeNotifications",if(it)"Sim"else"Não")};FinancePreferenceSwitch("Incluir valores nas notificações",vm.preferences["financeNotificationValues"]=="Sim"){vm.pref("financeNotificationValues",if(it)"Sim"else"Não")};Text("Até três alertas por dia, sem repetir a mesma faixa de orçamento. O Android pode atrasar os lembretes para economizar bateria.",style=MaterialTheme.typography.bodySmall)}
        OutlinedButton(onClick=account,modifier=Modifier.fillMaxWidth()){Text("Conta, sincronização, dados e backup")}
    }
}

@Composable private fun FinancePreferenceSwitch(label:String,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,Modifier.weight(1f).padding(top=12.dp));Switch(checked,change,enabled=enabled)}}

@Composable internal fun FinanceReportsPanel(all:List<Item>,summary:FinancialSummary,month:YearMonth,currency:String,hidden:Boolean,vm:VeyraViewModel){
    val close=FinanceEngine.monthClose(all,month,LocalDate.now(),currency,(vm.preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31))
    val health=FinanceEngine.health(all,month,LocalDate.now(),currency,(vm.preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31))
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        StudioCard{SectionTitle("Entradas e gastos ao longo dos meses");FinanceMonthlyChart(all,month,(vm.preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31),currency,hidden)}
        StudioCard{SectionTitle("Fechamento do mês");Text(month.toString(),style=MaterialTheme.typography.titleMedium);listOf("Você recebeu" to summary.incomeMinor,"Você gastou" to summary.expenseMinor,"Economizou" to summary.savingsMinor,"Patrimônio" to summary.netWorthMinor).forEach{(label,value)->Row{Text(label,Modifier.weight(1f));Text(financeMoney(value,currency,hidden))}};Text("Taxa de economia: ${summary.savingsRate?.let{"%.1f%%".format(Brazilian,it)} ?: "Sem receita registrada"}");close.largestCategory?.let{Text("Maior categoria: $it · ${financeMoney(close.largestCategoryMinor,currency,hidden)}")};close.largestExpense?.let{Text("Maior gasto: ${it.title} · ${financeMoney(FinancialDomain.amount(it),currency,hidden)}")};Button(onClick={vm.saveMonthClose(month,currency)},enabled=!vm.busy){Text("Guardar fechamento")}}
        StudioCard{SectionTitle("Indicador financeiro");Text("${health.score?.let{"$it/100 · "}.orEmpty()}${health.label}",style=MaterialTheme.typography.titleLarge);health.factors.forEach{factor->Text("${factor.label}: ${factor.points}/${factor.maximum}",style=MaterialTheme.typography.titleMedium);if(!hidden)Text(factor.explanation,style=MaterialTheme.typography.bodySmall)};Text("Indicador descritivo dos registros disponíveis. Não é aconselhamento financeiro profissional.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        StudioCard{SectionTitle("Fechamentos guardados");val snapshots=all.filter{it.type=="month_close" && FinancialDomain.currency(it)==currency}.sortedByDescending{it.date};if(snapshots.isEmpty())Text("Guarde um fechamento para acompanhar a evolução.");snapshots.take(12).forEach{Text("${it.title} · economia ${financeMoney(it.value("savingsMinor").toLongOrNull() ?: 0,currency,hidden)} · patrimônio ${financeMoney(it.value("netWorthMinor").toLongOrNull() ?: 0,currency,hidden)}")}}
    }
}

@Composable internal fun FinanceTrashPanel(vm:VeyraViewModel,currency:String,hidden:Boolean){
    val all by vm.financeItems.collectAsState()
    var displayLimit by remember{mutableIntStateOf(100)}
    val trashed by produceState<List<Item>>(emptyList(),all,vm.busy,displayLimit){value=vm.financeTrash(displayLimit)}
    var purging by remember{mutableStateOf<Item?>(null)}
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        SectionTitle("Lixeira financeira");Text("Registros excluídos não afetam saldos. Eles ficam disponíveis para restauração até você excluir definitivamente.",style=MaterialTheme.typography.bodySmall)
        if(trashed.isEmpty())EmptyCard("Lixeira vazia","Seus lançamentos excluídos aparecerão aqui.")
        trashed.forEach{item->StudioCard{Text(item.title,style=MaterialTheme.typography.titleMedium);Text(financeMoney(FinancialDomain.amount(item),currency,hidden));Row{TextButton(onClick={vm.restoreFinance(item)}){Text("Restaurar")};TextButton(onClick={purging=item}){Text("Excluir definitivamente")}}}}
        if(trashed.size>=displayLimit)OutlinedButton(onClick={displayLimit+=100},modifier=Modifier.fillMaxWidth()){Text("Carregar mais 100")}
    }
    purging?.let{item->AlertDialog(onDismissRequest={purging=null},title={Text("Excluir definitivamente?")},text={Text("${item.title}. A operação mantém um registro mínimo de exclusão para impedir que a sincronização ressuscite o lançamento.")},confirmButton={TextButton(onClick={vm.purgeFinance(item);purging=null}){Text("Excluir")}},dismissButton={TextButton(onClick={purging=null}){Text("Cancelar")}})}
}
