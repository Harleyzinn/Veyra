package app.veyra.android
import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import androidx.compose.runtime.*
import app.veyra.data.*
import app.veyra.model.*
import app.veyra.feature.weather.Weather
import app.veyra.feature.finance.Statement
import app.veyra.feature.finance.*
import app.veyra.cloud.CloudController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.math.BigDecimal
import org.json.JSONObject

class VeyraViewModel(app:Application):AndroidViewModel(app) {
    internal var store=WorkspaceStore(app);private set
    private val state=MutableStateFlow<List<Item>>(emptyList());val items=state.asStateFlow()
    private val mutex=Mutex()
    private var workspaceEpoch=0
    private var activeWorkspaceUid=WorkspaceIdentity.activeUid(app)
    private val financeState=MutableStateFlow<List<Item>>(emptyList());val financeItems=financeState.asStateFlow()
    private val searchState=MutableStateFlow<List<Item>>(emptyList());val searchItems=searchState.asStateFlow()
    private var financeFrom=LocalDate.now().minusMonths(3).withDayOfMonth(1)
    private var financeThrough=LocalDate.now().plusYears(1)
    private var searchJob:Job?=null
    var preferences by mutableStateOf<Map<String,String>>(emptyMap());private set
    var loaded by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    var message by mutableStateOf<String?>(null);private set
    var undoItem by mutableStateOf<Item?>(null);private set
    var busy by mutableStateOf(false);private set
    var importPreview by mutableStateOf<List<Item>>(emptyList());private set
    var cityResults by mutableStateOf<List<Item>>(emptyList());private set
    var ocrDraft by mutableStateOf<Item?>(null)
    var assistantReply by mutableStateOf("");internal set
    var sessionKey by mutableStateOf("")
    var updateRelease by mutableStateOf<AppRelease?>(null);private set
    var updateBusy by mutableStateOf(false);private set
    var updateProgress by mutableFloatStateOf(0f);private set
    var updateStatus by mutableStateOf("");private set
    var updateFile by mutableStateOf<java.io.File?>(null);private set
    val cloud=CloudController(app,onScopeChanged={
        AndroidJobs.cancelWorkspace(app,activeWorkspaceUid);activeWorkspaceUid=WorkspaceIdentity.activeUid(app)
        workspaceEpoch++;state.value=emptyList();financeState.value=emptyList();searchState.value=emptyList();preferences=emptyMap();undoItem=null;importPreview=emptyList();ocrDraft=null;message=null;error=null;loaded=false
        viewModelScope.launch{mutex.withLock{withContext(Dispatchers.IO){store.close();store=WorkspaceStore(app);AndroidJobs.install(app);AndroidJobs.updateWidget(app)} };operation{}}
    },onDataChanged={operation{}})
    init {operation{};AndroidJobs.install(app);checkUpdates(true)}
    fun checkUpdates(automatic:Boolean=false){if(updateBusy)return;viewModelScope.launch{
        updateBusy=true
        if(!automatic)updateStatus="Consultando a release oficial…"
        try{
            val result=withContext(Dispatchers.IO){
                val prefs=mutex.withLock{store.preferences()};val previous=prefs["updateCheckedAt"]?.toLongOrNull() ?: 0
                if(automatic && (prefs["autoUpdateCheck"]=="Não" || System.currentTimeMillis()-previous<24*60*60*1000L))return@withContext null to false
                val release=GitHubUpdater.latest(getApplication());mutex.withLock{store.preference("updateCheckedAt",System.currentTimeMillis().toString())};release to true
            }
            if(result.second){if(updateRelease?.tag!=result.first?.tag)updateFile=null;updateRelease=result.first;updateFile=result.first?.let{release->withContext(Dispatchers.IO){GitHubUpdater.cached(getApplication(),release)}};updateStatus=if(result.first==null)"Você está na versão mais recente disponível."else if(updateFile!=null)"Download, hash e assinatura conferidos. Confirme no Android."else "Uma nova versão está disponível."}
        }catch(e:Exception){if(!automatic)updateStatus=e.message ?: "Não foi possível consultar agora."}finally{updateBusy=false}
    }}
    fun downloadUpdate(){val release=updateRelease ?: return;if(updateBusy)return;viewModelScope.launch{
        updateBusy=true;updateProgress=0f;updateStatus="Baixando atualização…"
        try{updateFile=withContext(Dispatchers.IO){GitHubUpdater.download(getApplication(),release){value->updateProgress=value}};updateStatus="Hash e assinatura conferidos. Confirme a instalação no Android."}
        catch(e:Exception){updateFile=null;updateStatus=e.message ?: "O download falhou."}finally{updateBusy=false}
    }}
    fun installUpdate(){val release=updateRelease ?: return;val file=updateFile ?: return
        try{GitHubUpdater.install(getApplication(),file,release.version)}catch(e:Exception){updateStatus=e.message ?: "Não foi possível abrir o instalador."}
    }
    fun operation(action:suspend ()->Unit) {val epoch=workspaceEpoch;viewModelScope.launch {mutex.withLock{
        if(epoch!=workspaceEpoch)return@withLock
        busy=true
        try {val snapshot=withContext(Dispatchers.IO){
            if(store.preferences()["financialMigrationVersion"]!="3"){
                val original=store.all();val changes=FinanceMigration.migrate(original).zip(original).filter{it.first!=it.second}.map{it.first}
                if(changes.isNotEmpty())store.saveAll(changes);store.preference("financialMigrationVersion","3")
            }
            action();Triple(store.overview(),store.preferences(),store.financeWindow(financeFrom,financeThrough))
        };if(epoch==workspaceEpoch){state.value=snapshot.first;preferences=snapshot.second;financeState.value=snapshot.third;loaded=true;cloud.onLocalChanged()}}
        catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==workspaceEpoch){error=e.message ?: "Não foi possível concl​uir";runCatching{withContext(Dispatchers.IO){store.overview() to store.preferences()}}.onSuccess{state.value=it.first;preferences=it.second};loaded=true}}finally{busy=false}
    }}}
    fun loadFinance(from:LocalDate,through:LocalDate){require(!through.isBefore(from));financeFrom=from;financeThrough=through;operation{}}
    fun globalSearch(query:String){searchJob?.cancel();if(query.isBlank()){searchState.value=emptyList();return};val epoch=workspaceEpoch;searchJob=viewModelScope.launch{delay(250);val result=withContext(Dispatchers.IO){mutex.withLock{store.search(query)}};if(epoch==workspaceEpoch)searchState.value=result}}
    fun loadFullItem(item:Item,open:(Item)->Unit){val epoch=workspaceEpoch;viewModelScope.launch{val full=withContext(Dispatchers.IO){mutex.withLock{store.find(item.id)}};if(epoch==workspaceEpoch)open(full ?: item)}}
    fun openStoredItem(id:String,open:(Item)->Unit){val epoch=workspaceEpoch;viewModelScope.launch{val item=withContext(Dispatchers.IO){mutex.withLock{store.find(id)}};if(epoch==workspaceEpoch){if(item!=null && item.deletedAt==0L)open(item)else message="Este registro não está mais disponível."}}}
    suspend fun financeSuggestions(title:String,type:String):List<Item>{val epoch=workspaceEpoch;val records=withContext(Dispatchers.IO){mutex.withLock{store.search(title,limit=50)}};return if(epoch==workspaceEpoch)FinanceActions.suggestions(title,records,type)else emptyList()}
    fun commitFinance(changes:List<Item>,onDone:()->Unit={}){
        commitFinanceMutation({changes},onDone)
    }
    private fun commitFinanceMutation(build:(List<Item>)->List<Item>,onDone:()->Unit={}){
        val epoch=workspaceEpoch;viewModelScope.launch{mutex.withLock{
            if(epoch!=workspaceEpoch)return@withLock;busy=true
            try{val result=withContext(Dispatchers.IO){val context=store.financeWindow(financeFrom,financeThrough);val normalized=build(context).map(FinancialDomain::normalize);val ids=normalized.map{it.id}.toSet();val merged=context.filter{it.id !in ids}+normalized;normalized.forEach{record->if(record.type in setOf("account","card")){val previous=store.find(record.id);if(previous!=null && FinancialDomain.currency(previous)!=FinancialDomain.currency(record))require(!store.hasActiveFinanceReferences(record.id)){"Crie outra conta ou cartão para usar uma moeda diferente; há lançamentos vinculados."}};FinancialDomain.validate(record,merged)};store.saveAll(normalized);if(epoch==workspaceEpoch){normalized.forEach{AndroidJobs.schedule(getApplication(),it,store.uid)};AndroidJobs.financeChanged(getApplication())};Triple(store.overview(),store.preferences(),store.financeWindow(financeFrom,financeThrough))}
                if(epoch==workspaceEpoch){state.value=result.first;preferences=result.second;financeState.value=result.third;message="Salvo";cloud.onLocalChanged();AndroidJobs.updateWidget(getApplication());onDone()}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(epoch==workspaceEpoch)error=e.message ?: "Não foi possível salvar o lançamento."}finally{busy=false}
        }}
    }
    fun changeFinanceSeries(ruleId:String,at:LocalDate,scope:SeriesScope,replacement:Item?=null,onDone:()->Unit={}){
        commitFinanceMutation({context->
            val rule=store.find(ruleId) ?: error("Recorrência não encontrada.")
            require(rule.deletedAt==0L && RecurrenceEngine.isRule(rule)){"Esta recorrência não está mais ativa."}
            val members=store.financeSeriesMembers(rule.id)
            if(replacement==null)RecurrenceEngine.delete(rule,at,scope,context+members)
            else RecurrenceEngine.edit(rule,at,replacement,scope,context+members)
        },onDone)
    }
    fun saveFinance(item:Item,makeTemplate:Boolean=false,onDone:()->Unit={}){
        commitFinanceMutation({context->
            val prepared=FinanceActions.prepareSave(item,context)
            val current=store.find(item.id)
            val changes=if(current!=null && RecurrenceEngine.isRule(current) && prepared.size==1 && RecurrenceEngine.isRule(prepared.single()))
                RecurrenceEngine.edit(current,LocalDate.parse(current.value("startDate").ifBlank{current.date}),prepared.single(),SeriesScope.ALL,context+store.financeSeriesMembers(current.id)).toMutableList()
            else prepared.toMutableList()
            if(makeTemplate)changes+=FinanceActions.template(item)
            changes
        }){pref(if(item.type=="income")"recentIncomeCategory"else"recentExpenseCategory",item.value("category"));pref("recentFinanceAccount",item.value("account"));onDone()}
    }
    fun purgeFinance(item:Item)=operation{store.purge(item.id);message="Registro excluído definitivamente"}
    private fun financeMutationContext(item:Item,deleted:Boolean=false):List<Item>{
        val context=store.financeWindow(financeFrom,financeThrough).toMutableList()
        if(item.type=="installment_plan")context+=store.financePlanMembers(item.id,includeDeleted=deleted)
        if(RecurrenceEngine.isRule(item))context+=store.financeSeriesMembers(item.id,includeDeleted=deleted)
        item.value("debtId").takeIf{it.isNotBlank()}?.let{store.find(it)?.let(context::add)}
        context+=store.find(item.id) ?: item
        return context.distinctBy{it.id}
    }
    fun restoreFinance(item:Item)=operation{val current=store.find(item.id) ?: item;val context=financeMutationContext(current,true);val changes=FinanceActions.restore(current,context);changes.filter{it.deletedAt==0L}.forEach{FinancialDomain.validate(it,context+changes)};store.saveAll(changes);message="Registro restaurado";AndroidJobs.financeChanged(getApplication())}
    suspend fun financeTrash(limit:Int):List<Item>{val epoch=workspaceEpoch;val result=withContext(Dispatchers.IO){mutex.withLock{buildList{var cursor:FinancePageCursor?=null;do{val page=store.financeTrash(cursor,100);addAll(page.items);cursor=page.nextCursor}while(cursor!=null && size<limit)}}};return if(epoch==workspaceEpoch)result.take(limit)else emptyList()}
    fun financialHistory(id:String,receive:(List<app.veyra.data.AuditEvent>)->Unit){val epoch=workspaceEpoch;viewModelScope.launch{val history=withContext(Dispatchers.IO){mutex.withLock{store.audit(id)}};if(epoch==workspaceEpoch)receive(history)}}
    fun advanceFinance(planId:String,count:Int)=operation{require(count>0){"Informe quantas parcelas antecipar."};val installments=store.financePlanMembers(planId);val changes=CardEngine.advanceInstallments(installments,planId,count);require(changes.isNotEmpty()){ "Não há parcelas futuras para antecipar." };store.saveAll(changes);message="Vencimentos antecipados"}
    fun payDebt(debt:Item,value:String,accountId:String,done:()->Unit={}){
        val epoch=workspaceEpoch
        operation{val current=store.find(debt.id) ?: error("Dívida não encontrada.");require(FinancialDomain.amount(current,"paid")==FinancialDomain.amount(debt,"paid")){"A dívida foi alterada. Abra novamente antes de registrar."};val account=store.find(accountId) ?: error("Conta não encontrada.");require(account.type=="account" && account.deletedAt==0L){"Use uma conta ativa."};val changes=FinanceActions.recordDebtPayment(current,FinancialDomain.parseMinor(value,FinancialDomain.currency(current)),account);store.saveAll(changes);AndroidJobs.financeChanged(getApplication());message="Quitação registrada";if(epoch==workspaceEpoch)withContext(Dispatchers.Main){done()}}
    }
    fun payInvoice(invoice:CardInvoice,accountId:String)=operation{val card=store.find(invoice.cardId) ?: error("Cartão não encontrado.");val context=store.financeWindow(minOf(financeFrom,invoice.dueDate.minusMonths(2)),maxOf(financeThrough,invoice.dueDate.plusMonths(1)));val current=FinanceEngine.cardInvoices(context,card,invoice.dueDate,invoice.dueDate).firstOrNull{it.id==invoice.id} ?: error("Fatura não encontrada.");if(current.outstandingMinor==0L){message="Esta fatura já está quitada.";return@operation};require(current.paidMinor==invoice.paidMinor && current.outstandingMinor==invoice.outstandingMinor){"A fatura foi alterada. Abra novamente antes de pagar."};val changes=CardEngine.settleInvoice(current,accountId);changes.forEach{FinancialDomain.validate(it,context+changes)};store.saveAll(changes);AndroidJobs.financeChanged(getApplication());message="Pagamento da fatura registrado"}
    fun saveNetWorth(currency:String)=runCatching{commitFinance(listOf(FinanceActions.snapshot(financeState.value,LocalDate.now(),currency)))}.onFailure{error=it.message}
    fun saveMonthClose(month:java.time.YearMonth,currency:String)=operation{val day=(store.preferences()["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31);val period=FinanceEngine.period(month,day);val context=store.financeWindow(minOf(period.start.minusMonths(3),LocalDate.now().minusMonths(3)),maxOf(period.end,LocalDate.now()));store.saveAll(FinanceActions.closeMonth(context,month,LocalDate.now(),currency,day));message="Fechamento guardado"}
    override fun onCleared(){cloud.close();store.close();super.onCleared()}
    fun pref(key:String,value:String)=operation{store.preference(key,value)}
    fun autosave(item:Item)=operation{Workspace.validate(item,Registry.spec(item.type));store.save(item)}
    fun save(item:Item)=operation{
        Workspace.validate(item,Registry.spec(item.type))
        var parent=store.find(item.parentId);var steps=0
        while(parent!=null && steps++<100){require(parent.id!=item.id){"Vínculo circular"};val parentId=parent.parentId;parent=store.find(parentId)}
        if(item.type=="transfer")require(item.value("account")!=item.value("destination")){"Escolha contas diferentes"}
        val saved=if(item.type=="task" && item.value("status").isNotBlank())item.copy(done=item.value("status")=="Concluído")else item
        store.save(saved);AndroidJobs.schedule(getApplication(),saved,store.uid);evaluateRules(store);AndroidJobs.updateWidget(getApplication());message="Salvo"
    }
    fun delete(item:Item)=operation{val current=store.find(item.id) ?: item;if(item.type in setOf("account","card"))require(!store.hasActiveFinanceReferences(item.id)){"Este registro tem lançamentos vinculados. Transfira ou altere os vínculos antes de excluir."};if(item.type in FinancialDomain.financialTypes)store.saveAll(FinanceActions.trash(current,financeMutationContext(current)))else store.save(current.copy(deletedAt=System.currentTimeMillis()));undoItem=current;message="Movido para a lixeira";AndroidJobs.cancel(getApplication(),item.id);AndroidJobs.financeChanged(getApplication());AndroidJobs.updateWidget(getApplication())}
    fun undo(){undoItem?.let{if(it.type in FinancialDomain.financialTypes)restoreFinance(it)else save(it.copy(deletedAt=0))};undoItem=null}
    fun complete(item:Item)=operation{
        if(item.type in FinancialDomain.transactionTypes){store.save(FinanceActions.settle(item));return@operation}
        store.save(item.copy(done=!item.done,fields=if(item.type=="task")item.fields+("status" to if(!item.done)"Concluído" else "A fazer")else item.fields))
        if(!item.done && item.type=="task" && item.value("recurrence") in listOf("Diária","Semanal","Mensal")){
            val d=LocalDate.parse(item.date);val next=when(item.value("recurrence")){"Diária"->d.plusDays(1);"Semanal"->d.plusWeeks(1);else->d.plusMonths(1)}
            val copy=item.copy(id="repeat:${item.id}:$next",date=next.toString(),done=false,fields=item.fields+("status" to "A fazer"))
            if(store.find(copy.id)==null){store.save(copy);AndroidJobs.schedule(getApplication(),copy,store.uid)}
        };AndroidJobs.updateWidget(getApplication())
    }
    fun checkin(habit:Item)=operation{val date=LocalDate.now().toString();val id="checkin:${habit.id}:$date";val old=store.find(id);store.save(Item(id=id,type="checkin",title=habit.title,parentId=habit.id,date=date,deletedAt=if(old!=null && old.deletedAt==0L)System.currentTimeMillis() else 0))}
    fun review(card:Item,easy:Boolean){val interval=if(easy)(card.number("interval").toLong().coerceAtLeast(1)*2).coerceAtMost(365)else 1;save(card.copy(date=LocalDate.now().plusDays(interval).toString(),fields=card.fields+("interval" to interval.toString())))}
    fun applyTemplate(item:Item)=operation{store.saveAll(item.value("lines").lines().filter{it.isNotBlank()}.take(200).map{Item(type="task",title=it.take(200),parentId=item.parentId)})}
    fun ingredients(item:Item)=operation{store.saveAll(item.value("ingredients").lines().filter{it.isNotBlank()}.take(200).map{Item(type="shopping",title=it.take(200),parentId=item.parentId,fields=mapOf("quantity" to "1"))})}
    fun installment(item:Item,count:Int)=operation{val start=LocalDate.parse(item.date);val children=Workspace.installments(item.cents(),count).mapIndexed{n,amount->val date=start.plusMonths(n.toLong());item.copy(id="installment:${item.id}:$n",title="${item.title.take(170)} • ${n+1}/$count",date=date.toString(),fields=item.fields+mapOf("amount" to BigDecimal.valueOf(amount,2).toPlainString(),"planned" to if(date.isAfter(LocalDate.now()))"Sim"else "Não"))};store.saveAll(children+item.copy(type="installment_plan",fields=item.fields+("count" to count.toString())));message="Compra convertida em $count parcelas"}
    fun recurring()=operation{val all=store.all();val existing=all.map{it.id}.toSet();store.saveAll(Workspace.recurring(all,LocalDate.now()).filter{it.id !in existing});evaluateRules(store)}
    fun settleBill(id:String)=operation{
        val bill=store.find(id) ?: error("Conta não encontrada")
        if(!bill.done){
            val payment=store.find("bill-payment:$id")
            require(payment==null || payment.deletedAt!=0L){"Esta conta já possui um pagamento. Confira a planilha."}
            val changes=LifeInsights.settleBill(bill,LocalDate.now())
            changes.forEach{Workspace.validate(it,Registry.spec(it.type))};store.saveAll(changes)
            AndroidJobs.cancel(getApplication(),id);AndroidJobs.updateWidget(getApplication());message="Conta paga e despesa registrada"
        }
    }
    fun mealShopping(id:String)=operation{
        val meal=store.all().first{it.id==id};val existing=store.all().map{it.id}.toSet()
        val additions=LifeInsights.mealShopping(meal).filter{it.id !in existing}.take(200)
        additions.forEach{Workspace.validate(it,Registry.spec(it.type))};store.saveAll(additions);message="${additions.size} ingredientes adicionados às compras"
    }
    fun clearModule(type:String)=operation{store.clearModule(type);AndroidJobs.updateWidget(getApplication())}
    fun dismiss(){error=null;message=null};fun consumeMessage(){message=null}
    fun searchCity(query:String)=operation{require(store.preferences()["weatherConsent"]=="Sim"){"Ative a previsão online"};cityResults=Weather.search(query)}
    fun addCity(city:Item)=operation{require(store.preferences()["weatherConsent"]=="Sim"){"Ative a previsão online"};store.save(city);store.preference("weatherCity",city.id);cityResults=emptyList();store.save(Weather.forecast(city))}
    fun weather(city:Item)=operation{require(store.preferences()["weatherConsent"]=="Sim"){"Ative a previsão online"};store.save(Weather.forecast(city))}
    internal fun read(uri:Uri,max:Int):ByteArray=getApplication<Application>().contentResolver.openInputStream(uri)?.use{input->
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count=input.read(buffer)
        while(count>=0){require(output.size()+count<=max){"Arquivo excede ${max/1_000_000} MB"};output.write(buffer,0,count);count=input.read(buffer)};output.toByteArray()
    } ?: error("Arquivo indisponível")
    fun attach(uri:Uri,item:Item)=operation{val bytes=read(uri,10_000_000);val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)};store.save(item.copy(fields=item.fields-setOf("cloudAttachmentPath","cloudAttachmentSha256","cloudAttachmentSize")+mapOf("attachment" to android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),"attachmentHash" to hash,"hasAttachment" to "yes","mime" to (getApplication<Application>().contentResolver.getType(uri) ?: "application/octet-stream"))))}
    suspend fun attachmentBytes(item:Item):ByteArray{val epoch=workspaceEpoch;val full=withContext(Dispatchers.IO){mutex.withLock{store.find(item.id)}} ?: item;require(epoch==workspaceEpoch){"A conta ativa mudou. Abra novamente o anexo."};val bytes=cloud.attachmentBytes(full);require(epoch==workspaceEpoch){"A conta ativa mudou. Abra novamente o anexo."};return bytes}
    fun attachmentTo(uri:Uri,item:Item)=operation{val bytes=cloud.attachmentBytes(store.find(item.id) ?: item);getApplication<Application>().contentResolver.openOutputStream(uri)?.use{it.write(bytes)} ?: error("Arquivo indisponível")}
    fun export(uri:Uri,password:String="",format:String="json")=operation{val text=if(format=="csv")Statement.export(store.all().filter{it.deletedAt==0L})else store.exportJson().let{if(password.isBlank())it else BackupCrypto.encrypt(it,password)};getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(text)} ?: error("Arquivo indisponível");message="Exportado"}
    fun restore(uri:Uri,password:String)=operation{var text=read(uri,40_000_000).toString(Charsets.UTF_8);if(JSONObject(text).has("encrypted"))text=BackupCrypto.decrypt(text,password);store.importJson(text);message="Backup importado";AndroidJobs.updateWidget(getApplication())}
    fun previewStatement(uri:Uri,ofx:Boolean)=operation{val text=read(uri,5_000_000).toString(Charsets.UTF_8);val parsed=if(ofx)Statement.ofx(text)else Statement.csv(text);val existing=store.all().map{it.id}.toSet();importPreview=parsed.distinctBy{it.id}.filter{it.id !in existing};message="${importPreview.size} lançamentos novos; duplicatas ignoradas"}
    fun confirmStatement()=operation{store.saveAll(importPreview);importPreview=emptyList();AndroidJobs.updateWidget(getApplication())}
    fun cancelStatement(){importPreview=emptyList()}
}
fun money(cents:Long)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents,2))
