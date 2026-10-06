package app.veyra.android
import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import androidx.compose.runtime.*
import app.veyra.data.*
import app.veyra.model.*
import app.veyra.feature.weather.Weather
import app.veyra.feature.finance.Statement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.math.BigDecimal
import org.json.JSONObject

class VeyraViewModel(app:Application):AndroidViewModel(app) {
    internal val store=WorkspaceStore(app)
    private val state=MutableStateFlow<List<Item>>(emptyList());val items=state.asStateFlow()
    private val mutex=Mutex()
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
    init {operation{};AndroidJobs.install(app)}
    fun operation(action:suspend ()->Unit) {viewModelScope.launch {mutex.withLock{
        busy=true
        try {val snapshot=withContext(Dispatchers.IO){action();store.all() to store.preferences()};state.value=snapshot.first;preferences=snapshot.second;loaded=true}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "Não foi possível concluir";runCatching{withContext(Dispatchers.IO){store.all() to store.preferences()}}.onSuccess{state.value=it.first;preferences=it.second};loaded=true}finally{busy=false}
    }}}
    fun pref(key:String,value:String)=operation{store.preference(key,value)}
    fun autosave(item:Item)=operation{Workspace.validate(item,Registry.spec(item.type));store.save(item)}
    fun save(item:Item)=operation{
        Workspace.validate(item,Registry.spec(item.type));val all=store.all()
        var parent=all.firstOrNull{it.id==item.parentId};var steps=0
        while(parent!=null && steps++<100){require(parent.id!=item.id){"Vínculo circular"};val parentId=parent.parentId;parent=all.firstOrNull{it.id==parentId}}
        if(item.type=="transfer")require(item.value("account")!=item.value("destination")){"Escolha contas diferentes"}
        val saved=if(item.type=="task" && item.value("status").isNotBlank())item.copy(done=item.value("status")=="Concluído")else item
        store.save(saved);AndroidJobs.schedule(getApplication(),saved);evaluateRules(store);AndroidJobs.updateWidget(getApplication());message="Salvo"
    }
    fun delete(item:Item)=operation{undoItem=item;store.save(item.copy(deletedAt=System.currentTimeMillis()));message="Movido para a lixeira";AndroidJobs.cancel(getApplication(),item.id);AndroidJobs.updateWidget(getApplication())}
    fun undo(){undoItem?.let{save(it.copy(deletedAt=0))};undoItem=null}
    fun complete(item:Item)=operation{
        store.save(item.copy(done=!item.done,fields=if(item.type=="task")item.fields+("status" to if(!item.done)"Concluído" else "A fazer")else item.fields))
        if(!item.done && item.type=="task" && item.value("recurrence") in listOf("Diária","Semanal","Mensal")){
            val d=LocalDate.parse(item.date);val next=when(item.value("recurrence")){"Diária"->d.plusDays(1);"Semanal"->d.plusWeeks(1);else->d.plusMonths(1)}
            val copy=item.copy(id="repeat:${item.id}:$next",date=next.toString(),done=false,fields=item.fields+("status" to "A fazer"))
            if(store.all().none{it.id==copy.id}){store.save(copy);AndroidJobs.schedule(getApplication(),copy)}
        };AndroidJobs.updateWidget(getApplication())
    }
    fun checkin(habit:Item)=operation{val date=LocalDate.now().toString();val id="checkin:${habit.id}:$date";val old=store.all().firstOrNull{it.id==id};store.save(Item(id=id,type="checkin",title=habit.title,parentId=habit.id,date=date,deletedAt=if(old!=null && old.deletedAt==0L)System.currentTimeMillis() else 0))}
    fun review(card:Item,easy:Boolean){val interval=if(easy)(card.number("interval").toLong().coerceAtLeast(1)*2).coerceAtMost(365)else 1;save(card.copy(date=LocalDate.now().plusDays(interval).toString(),fields=card.fields+("interval" to interval.toString())))}
    fun applyTemplate(item:Item)=operation{store.saveAll(item.value("lines").lines().filter{it.isNotBlank()}.take(200).map{Item(type="task",title=it.take(200),parentId=item.parentId)})}
    fun ingredients(item:Item)=operation{store.saveAll(item.value("ingredients").lines().filter{it.isNotBlank()}.take(200).map{Item(type="shopping",title=it.take(200),parentId=item.parentId,fields=mapOf("quantity" to "1"))})}
    fun installment(item:Item,count:Int)=operation{val start=LocalDate.parse(item.date);val children=Workspace.installments(item.cents(),count).mapIndexed{n,amount->val date=start.plusMonths(n.toLong());item.copy(id="installment:${item.id}:$n",title="${item.title.take(170)} • ${n+1}/$count",date=date.toString(),fields=item.fields+mapOf("amount" to BigDecimal.valueOf(amount,2).toPlainString(),"planned" to if(date.isAfter(LocalDate.now()))"Sim"else "Não"))};store.saveAll(children+item.copy(type="installment_plan",fields=item.fields+("count" to count.toString())));message="Compra convertida em $count parcelas"}
    fun recurring()=operation{val all=store.all();val existing=all.map{it.id}.toSet();store.saveAll(Workspace.recurring(all,LocalDate.now()).filter{it.id !in existing});evaluateRules(store)}
    fun settleBill(id:String)=operation{
        val bill=store.all().first{it.id==id}
        if(!bill.done){
            val payment=store.all().firstOrNull{it.id=="bill-payment:$id"}
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
    fun attach(uri:Uri,item:Item)=operation{val bytes=read(uri,10_000_000);store.save(item.copy(fields=item.fields+mapOf("attachment" to android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP),"mime" to (getApplication<Application>().contentResolver.getType(uri) ?: "application/octet-stream"))))}
    fun attachmentTo(uri:Uri,item:Item)=operation{getApplication<Application>().contentResolver.openOutputStream(uri)?.use{it.write(android.util.Base64.decode(item.value("attachment"),android.util.Base64.NO_WRAP))} ?: error("Arquivo indisponível")}
    fun export(uri:Uri,password:String="",format:String="json")=operation{val text=if(format=="csv")Statement.export(store.all().filter{it.deletedAt==0L})else store.exportJson().let{if(password.isBlank())it else BackupCrypto.encrypt(it,password)};getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(text)} ?: error("Arquivo indisponível");message="Exportado"}
    fun restore(uri:Uri,password:String)=operation{var text=read(uri,40_000_000).toString(Charsets.UTF_8);if(JSONObject(text).has("encrypted"))text=BackupCrypto.decrypt(text,password);store.importJson(text);message="Backup importado";AndroidJobs.updateWidget(getApplication())}
    fun previewStatement(uri:Uri,ofx:Boolean)=operation{val text=read(uri,5_000_000).toString(Charsets.UTF_8);val parsed=if(ofx)Statement.ofx(text)else Statement.csv(text);val existing=store.all().map{it.id}.toSet();importPreview=parsed.distinctBy{it.id}.filter{it.id !in existing};message="${importPreview.size} lançamentos novos; duplicatas ignoradas"}
    fun confirmStatement()=operation{store.saveAll(importPreview);importPreview=emptyList();AndroidJobs.updateWidget(getApplication())}
    fun cancelStatement(){importPreview=emptyList()}
}
fun money(cents:Long)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(cents,2))
