package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import app.veyra.model.NavigationPolicy

data class AppDestination(val id:String,val label:String,val group:String)
object AppNavigation {
    val destinations=(listOf(
        AppDestination("today","Hoje","Principal"),AppDestination("agenda","Agenda","Produtividade"),
        AppDestination("finance","Finanças","Finanças"),AppDestination("notes","Notas","Produtividade"),
        AppDestination("hub","Explorar","Principal"),AppDestination("routine","Central de rotina","Produtividade"),
        AppDestination("favorites","Favoritos de registros","Organização"),AppDestination("timeline","Linha do tempo","Organização"),
        AppDestination("settings","Configurações","Sistema"),AppDestination("navigation","Personalizar menus","Sistema")
    )+Registry.specs.filter{it.type!="note"}.map{AppDestination(it.type,it.label,NavigationPolicy.area(it.group))}).distinctBy{it.id}
    fun resolve(id:String)=if(id=="note")"notes"else id
    val ids=destinations.map{it.id}.toSet()+"note"
    fun destination(id:String)=destinations.firstOrNull{it.id==resolve(id)} ?: destinations.first{it.id=="hub"}
    fun matches(d:AppDestination,query:String)=NavigationPolicy.matches("${d.label} ${d.group} "+if(d.id=="settings")"perfil aparência preferencias integração sincronização segurança backup atualizações"else "",query)
    fun search(query:String)=destinations.filter{matches(it,query)}.take(12)
    fun inProfile(id:String,profile:String?):Boolean {
        val group=Registry.spec(if(id=="notes")"note"else id).group
        val groups=when(profile){"Essencial"->setOf("Produtividade","Notas e diário","Rotina","Clima","Ferramentas");"Produtividade"->setOf("Produtividade","Notas e diário","Rotina","Sistema","Ferramentas","Clima");"Estudos"->setOf("Estudos","Produtividade","Notas e diário","Rotina","Clima","Ferramentas");"Finanças"->setOf("Finanças","Casa e veículo","Sistema","Clima","Ferramentas");else->return true}
        return id in setOf("today","agenda","finance","routine","favorites","timeline","settings","navigation") || group in groups
    }
    fun bottom(prefs:Map<String,String>)=(NavigationPolicy.ids(prefs["navBottom"].orEmpty(),ids).map(::resolve).distinct().filter{it !in setOf("hub","settings","navigation")}.take(4).ifEmpty{listOf("today","agenda","finance","notes")}+"hub")
}

@Composable fun NavigationCustomization(vm:VeyraViewModel){
    var query by rememberSaveable{mutableStateOf("")}
    val ordered=NavigationPolicy.ordered(vm.preferences["navOrder"].orEmpty(),AppNavigation.destinations.map{it.id})
    val favorites=NavigationPolicy.ids(vm.preferences["favoriteModules"].orEmpty(),AppNavigation.ids).map(AppNavigation::resolve).distinct()
    val hidden=NavigationPolicy.ids(vm.preferences["hidden"].orEmpty(),AppNavigation.ids).map(AppNavigation::resolve).distinct()
    val bottom=AppNavigation.bottom(vm.preferences).filter{it!="hub"}
    fun toggle(key:String,current:List<String>,id:String){vm.pref(key,(if(id in current)current.filter{it!=id}else current+id).joinToString(","))}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{PageHeading("DO SEU JEITO","Personalizar menus","Ocultar atalhos não remove módulos. A busca e o catálogo completo continuam disponíveis.")}
        item{Choice("Página inicial",AppNavigation.destinations.map{it.id},vm.preferences["navHome"].orEmpty().ifBlank{"today"},{vm.pref("navHome",it)}){AppNavigation.destination(it).label}}
        item{OutlinedButton(onClick={vm.navigationDefaults()},enabled=!vm.busy){Text("Restaurar organização padrão")}}
        item{OutlinedTextField(query,{query=it},label={Text("Encontrar página")},modifier=Modifier.fillMaxWidth())}
        items(ordered.filter{AppNavigation.matches(AppNavigation.destination(it),query)},key={it}){id->val d=AppNavigation.destination(id)
            OutlinedCard{Column(Modifier.padding(12.dp)){
                Text(d.label,style=MaterialTheme.typography.titleSmall);Text(d.group,style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(onClick={toggle("favoriteModules",favorites,id)},enabled=!vm.busy){Icon(if(id in favorites)Icons.Default.Star else Icons.Default.StarBorder,"Favoritar ${d.label}")}
                    if(id !in setOf("hub","navigation","settings")){Checkbox(id !in hidden,{toggle("hidden",hidden,id)},enabled=!vm.busy);Text("Atalho",style=MaterialTheme.typography.labelSmall)}
                    Spacer(Modifier.weight(1f));val n=ordered.indexOf(id)
                    IconButton(onClick={val next=ordered.toMutableList();java.util.Collections.swap(next,n,n-1);vm.pref("navOrder",next.joinToString(","))},enabled=!vm.busy&&n>0){Icon(Icons.Default.ArrowUpward,"Subir ${d.label}")}
                    IconButton(onClick={val next=ordered.toMutableList();java.util.Collections.swap(next,n,n+1);vm.pref("navOrder",next.joinToString(","))},enabled=!vm.busy&&n<ordered.lastIndex){Icon(Icons.Default.ArrowDownward,"Descer ${d.label}")}
                }
                if(id !in setOf("hub","navigation","settings"))Row(verticalAlignment=Alignment.CenterVertically){Checkbox(id in bottom,{if(id in bottom&&bottom.size>1)vm.pref("navBottom",bottom.filter{it!=id}.joinToString(","))else if(id !in bottom&&bottom.size<4)vm.pref("navBottom",(bottom+id).joinToString(","))},enabled=!vm.busy&&(id in bottom&&bottom.size>1||id !in bottom&&bottom.size<4));Text("Barra principal (até 4)",style=MaterialTheme.typography.bodySmall)}
            }}
        }
        item{Text("Organização dos menus por plataforma, sincronizada com a conta quando conectado. Recentes e contagens de uso ficam neste aparelho.",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(80.dp))}
    }
}

@Composable fun NavigationHub(vm:VeyraViewModel,select:(String)->Unit){
    var query by rememberSaveable{mutableStateOf("")};var showAll by rememberSaveable{mutableStateOf(false)}
    val hidden=NavigationPolicy.ids(vm.preferences["hidden"].orEmpty(),AppNavigation.ids).map(AppNavigation::resolve).distinct()
    val favorites=NavigationPolicy.ids(vm.preferences["favoriteModules"].orEmpty(),AppNavigation.ids).map(AppNavigation::resolve).distinct()
    val recent=NavigationPolicy.ids(vm.preferences["navRecent"].orEmpty(),AppNavigation.ids).map(AppNavigation::resolve).distinct()
    val counts=vm.preferences["navUsage"].orEmpty().split(',').mapNotNull{v->val p=v.split(':');if(p.size==2)p[0] to (p[1].toIntOrNull()?:0)else null}.toMap()
    val frequent=counts.keys.filter{it in AppNavigation.ids}.sortedByDescending{counts[it]}.take(4)
    val collapsed=vm.preferences["navCollapsed"].orEmpty().split(',')
    val ordered=NavigationPolicy.ordered(vm.preferences["navOrder"].orEmpty(),AppNavigation.destinations.map{it.id}).map{AppNavigation.destination(it)}
    val matches=ordered.filter{it.id!="hub" && (showAll || query.isNotBlank() || it.id !in hidden && AppNavigation.inProfile(it.id,vm.preferences["profile"])) && AppNavigation.matches(it,query)}
    @Composable fun shortcut(id:String){val d=AppNavigation.destination(id);OutlinedButton(onClick={select(id)},modifier=Modifier.fillMaxWidth()){Text(d.label)}}
    LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{PageHeading("SEU ESPAÇO","Tudo no lugar.","Explore por área ou encontre uma função pelo nome.")}
        item{OutlinedTextField(query,{query=it},label={Text("Encontrar página ou módulo")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth(),singleLine=true)}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(showAll,{showAll=it});Text("Catálogo completo",Modifier.weight(1f));IconButton(onClick={select("navigation")}){Icon(Icons.Default.Tune,"Personalizar menus")}}}
        if(query.isBlank()){
            if(favorites.isNotEmpty()){item{SectionTitle("Favoritos de páginas")};items(favorites,key={"fav-$it"}){shortcut(it)}}
            if(recent.isNotEmpty()){item{SectionTitle("Recentes")};items(recent.take(3),key={"recent-$it"}){shortcut(it)}}
            if(frequent.isNotEmpty()){item{SectionTitle("Mais utilizados")};items(frequent,key={"used-$it"}){shortcut(it)}}
        }
        matches.groupBy{it.group}.forEach{(group,list)->
            item{TextButton(onClick={vm.pref("navCollapsed",(if(group in collapsed)collapsed.filter{it!=group}else collapsed+group).filter{it.isNotBlank()}.joinToString(","))},modifier=Modifier.fillMaxWidth()){Text(group,Modifier.weight(1f));Icon(if(group in collapsed)Icons.Default.ExpandMore else Icons.Default.ExpandLess,if(group in collapsed)"Expandir $group"else"Recolher $group")}}
            if(query.isNotBlank()||group !in collapsed)items(list,key={it.id}){d->OutlinedCard(onClick={select(d.id)},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(d.id),size=36);Text(d.label,Modifier.weight(1f).padding(horizontal=12.dp),style=MaterialTheme.typography.titleSmall);IconButton(onClick={vm.pref("favoriteModules",(if(d.id in favorites)favorites.filter{it!=d.id}else favorites+d.id).joinToString(","))},enabled=!vm.busy){Icon(if(d.id in favorites)Icons.Default.Star else Icons.Default.StarBorder,"Favoritar ${d.label}")}}}}
        }
        if(matches.isEmpty())item{EmptyCard("Nenhum módulo encontrado","Tente outro nome ou ative o catálogo completo.")}
        item{Spacer(Modifier.height(90.dp))}
    }
}
