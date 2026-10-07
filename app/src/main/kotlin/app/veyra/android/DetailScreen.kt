package app.veyra.android
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.veyra.model.*
import java.time.*

@Composable fun DetailScreen(item:Item,items:List<Item>,vm:VeyraViewModel,edit:(Item)->Unit,delete:(Item)->Unit,create:(String)->Unit){
    var installments by remember{mutableStateOf("3")}
    val attachment=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{uri->vm.attach(uri,item)}}
    val download=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(item.value("mime").ifBlank{"application/octet-stream"})){it?.let{uri->vm.attachmentTo(uri,item)}}
    val linked=items.filter{it.parentId==item.id || it.value("account")==item.id || it.value("destination")==item.id || it.value("card")==item.id}
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text(item.title,style=MaterialTheme.typography.headlineMedium);Text("${Registry.spec(item.type).label} • ${item.date}",color=MaterialTheme.colorScheme.primary)}
        if(item.notes.isNotBlank())item{MarkdownText(item.notes)}
        if(item.tags.isNotBlank())item{Text(item.tags)}
        Registry.spec(item.type).fields.filter{item.value(it.key).isNotBlank()}.forEach{f->item{Text("${f.label}: "+if(f.kind==FieldKind.REFERENCE)items.firstOrNull{it.id==item.value(f.key)}?.title.orEmpty()else item.value(f.key))}}
        if(item.parentId.isNotBlank())item{Text("Vinculado a: ${items.firstOrNull{it.id==item.parentId}?.title ?: "registro não disponível"}")}
        when(item.type){
            "bill"->if(!item.done)item{Button(onClick={vm.settleBill(item.id)},enabled=!vm.busy){Text("Pagar e registrar despesa")}}
            "trip"->item{val days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(item.date));Text(if(days>=0)"Faltam $days dias"else "Viagem iniciada há ${-days} dias");Text("Gastos vinculados: ${money(Workspace.expenses(linked))} / ${money(item.cents())}")}
            "subject"->item{Text("Média ponderada: ${Workspace.weightedGrade(linked.filter{it.type=="grade"})}");val attendance=linked.filter{it.type=="attendance"};Text("Presenças: ${attendance.count{it.value("present")=="Sim"}} / ${attendance.size}");Text("${linked.filter{it.type=="focus"}.sumOf{it.number("minutes").toInt()}} minutos estudados")}
            "goal"->item{val target=item.number("target").toFloat();val progress=item.number("progress").toFloat();LinearProgressIndicator(progress={if(target>0)(progress/target).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth());Text("$progress / $target")}
            "book"->item{Text("Progresso: ${item.value("progress")} / ${item.value("pages")} páginas")}
            "birthday"->item{if(item.value("birth").isNotBlank()){val birth=LocalDate.parse(item.value("birth"));val now=LocalDate.now();var next=birth.withYear(now.year);if(next.isBefore(now))next=next.plusYears(1);Text("${Period.between(birth,now).years} anos • faltam ${java.time.temporal.ChronoUnit.DAYS.between(now,next)} dias")}}
            "vehicle"->item{val fills=linked.filter{it.type=="fuel"}.sortedBy{it.number("odometer")};if(fills.size>=2){val distance=fills.last().number("odometer")-fills.first().number("odometer");val liters=fills.drop(1).sumOf{it.number("liters").toDouble()};if(liters>0)Text("Consumo estimado: %.2f km/L • pressupõe tanques completos".format(distance.toDouble()/liters))};Text("Abastecimentos: ${money(linked.filter{it.type=="fuel"}.sumOf{it.cents()})}")}
            "habit"->item{Text("Sequência: ${Workspace.streak(items,item.id,LocalDate.now())} dias");TextButton(onClick={vm.checkin(item)}){Text("Registrar / desfazer hoje")}}
            "template"->item{Button(onClick={vm.applyTemplate(item)}){Text("Criar tarefas deste template")}}
            "recipe"->item{Button(onClick={vm.ingredients(item)}){Text("Enviar ingredientes à lista de compras")}}
            "expense"->item{Row{OutlinedTextField(installments,{installments=it},label={Text("Parcelas")},modifier=Modifier.weight(1f));TextButton(onClick={installments.toIntOrNull()?.takeIf{it in 1..120}?.let{vm.installment(item,it)}}){Text("Criar parcelas")}}}
        }
        if(item.type!="bill" && (Registry.spec(item.type).checkable || item.type in setOf("income","expense") && item.value("planned")=="Sim"))item{Button(onClick={vm.complete(item)},modifier=Modifier.fillMaxWidth()){Text(if(item.type in setOf("income","expense")){if(item.done)"Voltar a previsto"else "Marcar como realizado"}else if(item.done)"Reabrir"else "Concluir")}}
        item{Row{Button(onClick={edit(item)}){Text("Editar")};TextButton(onClick={delete(item)}){Text("Mover à lixeira")}}}
        item{OutlinedButton(onClick={attachment.launch(arrayOf("image/*","application/pdf","text/plain"))}){Text("Anexar arquivo • até 10 MB")}}
        if(item.value("attachment").isNotBlank() || item.value("hasAttachment")=="yes" || item.value("cloudAttachmentPath").isNotBlank())item{Button(onClick={download.launch(item.title+when(item.value("mime")){"application/pdf"->".pdf";"image/png"->".png";"image/jpeg"->".jpg";else->".bin"})}){Text("Salvar anexo")}}
        if(item.type in Registry.contexts){
            item{Text("Tudo conectado",style=MaterialTheme.typography.titleLarge);Row{TextButton(onClick={create("task")}){Text("+ Tarefa")};TextButton(onClick={create("note")}){Text("+ Nota")};TextButton(onClick={create("expense")}){Text("+ Gasto")}}}
            item{Choice("Adicionar vínculo",listOf("event","document","reservation","packing","grade","exam","focus","fuel","maintenance"),"",{create(it)}){Registry.spec(it).label}}
        }
        items(linked,key={it.id}){child->Card(onClick={edit(child)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(child.title);Text("${Registry.spec(child.type).label} • ${child.date}")}}}
        item{Spacer(Modifier.height(60.dp))}
    }
}
