package app.veyra.android
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import app.veyra.feature.finance.FinancialDomain
import java.time.LocalDate
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VeyraApp(vm:VeyraViewModel,shared:String?,image:Uri?,shortcut:String?,capture:Boolean,openId:String?,notificationPermission:()->Unit,openUpdates:Boolean=false,openFinance:Boolean=false,entryRevision:Int=0){
    val all by vm.items.collectAsState();val items=all.filter{it.deletedAt==0L}
    val searchResults by vm.searchItems.collectAsState()
    val financialItems by vm.financeItems.collectAsState()
    var page by rememberSaveable{mutableStateOf("Hoje")};var module by rememberSaveable{mutableStateOf("hub")};var query by rememberSaveable{mutableStateOf("")}
    var searching by rememberSaveable{mutableStateOf(false)}
    var editor by remember{mutableStateOf<Item?>(if(shared!=null)Item(type="note",title="",notes=shared)else if(shortcut!=null || capture)Item(type=shortcut ?: "note",title="")else null)}
    var detail by remember{mutableStateOf<Item?>(null)};var deleting by remember{mutableStateOf<Item?>(null)}
    var showSettings by remember{mutableStateOf(openUpdates)};var trash by remember{mutableStateOf(false)}
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(query){vm.globalSearch(query)}
    LaunchedEffect(openUpdates,entryRevision){if(openUpdates)showSettings=true}
    LaunchedEffect(openFinance,entryRevision){if(openFinance){page="Finanças";showSettings=false}}
    LaunchedEffect(entryRevision){if(entryRevision>0){if(shared!=null)editor=Item(type="note",title="",notes=shared)else if(shortcut!=null || capture)editor=Item(type=shortcut ?: "note",title="")}}
    LaunchedEffect(image){image?.let{vm.scan(it)}}
    LaunchedEffect(openId,entryRevision){if(openId!=null)vm.openStoredItem(openId){detail=it}}
    LaunchedEffect(vm.ocrDraft){vm.ocrDraft?.let{editor=it;vm.ocrDraft=null}}
    LaunchedEffect(vm.message){vm.message?.let{val result=snackbar.showSnackbar(it,if(vm.undoItem!=null)"Desfazer"else null);if(result==SnackbarResult.ActionPerformed)vm.undo();vm.consumeMessage()}}
    val tabs=listOf("Hoje","Agenda","Finanças","Notas","Mais")
    val icons=listOf(Icons.Default.Dashboard,Icons.Default.CalendarMonth,Icons.Default.AccountBalanceWallet,Icons.AutoMirrored.Filled.Notes,Icons.Default.GridView)
    fun create(type:String,parent:String=""){editor=Item(type=type,title="",parentId=parent)}
    fun navigate(type:String){query="";searching=false;trash=false;when(type){"finance"->page="Finanças";"notes"->page="Notas";"agenda"->page="Agenda";else->{page="Mais";module=type}}}
    BackHandler(enabled=page!="Hoje" || searching){if(searching){searching=false;query=""}else if(page=="Mais" && module!="hub"){module="hub";trash=false}else page="Hoje"}
    Scaffold(snackbarHost={SnackbarHost(snackbar)},topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),contentAlignment=Alignment.Center){Text("v",color=MaterialTheme.colorScheme.onPrimary,fontWeight=FontWeight.ExtraBold,fontSize=23.sp)};Text("veyra",fontWeight=FontWeight.ExtraBold,letterSpacing=(-1).sp,fontSize=24.sp)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),actions={IconButton(onClick={searching=!searching;query=""}){Icon(Icons.Default.Search,"Buscar")};IconButton(onClick={showSettings=true}){Icon(Icons.Default.Tune,"Configurações")}})},
        bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp){tabs.forEachIndexed{n,label->NavigationBarItem(selected=page==label,onClick={page=label;if(label=="Mais")module="hub";query="";searching=false;trash=false},icon={Icon(icons[n],null)},label={Text(label)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))}}},
        floatingActionButton={if(page in setOf("Hoje","Agenda") || page=="Mais" && module !in setOf("city","tools","assistant","hub","task","focus","routine","sketch","scanner","decisions","playroom","worldclock"))FloatingActionButton(modifier=Modifier.semantics{contentDescription="Capturar"},containerColor=MaterialTheme.colorScheme.primary,onClick={create(when(page){"Agenda"->"task";"Mais"->module.takeUnless{it in listOf("favorites","timeline")} ?: "note";else->"task"})}){Icon(Icons.Default.Add,null)}}
    ){padding->
        Column(Modifier.fillMaxSize().padding(padding)){
            if(vm.updateRelease!=null)TextButton(onClick={showSettings=true},modifier=Modifier.fillMaxWidth()){Text("Veyra ${vm.updateRelease?.version} disponível • Ver atualização")}
            if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(searching)OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),label={Text("Buscar em toda sua vida")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
            if(query.isNotBlank())FinanceSurface(vm,searchResults.any{financeSensitive(it,items)}){ItemList(searchResults,{vm.loadFullItem(it){full->detail=full}},{vm.loadFullItem(it){full->editor=full}},{deleting=it},vm)}
            else when(page){
                "Hoje"->TodayScreen(items,vm,{detail=it},{create(it)},::navigate)
                "Agenda"->AgendaScreen(items,{detail=it},{date->editor=Item(type="event",title="",date=date)},vm)
                "Finanças"->FinanceAccessGate(vm){FinanceScreen(items,vm,{detail=it},{create(it)})}
                "Notas"->NotesStudio(items,vm,{detail=it},{create(it)})
                "Mais"->{if(module=="hub")ModuleHub(vm,{module=it})else{
                    Row(Modifier.padding(horizontal=20.dp)){TextButton(onClick={module="hub"}){Text("← Todos os módulos")};TextButton(onClick={trash=!trash}){Text(if(trash)"Sair da lixeira"else "Lixeira")}}
                    if(trash)FinanceSurface(vm,all.any{it.deletedAt!=0L && financeSensitive(it,items)}){ItemList(all.filter{it.deletedAt!=0L && it.value("purged")!="yes" && !(it.title=="Registro excluído" && it.fields.isEmpty())},{if(it.type in FinancialDomain.financialTypes)vm.restoreFinance(it)else vm.save(it.copy(deletedAt=0))},{if(it.type in FinancialDomain.financialTypes)vm.restoreFinance(it)else vm.save(it.copy(deletedAt=0))},{},vm)}
                    else FinanceSurface(vm,module in FinancialDomain.financialTypes || module in setOf("routine","assistant","favorites","timeline","trip","vehicle","subscription_audit") || items.any{it.type==module && financeSensitive(it,items)}){when{module=="routine"->RoutineStudio(items,vm,{detail=it},::navigate);module in ExtraCatalog.specs.map{it.type}->ExtraStudio(module,items,vm,{detail=it},{editor=it},{deleting=it},{create(it)});else->when(module){"sketch"->SketchStudio(items,vm,{detail=it});"scanner"->ScannerStudio(vm);"decisions"->DecisionStudio(vm);"playroom"->GamesStudio();"worldclock"->WorldClockStudio(vm);"city"->WeatherScreen(items,vm);"task"->TasksStudio(items,vm,{detail=it},{create(it)});"tools"->ToolsScreen();"assistant"->AssistantScreen(vm);else->ModuleScreen(module,items,vm,{detail=it},{editor=it},{deleting=it},{create(it)})}}}
                }}
            }
        }
    }
    if(vm.preferences["name"].isNullOrBlank())Onboarding(vm)
    editor?.let{draft->when(draft.type){
        "income","expense","transfer","recurring_rule","subscription"->FinanceAccessGate(vm){FinanceEditor(draft,items,vm,{editor=null})}
        "account","card","budget","savings_goal","debt","investment","financial_asset","financial_category","financial_rule"->FinanceAccessGate(vm){FinanceEntityEditor(draft,items,vm,{editor=null})}
        else->FinanceSurface(vm,financeSensitive(draft,items)){ItemEditor(draft,items,{editor=null},{item->vm.save(item);editor=null},vm::autosave)}
    }}
    detail?.let{current->val item=all.firstOrNull{it.id==current.id} ?: current
        if(item.type in FinancialDomain.financialTypes)FinanceAccessGate(vm){FinanceDetail(item,financialItems,vm,FinancialDomain.currency(item),vm.preferences["financeHidden"]=="Sim",{detail=null},{editor=it;detail=null},{deleting=it;detail=null})}
        else ModalBottomSheet(onDismissRequest={detail=null}){FinanceSurface(vm,financeSensitive(item,items) || item.type in setOf("trip","vehicle","subscription_audit")){DetailScreen(item,items,vm,{editor=it;detail=null},{deleting=it;detail=null},{type->create(type,item.id);detail=null})}}
    }
    deleting?.let{i->FinanceSurface(vm,financeSensitive(i,items)){AlertDialog(onDismissRequest={deleting=null},title={Text("Mover para a lixeira?")},text={Text(i.title)},confirmButton={TextButton(onClick={vm.delete(i);deleting=null}){Text("Mover")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancelar")}})}}
    if(showSettings)ModalBottomSheet(onDismissRequest={showSettings=false}){FinanceAccessGate(vm){SettingsScreen(vm,notificationPermission)}}
    vm.error?.let{AlertDialog(onDismissRequest=vm::dismiss,title={Text("Não foi possível concluir")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismiss){Text("Entendi")}})}
}

@Composable private fun FinanceSurface(vm:VeyraViewModel,protected:Boolean,content:@Composable ()->Unit){if(protected)FinanceAccessGate(vm,content)else content()}
internal fun financeSensitive(item:Item,all:List<Item>):Boolean=item.type in FinancialDomain.financialTypes || item.value("financial")=="yes" || all.any{it.id==item.parentId && it.type in FinancialDomain.financialTypes}

@Composable fun Choice(label:String,options:List<String>,value:String,onSelect:(String)->Unit,display:(String)->String={it}){
    var expanded by remember{mutableStateOf(false)}
    Box(Modifier.padding(4.dp)){OutlinedButton(onClick={expanded=true}){Text("$label: ${display(value).ifBlank{"Selecionar"}}")};DropdownMenu(expanded,{expanded=false}){options.forEach{option->DropdownMenuItem(text={Text(display(option).ifBlank{"Nenhum"})},onClick={onSelect(option);expanded=false})}}}
}
@Composable fun ItemList(items:List<Item>,open:(Item)->Unit,edit:(Item)->Unit,delete:(Item)->Unit,vm:VeyraViewModel){
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(items.isEmpty())item{EmptyCard("Tudo começa com uma captura","Toque em Capturar para adicionar. Seus registros ficam no aparelho.")}
        items(items,key={it.id}){item->ItemCard(item,open,edit,delete,vm)};item{Spacer(Modifier.height(90.dp))}
    }
}
@Composable fun EmptyCard(title:String,body:String){OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=MaterialTheme.typography.titleMedium);Text(body)}}}
