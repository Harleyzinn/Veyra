package app.veyra.android
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import app.veyra.model.*
import androidx.compose.ui.window.DialogProperties

@Composable fun ItemEditor(initial:Item,items:List<Item>,dismiss:()->Unit,save:(Item)->Unit,autosave:(Item)->Unit={}){
    var type by remember{mutableStateOf(initial.type)};var title by remember{mutableStateOf(initial.title)};var notes by remember{mutableStateOf(initial.notes)}
    var date by remember{mutableStateOf(initial.date)};var tags by remember{mutableStateOf(initial.tags)};var parent by remember{mutableStateOf(initial.parentId)}
    val fields=remember{mutableStateMapOf<String,String>().apply{putAll(initial.fields)}};var error by remember{mutableStateOf("")}
    var markdown by remember{mutableStateOf(false)}
    LaunchedEffect(title,notes,date,tags,parent,fields.toMap()){if(type in listOf("note","journal") && title.isNotBlank()){kotlinx.coroutines.delay(1200);val draft=initial.copy(type=type,title=title.trim(),notes=notes,date=date.trim(),tags=tags,parentId=parent,fields=fields.toMap());if(runCatching{Workspace.validate(draft,Registry.spec(type))}.isSuccess)autosave(draft)}}
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(shape=MaterialTheme.shapes.extraLarge,modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=20.dp).imePadding().fillMaxHeight(.96f)){
        Column(Modifier.padding(22.dp)){
            Text(if(initial.title.isBlank())"NOVO REGISTRO"else "SEU REGISTRO",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Text(if(initial.title.isBlank())"Tire a ideia do papel."else "Ajuste os detalhes.",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=6.dp,bottom=14.dp))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                item{Choice("Tipo",Registry.specs.filter{it.type !in listOf("city","tools","assistant","routine","sketch","scanner","decisions","playroom","worldclock")}.map{it.type},type,{type=it}){Registry.spec(it).label}}
                item{OutlinedTextField(title,{title=it},label={Text("Título")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                item{OutlinedTextField(date,{date=it},label={Text("Data • AAAA-MM-DD, ou vazia")},modifier=Modifier.fillMaxWidth(),singleLine=true)}
                item{OutlinedTextField(notes,{notes=it},label={Text(if(type=="flashcard")"Dicas / explicação"else "Texto / detalhes • Markdown")},modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=8)}
                if(type in listOf("note","journal"))item{TextButton(onClick={markdown=!markdown}){Text(if(markdown)"Fechar prévia"else "Prévia Markdown")};if(markdown)MarkdownText(notes)}
                item{OutlinedTextField(tags,{tags=it},label={Text("Tags • #faculdade #viagem")},modifier=Modifier.fillMaxWidth())}
                item{Choice("Vincular",listOf("")+items.filter{it.type in Registry.contexts && it.id!=initial.id}.map{it.id},parent,{parent=it}){id->items.firstOrNull{it.id==id}?.title ?: "Nenhum"}}
                Registry.spec(type).fields.forEach{f->item{
                    when(f.kind){
                        FieldKind.CHOICE->Choice(f.label,listOf("")+f.choices,fields[f.key].orEmpty(),{fields[f.key]=it})
                        FieldKind.REFERENCE->Choice(f.label,listOf("")+items.filter{it.type in Registry.referenceTypes(f.key)}.map{it.id},fields[f.key].orEmpty(),{fields[f.key]=it}){id->items.firstOrNull{it.id==id}?.title ?: "Nenhum"}
                        else->OutlinedTextField(fields[f.key].orEmpty(),{fields[f.key]=it.replace(',','.') .takeIf{f.kind in listOf(FieldKind.MONEY,FieldKind.DECIMAL)} ?: it},label={Text(f.label+(if(f.required)" *"else ""))},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=if(f.kind in listOf(FieldKind.MONEY,FieldKind.DECIMAL))KeyboardType.Decimal else KeyboardType.Text),maxLines=if(f.key in listOf("ingredients","lines"))8 else 3)
                    }
                }}
                if(error.isNotBlank())item{Text(error,color=MaterialTheme.colorScheme.error)}
            }
            Row{TextButton(onClick=dismiss,modifier=Modifier.weight(1f)){Text("Cancelar")};Button(onClick={try{
                val result=initial.copy(type=type,title=title.trim(),notes=notes,date=date.trim(),tags=tags,parentId=parent,fields=fields.toMap())
                Workspace.validate(result,Registry.spec(type));if(result.value("reminder").isNotBlank())java.time.LocalTime.parse(result.value("reminder"));save(result)
            }catch(e:Exception){error=e.message ?: "Confira os campos"}},modifier=Modifier.weight(1f)){Text("Salvar")}}
        }
    }}
}
@Composable fun MarkdownText(text:String){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){text.lines().forEach{line->val cleaned=line.removePrefix("### ").removePrefix("## ").removePrefix("# ");val rich=androidx.compose.ui.text.buildAnnotatedString{var start=0;Regex("\\*\\*(.+?)\\*\\*").findAll(cleaned).forEach{match->append(cleaned.substring(start,match.range.first));pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight=androidx.compose.ui.text.font.FontWeight.Bold));append(match.groupValues[1]);pop();start=match.range.last+1};append(cleaned.substring(start))};Text(rich,style=if(line.startsWith("#"))MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium)}}}
