package app.veyra.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.veyra.model.Item
import app.veyra.model.Money
import app.veyra.feature.finance.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

internal val incomeCategories=listOf("Salário","Freelance","Comissão","Venda","Cashback","Reembolso","Rendimentos","Investimentos","Presente","Outros")
internal val expenseCategories=listOf("Alimentação","Mercado","Transporte","Combustível","Casa","Aluguel","Energia","Água","Internet","Telefone","Assinaturas","Compras","Lazer","Saúde","Educação","Viagem","Impostos","Dívidas","Cartão","Investimentos","Outros")

@Composable internal fun FinanceSection(title:String,content:@Composable ColumnScope.()->Unit){
    var expanded by remember{mutableStateOf(false)}
    OutlinedCard(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()){
                Text(title,Modifier.weight(1f));Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
            }
            AnimatedVisibility(expanded){Column(verticalArrangement=Arrangement.spacedBy(10.dp),content=content)}
        }
    }
}

@Composable internal fun FinanceField(label:String,value:String,change:(String)->Unit,numeric:Boolean=false,lines:Int=1){
    OutlinedTextField(value,change,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=lines==1,minLines=lines,
        keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text))
}

/** Four-field capture; advanced details expand without discarding the draft. */
@Composable fun FinanceEditor(initial:Item,all:List<Item>,vm:VeyraViewModel,dismiss:()->Unit,submit:((Item,Boolean,()->Unit)->Unit)?=null){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val haptic=LocalHapticFeedback.current
    val incoming=initial.type=="income" || initial.value("transactionType")=="income";val transfer=initial.type=="transfer"
    val fields=remember(initial.id){mutableStateMapOf<String,String>().apply{
        putAll(initial.fields)
        if(get("currency").isNullOrBlank())put("currency",vm.preferences["financeCurrency"] ?: "BRL")
        if(get("category").isNullOrBlank())put("category",vm.preferences[if(incoming)"recentIncomeCategory"else"recentExpenseCategory"] ?: if(incoming)"Salário"else"Alimentação")
        if(get("status").isNullOrBlank())put("status",if(incoming)"RECEIVED"else"PAID")
        else put("status",getValue("status").uppercase())
        if(get("account").isNullOrBlank())put("account",vm.preferences["recentFinanceAccount"].orEmpty())
    }}
    var title by remember(initial.id){mutableStateOf(initial.title)}
    var categoryChosen by remember(initial.id){mutableStateOf(initial.value("category").isNotBlank())}
    var amount by remember(initial.id){mutableStateOf(initial.value("amount"))}
    var date by remember(initial.id){mutableStateOf(initial.date.ifBlank{LocalDate.now().toString()})}
    var notes by remember(initial.id){mutableStateOf(initial.notes)};var tags by remember(initial.id){mutableStateOf(initial.tags)}
    var advanced by remember{mutableStateOf(initial.title.isNotBlank() || transfer)}
    var error by remember{mutableStateOf("")};var importing by remember{mutableStateOf(false)}
    var makeTemplate by remember{mutableStateOf(false)}
    val accounts=all.filter{it.type=="account" && it.deletedAt==0L};val cards=all.filter{it.type=="card" && it.deletedAt==0L}
    val categories=FinancialCategories.categories(all,if(incoming)"income"else"expense")
    val recent by produceState<List<Item>>(emptyList(),title,initial.type){if(title.length<2)value=emptyList()else{kotlinx.coroutines.delay(250);value=vm.financeSuggestions(title,initial.type).filter{it.id!=initial.id}.take(4)}}
    val attach=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
        importing=true;error=""
        runCatching{withContext(Dispatchers.IO){val bytes=vm.read(uri,10_000_000);val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
            require(mime in listOf("application/pdf","image/png","image/jpeg","text/plain")){"Use PDF, PNG, JPEG ou texto (até 10 MB)."}
            val name=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst())cursor.getString(0)else null}.orEmpty()
            Triple(android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),mime,name)
        }}.onSuccess{(data,mime,name)->listOf("cloudAttachmentPath","cloudAttachmentSha256","cloudAttachmentSize","attachmentHash").forEach(fields::remove);fields["attachment"]=data;fields["hasAttachment"]="yes";fields["mime"]=mime;fields["attachmentName"]=name}.onFailure{error=it.message ?: "Não foi possível adicionar o arquivo."};importing=false
    }}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxWidth().padding(10.dp).imePadding().fillMaxHeight(.97f),shape=MaterialTheme.shapes.extraLarge){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row{Column(Modifier.weight(1f)){Text("LANÇAMENTO",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(if(transfer)"Transferência"else if(incoming)"Nova entrada"else"Novo gasto",style=MaterialTheme.typography.headlineMedium)};IconButton(onClick=dismiss){Icon(Icons.Default.Close,"Fechar lançamento")}}
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    item{FinanceField("Valor • ${fields["currency"]}",amount,{amount=it},true)}
                    item{FinanceField("Descrição",title,{title=it.take(200)})}
                    if(recent.isNotEmpty())item{Column{Text("Seu histórico",style=MaterialTheme.typography.labelMedium);recent.forEach{previous->TextButton(onClick={title=previous.title;amount=previous.value("amount");listOf("category","subcategory","account","card","paymentMethod").forEach{k->fields[k]=previous.value(k)}}){Text("${previous.title} · ${previous.value("amount")} ${previous.value("currency").ifBlank{"BRL"}}")}}}}
                    if(!transfer)item{Choice("Categoria",categories,fields["category"].orEmpty(),{fields["category"]=it;categoryChosen=true});FinanceField("Categoria personalizada",fields["category"].orEmpty(),{fields["category"]=it;categoryChosen=true})}
                    item{FinanceField("Data • AAAA-MM-DD",date,{date=it});TextButton(onClick={date=LocalDate.now().toString()}){Text("Usar hoje")}}
                    item{TextButton(onClick={advanced=!advanced},modifier=Modifier.fillMaxWidth()){Icon(if(advanced)Icons.Default.ExpandLess else Icons.Default.Tune,null);Spacer(Modifier.width(8.dp));Text(if(advanced)"Recolher detalhes"else"Modo avançado")}}
                    if(advanced){
                        item{FinanceSection("Pagamento e contas"){
                            Choice(if(transfer)"Origem"else"Conta",listOf("")+accounts.map{it.id},fields["account"].orEmpty(),{fields["account"]=it}){id->accounts.firstOrNull{it.id==id}?.title ?: "Sem conta"}
                            if(transfer)Choice("Destino",listOf("")+accounts.map{it.id},fields["destination"].orEmpty(),{fields["destination"]=it}){id->accounts.firstOrNull{it.id==id}?.title ?: "Escolher conta"}
                            else{
                                if(!incoming)Choice("Cartão",listOf("")+cards.map{it.id},fields["card"].orEmpty(),{fields["card"]=it}){id->cards.firstOrNull{it.id==id}?.title ?: "Sem cartão"}
                                Choice("Forma",listOf("Pix","Dinheiro","Débito","Crédito","Boleto","Transferência","Outro"),fields["paymentMethod"].orEmpty(),{fields["paymentMethod"]=it})
                            }
                            Choice("Moeda",listOf("BRL","USD","EUR","GBP"),fields["currency"].orEmpty(),{fields["currency"]=it})
                        }}
                        item{FinanceSection("Status e datas"){
                            Choice("Status",if(incoming)listOf("RECEIVED","PENDING","EXPECTED","CANCELLED")else listOf("PAID","PENDING","EXPECTED","CANCELLED"),fields["status"].orEmpty(),{fields["status"]=it}){financeStatusLabel(it)}
                            FinanceField("Vencimento • AAAA-MM-DD",fields["dueDate"].orEmpty(),{fields["dueDate"]=it})
                            FinanceField("Competência • AAAA-MM",fields["competence"].orEmpty(),{fields["competence"]=it})
                            FinanceField("Horário • HH:mm",fields["time"].orEmpty(),{fields["time"]=it})
                            FinanceField("Lembrete • HH:mm",fields["reminder"].orEmpty(),{fields["reminder"]=it})
                        }}
                        if(!transfer)item{FinanceSection("Recorrência e parcelas"){
                            Choice("Frequência",listOf("NONE","DAILY","WEEKLY","FORTNIGHTLY","MONTHLY","BIMONTHLY","QUARTERLY","SEMIANNUAL","YEARLY","CUSTOM"),fields["frequency"].ifNullOrBlank("NONE"),{fields["frequency"]=it}){financeFrequencyLabel(it)}
                            if(fields["frequency"] !in listOf(null,"","NONE")){
                                FinanceField("Início • AAAA-MM-DD",fields["startDate"].ifNullOrBlank(date),{fields["startDate"]=it})
                                FinanceField("Fim • vazio para continuar",fields["endDate"].orEmpty(),{fields["endDate"]=it})
                                FinanceField("Quantidade de ocorrências • opcional",fields["occurrenceCount"].orEmpty(),{fields["occurrenceCount"]=it},true)
                                FinanceField("Dia do mês • 1–31",fields["dayOfMonth"].orEmpty(),{fields["dayOfMonth"]=it},true)
                                FinanceField("Dias da semana • 1=seg … 7=dom, separados por vírgula",fields["weekdays"].orEmpty(),{fields["weekdays"]=it})
                                Row{Checkbox(fields["lastBusinessDay"]=="yes",{fields["lastBusinessDay"]=if(it)"yes"else"no"});Text("Último dia útil (segunda a sexta)",Modifier.padding(top=12.dp))}
                                if(fields["frequency"]=="CUSTOM"){FinanceField("Intervalo",fields["interval"].ifNullOrBlank("1"),{fields["interval"]=it},true);Choice("Unidade",listOf("days","weeks","months","years"),fields["customUnit"].ifNullOrBlank("days"),{fields["customUnit"]=it})}
                            }
                            if(!incoming){FinanceField("Número de parcelas • 1–120",fields["installments"].ifNullOrBlank("1"),{fields["installments"]=it},true);FinanceField("Primeira parcela • AAAA-MM-DD",fields["firstInstallment"].orEmpty(),{fields["firstInstallment"]=it})}
                        }}
                        item{FinanceSection("Classificação e contexto"){
                            val subcategories=FinancialCategories.subcategories(all,fields["category"].orEmpty());if(subcategories.isNotEmpty())Choice("Subcategoria cadastrada",listOf("")+subcategories,fields["subcategory"].orEmpty(),{fields["subcategory"]=it})
                            FinanceField("Subcategoria",fields["subcategory"].orEmpty(),{fields["subcategory"]=it});FinanceField("Tags",tags,{tags=it})
                            FinanceField("Pessoa ou empresa",fields["person"].orEmpty(),{fields["person"]=it});FinanceField("Centro de custo",fields["costCenter"].orEmpty(),{fields["costCenter"]=it})
                            FinanceField("Localização • opcional",fields["location"].orEmpty(),{fields["location"]=it})
                            Row{Checkbox(fields["essential"]=="true",{fields["essential"]=it.toString()});Text("Essencial",Modifier.padding(top=12.dp));Checkbox(fields["reimbursable"]=="true",{fields["reimbursable"]=it.toString()});Text("Reembolsável",Modifier.padding(top=12.dp))}
                        }}
                        item{FinanceSection("Notas e comprovante"){
                            FinanceField("Observações / nota",notes,{notes=it},lines=3)
                            OutlinedButton(onClick={attach.launch(arrayOf("application/pdf","image/png","image/jpeg","text/plain"))},enabled=!importing){Icon(Icons.Default.AttachFile,null);Text(if(fields["attachment"].isNullOrBlank())"Anexar comprovante"else"Trocar comprovante")}
                            if(!fields["attachment"].isNullOrBlank() || fields["hasAttachment"]=="yes" || !fields["cloudAttachmentPath"].isNullOrBlank()){Text(fields["attachmentName"].ifNullOrBlank("Comprovante anexado"));TextButton(onClick={listOf("attachment","mime","attachmentName","hasAttachment","attachmentHash","cloudAttachmentPath","cloudAttachmentSha256","cloudAttachmentSize").forEach(fields::remove)}){Text("Remover anexo")}}
                        }}
                        item{Row{Checkbox(makeTemplate,{makeTemplate=it});Text("Guardar também como lançamento rápido",Modifier.padding(top=12.dp))}}
                    }
                    if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
                }
                Button(onClick={
                    runCatching{
                        val cents=FinancialDomain.parseMinor(amount,fields["currency"] ?: "BRL");require(cents>0){"Informe um valor maior que zero."};LocalDate.parse(date)
                        var record=initial.copy(title=title.trim(),date=date,notes=notes,tags=tags,fields=fields.toMap()+mapOf("recurrence" to if(fields["frequency"] !in listOf(null,"","NONE"))"yes"else"no","amount" to FinancialDomain.decimal(cents,fields["currency"] ?: "BRL"),"amountMinor" to cents.toString(),"financialVersion" to "3"))
                        if(!categoryChosen){val suggested=FinanceActions.applyRules(record.copy(fields=record.fields-"category"),all);record=suggested.copy(fields=suggested.fields+("category" to suggested.value("category").ifBlank{fields["category"].orEmpty()}))}
                        FinancialDomain.validate(record,all)
                        val done={haptic.performHapticFeedback(HapticFeedbackType.LongPress);dismiss()}
                        if(submit!=null)submit(record,makeTemplate,done)else vm.saveFinance(record,makeTemplate,done)
                    }.onFailure{error=it.message ?: "Confira os dados do lançamento."}
                },enabled=!vm.busy && !importing,modifier=Modifier.fillMaxWidth().height(54.dp)){Text(if(vm.busy)"Salvando…"else"Salvar")}
            }
        }
    }
}

private fun String?.ifNullOrBlank(default:String)=if(this.isNullOrBlank())default else this
internal fun financeStatusLabel(value:String)=mapOf("PAID" to "Pago","RECEIVED" to "Recebido","PENDING" to "Pendente","EXPECTED" to "Previsto","OVERDUE" to "Atrasado","CANCELLED" to "Cancelado")[value] ?: value
internal fun financeFrequencyLabel(value:String)=mapOf("NONE" to "Não repetir","DAILY" to "Diária","WEEKLY" to "Semanal","FORTNIGHTLY" to "Quinzenal","MONTHLY" to "Mensal","BIMONTHLY" to "Bimestral","QUARTERLY" to "Trimestral","SEMIANNUAL" to "Semestral","YEARLY" to "Anual","CUSTOM" to "Personalizada")[value] ?: value
