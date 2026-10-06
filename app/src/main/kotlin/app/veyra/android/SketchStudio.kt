package app.veyra.android

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.veyra.model.Item
import kotlinx.coroutines.*

private data class InkStroke(val points:List<Offset>,val color:Int,val width:Float)
private fun renderInk(strokes:List<InkStroke>):Bitmap {
    val bitmap=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
    strokes.forEach{stroke->val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=stroke.color;strokeWidth=stroke.width*1024;strokeCap=android.graphics.Paint.Cap.ROUND;strokeJoin=android.graphics.Paint.Join.ROUND}
        if(stroke.points.size==1){val point=stroke.points.first();canvas.drawCircle(point.x*1024,point.y*1024,paint.strokeWidth/2,paint)}
        stroke.points.zipWithNext().forEach{(a,b)->canvas.drawLine(a.x*1024,a.y*1024,b.x*1024,b.y*1024,paint)}
    };return bitmap
}
@Composable fun SketchStudio(items:List<Item>,vm:VeyraViewModel,open:(Item)->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val strokes=remember{mutableStateListOf<InkStroke>()}
    var live by remember{mutableStateOf(emptyList<Offset>())};var color by remember{mutableStateOf(Color(0xFF25213A))};var width by remember{mutableFloatStateOf(5f)}
    var title by remember{mutableStateOf("")};var status by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var gallery by remember{mutableStateOf(false)}
    val saveFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->if(uri!=null)scope.launch{
        busy=true;val snapshot=strokes.toList();status=runCatching{withContext(Dispatchers.IO){val bitmap=renderInk(snapshot);try{context.contentResolver.openOutputStream(uri)?.use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)} ?: error("Arquivo indisponível")}finally{bitmap.recycle()}};"PNG exportado"}.getOrElse{it.message ?: "Não foi possível exportar"};busy=false
    }}
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Risque suas ideias.",style=MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!gallery,onClick={gallery=false},label={Text("Desenhar")});FilterChip(selected=gallery,onClick={gallery=true},label={Text("Galeria")})}
        if(gallery){
            val drawings=items.filter{it.type=="sketch"}.sortedByDescending{it.createdAt}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                if(drawings.isEmpty())item{EmptyCard("Espaço para criar","Seus desenhos guardados aparecerão aqui.")}
                items(drawings,key={it.id}){sketch->
                    val bitmap=remember(sketch.value("attachment")){runCatching{val bytes=android.util.Base64.decode(sketch.value("attachment"),android.util.Base64.NO_WRAP);android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size)}.getOrNull()}
                    Card(onClick={open(sketch)},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){bitmap?.let{Image(it.asImageBitmap(),sketch.title,Modifier.fillMaxWidth().height(240.dp))};Text(sketch.title,style=MaterialTheme.typography.titleMedium);Text(dateLabel(sketch.date))}}
                }
            }
        }else{
            OutlinedTextField(title,{title=it.take(200)},label={Text("Nome do desenho")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(Color(0xFF25213A),Color(0xFF8459DB),Color(0xFF18856B),Color(0xFFE46A54),Color(0xFF287AD2),Color.White).forEach{ink->Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(ink).clickable{color=ink}){if(color==ink)Text("●",color=if(ink==Color.White)Color.Black else Color.White,modifier=Modifier.padding(6.dp))}};Text(if(color==Color.White)"Borracha"else "Tinta",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))}
            Row(verticalAlignment=Alignment.CenterVertically){Text("${width.toInt()} px",style=MaterialTheme.typography.labelMedium);Slider(width,{width=it},valueRange=1f..20f,modifier=Modifier.weight(1f))}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Button(onClick={scope.launch{busy=true;val snapshot=strokes.toList();runCatching{withContext(Dispatchers.Default){val bitmap=renderInk(snapshot);try{val bytes=java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP)}finally{bitmap.recycle()}}}.onSuccess{data->vm.save(Item(type="sketch",title=title.ifBlank{"Meu desenho"},fields=mapOf("attachment" to data,"mime" to "image/png")));status="Desenho enviado para sua galeria"}.onFailure{status=it.message.orEmpty()};busy=false}},enabled=!busy && strokes.isNotEmpty()){Text("Guardar no Veyra")}
                OutlinedButton(onClick={saveFile.launch("${title.ifBlank{"veyra-desenho"}.replace(Regex("[^\\p{L}0-9_-]"),"-")}.png")},enabled=!busy && strokes.isNotEmpty()){Text("Exportar PNG")}
            }
            Row{TextButton(onClick={if(strokes.isNotEmpty())strokes.removeAt(strokes.lastIndex)},enabled=strokes.isNotEmpty()){Text("Desfazer traço")};TextButton(onClick={strokes.clear();live=emptyList()}){Text("Limpar")}}
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){val side=minOf(maxWidth,maxHeight)
                Canvas(Modifier.size(side).clip(RoundedCornerShape(16.dp)).background(Color.White).semantics{contentDescription="Área de desenho"}.pointerInput(color,width){
                    detectDragGestures(onDragStart={point->live=listOf(Offset((point.x/size.width).coerceIn(0f,1f),(point.y/size.height).coerceIn(0f,1f)))},onDragEnd={if(live.isNotEmpty()){strokes.add(InkStroke(live,color.toArgb(),width/1024));live=emptyList()}},onDragCancel={live=emptyList()}){change,_->change.consume();if(live.size<5000)live=live+Offset((change.position.x/size.width).coerceIn(0f,1f),(change.position.y/size.height).coerceIn(0f,1f))}
                }){(strokes.toList()+InkStroke(live,color.toArgb(),width/1024)).forEach{stroke->stroke.points.zipWithNext().forEach{(a,b)->drawLine(Color(stroke.color),Offset(a.x*size.width,a.y*size.height),Offset(b.x*size.width,b.y*size.height),stroke.width*size.width,StrokeCap.Round)}}}
            }
            if(status.isNotBlank())Text(status,style=MaterialTheme.typography.bodySmall)
            Text("Guarde ou exporte antes de sair.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
