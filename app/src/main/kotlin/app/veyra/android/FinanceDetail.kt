package app.veyra.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.data.AuditEvent
import app.veyra.model.Item
import app.veyra.feature.finance.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun FinanceDetail(item:Item,all:List<Item>,vm:VeyraViewModel,currency:String,hidden:Boolean,dismiss:()->Unit,edit:(Item)->Unit,delete:(Item)->Unit){
    var history by remember(item.id){mutableStateOf(emptyList<AuditEvent>())}
    LaunchedEffect(item.id){vm.financialHistory(item.id){history=it}}
    val download=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(item.value("mime").ifBlank{"application/octet-stream"})){uri->uri?.let{vm.attachmentTo(it,item)}}
    ModalBottomSheet(onDismissRequest=dismiss){LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text(item.title,style=MaterialTheme.typography.headlineMedium);if(item.type in FinancialDomain.transactionTypes)Text("${FinancialDomain.status(item).label} · ${dateLabel(item.date)}",color=MaterialTheme.colorScheme.primary);if(item.value("amount").isNotBlank() || item.value("amountMinor").isNotBlank())Text(financeMoney(FinancialDomain.amount(item),currency,hidden),style=MaterialTheme.typography.headlineLarge)}
        if(item.notes.isNotBlank())item{Text(item.notes)}
        if(item.tags.isNotBlank())item{Text(item.tags)}
        item{listOf("category" to "Categoria","subcategory" to "Subcategoria","dueDate" to "Vencimento","competence" to "Competência","time" to "Horário","paymentMethod" to "Forma de pagamento","person" to "Pessoa / empresa","costCenter" to "Centro de custo","location" to "Localização").forEach{(key,label)->item.value(key).takeIf{it.isNotBlank()}?.let{Text("$label: $it")}};listOf("account" to "Conta","destination" to "Destino","card" to "Cartão").forEach{(key,label)->all.firstOrNull{it.id==item.value(key)}?.let{Text("$label: ${it.title}")}}}
        if(item.type in FinancialDomain.transactionTypes && !FinancialDomain.settled(item) && FinancialDomain.active(item))item{Button(onClick={val bill=all.firstOrNull{it.id==item.value("source") && it.type=="bill"};if(bill!=null)vm.settleBill(bill.id)else vm.commitFinance(listOf(FinanceActions.settle(item)));dismiss()},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text(if(item.type=="income")"Marcar como recebido"else"Marcar como pago")}}
        if(item.value("attachment").isNotBlank() || item.value("hasAttachment")=="yes" || item.value("cloudAttachmentPath").isNotBlank())item{OutlinedButton(onClick={download.launch(item.value("attachmentName").ifBlank{"${item.title}."+if(item.value("mime")=="application/pdf")"pdf"else if(item.value("mime")=="image/png")"png"else"bin"})}){Text("Salvar comprovante / anexo")}}
        item{Row{TextButton(onClick={edit(item)}){Text("Editar")};TextButton(onClick={delete(item)}){Text("Mover para lixeira")}}}
        if(item.type=="card")item{FinanceCardPanel(item,all,vm,currency,hidden,{target->vm.loadFullItem(target){edit(it)}})}
        if(item.type=="account")item{FinanceSection("Movimentações da conta"){all.filter{it.type in FinancialDomain.transactionTypes && (it.value("account")==item.id || it.value("destination")==item.id)}.sortedByDescending{it.date}.take(100).forEach{Text("${dateLabel(it.date)} · ${it.title} · ${financeMoney(FinancialDomain.amount(it),currency,hidden)}")}}}
        item{FinanceSection("Histórico de alterações"){if(history.isEmpty())Text("Sem alterações locais registradas para este item.")else history.take(30).forEach{Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.changedAt))} · ${it.action} · revisão ${it.revision}",style=MaterialTheme.typography.bodySmall)}}}
        item{Spacer(Modifier.height(30.dp))}
    }}
}
