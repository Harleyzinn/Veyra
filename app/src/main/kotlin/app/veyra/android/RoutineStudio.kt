package app.veyra.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import java.time.LocalDate

@Composable fun RoutineStudio(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,navigate:(String)->Unit){
    val today=LocalDate.now();val pending=LifeInsights.pending(items,today)
    val daily=items.filter{it.type in LifeInsights.actionable && it.date==today.toString()}
    val completed=daily.count{it.done}
    LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("CENTRAL DE ROTINA","Cuide do seu dia.","Casa, bem-estar e planos em um único lugar.")}
        item{StudioCard{Text("Seu progresso de hoje",style=MaterialTheme.typography.titleLarge);Text("$completed de ${daily.size} atividades concluídas");LinearProgressIndicator(progress={LifeInsights.progress(completed.toLong(),daily.size.toLong())},modifier=Modifier.fillMaxWidth());Text("${pending.count{it.date<today.toString()}} pendências de dias anteriores",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        item{SectionTitle("Acesso rápido")}
        listOf("checklist","meal","mood","medicine","savings_goal","bill","course","countdown").chunked(2).forEach{row->item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{type->Card(onClick={navigate(type)},modifier=Modifier.weight(1f)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){IconBadge(moduleIcon(type));Text(Registry.spec(type).label,style=MaterialTheme.typography.titleSmall)}}}}}}
        item{SectionTitle("Hoje e atrasadas")}
        if(pending.isEmpty())item{EmptyCard("Sua rotina está em dia","Adicione atividades nos módulos e acompanhe tudo por aqui.")}
        items(pending.take(40),key={it.id}){i->StudioCard{Text(Registry.spec(i.type).label,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium);Text(i.title,style=MaterialTheme.typography.titleMedium);Text(dateLabel(i.date));Row{TextButton(onClick={open(i)}){Text("Detalhes")};if(i.type=="bill")TextButton(onClick={vm.settleBill(i.id)},enabled=!vm.busy){Text("Pagar e registrar")}else TextButton(onClick={vm.complete(i)},enabled=!vm.busy){Text("Concluir")}}}}
        item{Spacer(Modifier.height(90.dp))}
    }
}

@Composable fun ExtraStudio(type:String,items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit,edit:(Item)->Unit,delete:(Item)->Unit,create:(String)->Unit){
    var filter by rememberSaveable(type){mutableStateOf("Todos")};var selectedDate by rememberSaveable(type){mutableStateOf(LocalDate.now().toString())};var group by rememberSaveable(type){mutableStateOf("Todas")}
    val all=items.filter{it.type==type};val today=LocalDate.now()
    val data=all.filter{(filter!="Pendentes" || !it.done) && (filter!="Concluídos" || it.done) && (type!="meal" || it.date==selectedDate) && (type!="checklist" || group=="Todas" || it.value("list")==group)}.sortedWith(compareBy<Item>{it.done}.thenBy{it.date})
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading(Registry.spec(type).group,Registry.spec(type).label,when(type){
            "checklist"->"Uma lista para cada plano. Um passo de cada vez."
            "meal"->"Planeje o que comer e prepare sua lista de compras."
            "savings_goal"->"Transforme uma vontade em um plano de economia."
            "debt"->"Acompanhe o que falta pagar e receber."
            "bill"->"Veja seus vencimentos e registre cada pagamento."
            "mood"->"Faça uma pausa para perceber como você está."
            "medicine"->"Organize os horários e as doses prescritas."
            "course"->"Dê espaço ao aprendizado e acompanhe sua evolução."
            "countdown"->"Os próximos momentos especiais começam aqui."
            "subscription_audit"->"Descubra quais serviços ainda fazem sentido para você."
            "job"->"Acompanhe cada oportunidade e seu próximo passo."
            else->"Organize os detalhes que fazem parte da sua vida."
        })}
        item{Button(onClick={create(type)}){Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Adicionar registro")}}
        if(type=="checklist")item{Choice("Lista",listOf("Todas")+all.map{it.value("list")}.filter{it.isNotBlank()}.distinct(),group,{group=it})}
        if(type=="meal")item{StudioCard{Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={selectedDate=LocalDate.parse(selectedDate).minusDays(1).toString()}){Icon(Icons.Default.ChevronLeft,"Dia anterior")};Text(dateLabel(selectedDate),Modifier.weight(1f));IconButton(onClick={selectedDate=LocalDate.parse(selectedDate).plusDays(1).toString()}){Icon(Icons.Default.ChevronRight,"Próximo dia")}};Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){(0..6).forEach{n->val day=today.plusDays(n.toLong()).toString();FilterChip(selected=selectedDate==day,onClick={selectedDate=day},label={Text(dateLabel(day))})}}}}
        if(Registry.spec(type).checkable)item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Todos","Pendentes","Concluídos").forEach{value->FilterChip(selected=filter==value,onClick={filter=value},label={Text(value)})}}}
        if(type in setOf("bill","debt","savings_goal","subscription_audit"))item{StudioCard{Text(when(type){"bill"->"Total a pagar";"debt"->"Saldo das dívidas a pagar";"savings_goal"->"Total reservado";else->"Economia mensal possível"},style=MaterialTheme.typography.titleMedium);val total=all.filter{!it.done}.filter{type!="debt" || it.value("direction")!="A receber"}.filter{type!="subscription_audit" || it.value("use") in setOf("Raramente","Nunca")}.fold(0L){sum,i->Math.addExact(sum,when(type){"debt"->LifeInsights.remaining(i,"paid");"savings_goal"->i.cents("saved");else->i.cents()})};Text(money(total),style=MaterialTheme.typography.headlineMedium);Text(if(type=="bill")"Pagar cria uma despesa na planilha. Confira a conta antes de confirmar." else "Valores informados por você; sem conexão bancária.",style=MaterialTheme.typography.bodySmall)}}
        if(type=="mood" && all.isNotEmpty())item{StudioCard{Text("Últimos 7 dias",style=MaterialTheme.typography.titleMedium);val week=all.filter{it.date>=today.minusDays(6).toString() && it.date<=today.toString()};Text("${week.size} registros • ${week.count{it.value("mood") in setOf("Bem","Muito bem")}} com humor positivo");Text("Um espaço de reflexão pessoal.",style=MaterialTheme.typography.bodySmall)}}
        if(data.isEmpty())item{EmptyCard("Pronto para começar","Toque em Adicionar registro para montar seu espaço.")}
        items(data,key={it.id}){i->
            StudioCard{
                var menu by remember{mutableStateOf(false)}
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconBadge(moduleIcon(type),size=38);Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable{open(i)}){Text(i.title,style=MaterialTheme.typography.titleMedium);Text(dateLabel(i.date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick={vm.save(i.copy(favorite=!i.favorite))}){Icon(if(i.favorite)Icons.Default.Star else Icons.Default.StarBorder,"Favoritar",tint=MaterialTheme.colorScheme.tertiary)}
                    Box{IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Opções")};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Detalhes")},onClick={menu=false;open(i)});DropdownMenuItem(text={Text("Editar")},onClick={menu=false;edit(i)});DropdownMenuItem(text={Text("Mover à lixeira")},onClick={menu=false;delete(i)})}}
                }
                if(i.notes.isNotBlank())Text(i.notes,maxLines=3,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(type !in setOf("debt","savings_goal") && i.value("amount").isNotBlank())Text(money(i.cents()),style=MaterialTheme.typography.titleLarge)
                if(Registry.spec(type).checkable && type!="bill")TextButton(onClick={vm.complete(i)},enabled=!vm.busy){Text(if(i.done)"✓ Concluído • reabrir"else "Concluir")}
                if(type=="bill" && i.done)Text("✓ Pago • despesa registrada",color=MaterialTheme.colorScheme.secondary)
                when(type){
                    "checklist"->if(i.value("list").isNotBlank())Text("Lista: ${i.value("list")}")
                    "countdown"->{val days=runCatching{LifeInsights.daysUntil(i,today)}.getOrNull();Text(when{days==null->"Defina uma data";days>0->"Faltam $days dias";days==0L->"É hoje!";else->"Há ${-days} dias"},style=MaterialTheme.typography.titleLarge)}
                    "savings_goal","debt"->{val key=if(type=="debt")"paid"else "saved";Text("${money(i.cents(key))} de ${money(i.cents())}");LinearProgressIndicator(progress={LifeInsights.progress(i.cents(key),i.cents())},modifier=Modifier.fillMaxWidth());Text("Faltam ${money(LifeInsights.remaining(i,key))}");TextButton(onClick={edit(i)}){Text(if(type=="debt")"Atualizar quitação"else "Atualizar reserva")}}
                    "bill"->if(!i.done)Button(onClick={vm.settleBill(i.id)},enabled=!vm.busy){Text("Pagar e registrar despesa")}
                    "meal"->{Text(i.value("meal"));if(i.value("ingredients").isNotBlank()){Text(i.value("ingredients"));TextButton(onClick={vm.mealShopping(i.id)},enabled=!vm.busy){Text("Adicionar ingredientes às compras")}}}
                    "course"->{Text("${i.value("completed").ifBlank{"0"}} de ${i.value("lessons").ifBlank{"0"}} aulas");LinearProgressIndicator(progress={LifeInsights.progress(i.number("completed").toLong(),i.number("lessons").toLong())},modifier=Modifier.fillMaxWidth());TextButton(onClick={edit(i)}){Text("Atualizar progresso")}}
                    "mood"->Text("${i.value("mood")} · energia ${i.value("energy")}")
                    "medicine"->Text("${i.value("dose")} · ${i.value("time")} · estoque ${i.value("stock")}")
                    "measurement"->Text("${i.value("weight")} kg · cintura ${i.value("waist")} cm")
                    "exercise"->Text("${i.value("sets")} séries × ${i.value("reps")} repetições · ${i.value("load")} kg")
                    "job"->Text("${i.value("company")} · ${i.value("status")}")
                }
            }
        }
    }
}
