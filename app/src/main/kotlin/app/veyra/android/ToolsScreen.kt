package app.veyra.android
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.veyra.model.Toolbox
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.*

@Composable fun ToolsScreen(){val context=LocalContext.current;val scope=rememberCoroutineScope()
    var mode by remember{mutableStateOf("Calculadora")};var input by remember{mutableStateOf("")};var result by remember{mutableStateOf("")}
    var from by remember{mutableStateOf("m")};var to by remember{mutableStateOf("km")};var monthly by remember{mutableStateOf("0")};var rate by remember{mutableStateOf("1")};var months by remember{mutableStateOf("12")}
    var passwordLength by remember{mutableFloatStateOf(20f)};var symbols by remember{mutableStateOf(true)};var bitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    var seconds by remember{mutableLongStateOf(0)};var elapsed by remember{mutableLongStateOf(0)};var started by remember{mutableLongStateOf(0)};var running by remember{mutableStateOf(false)};val laps=remember{mutableStateListOf<Long>()}
    LaunchedEffect(running){while(running){seconds=(elapsed+android.os.SystemClock.elapsedRealtime()-started)/1000;kotlinx.coroutines.delay(100)}}
    fun run(action:()->String){result=runCatching(action).getOrElse{it.message ?: "Confira os valores"}}
    val readQr=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{result=withContext(Dispatchers.IO){runCatching{
        val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};context.contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,options)}
        options.inJustDecodeBounds=false;options.inSampleSize=(maxOf(options.outWidth,options.outHeight)/2048).coerceAtLeast(1)
        val image=context.contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,options)} ?: error("Imagem inválida")
        val pixels=IntArray(image.width*image.height);image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width,image.height,pixels)))).text
    }.getOrElse{"Não foi possível ler o código nesta imagem"}}}}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Ferramentas de bolso",style=MaterialTheme.typography.headlineMedium);Choice("Ferramenta",listOf("Calculadora","Conversor","Juros compostos","Texto","Senhas","QR Code","Cronômetro","Datas"),mode,{mode=it;result=""})}
        if(mode !in listOf("Senhas","Cronômetro"))item{OutlinedTextField(input,{input=it},label={Text(when(mode){"Calculadora"->"Expressão • sqrt(9), sin(pi/2), 2^3";"Juros compostos"->"Valor inicial";"Datas"->"Primeira data • AAAA-MM-DD";else->"Valor / texto"})},modifier=Modifier.fillMaxWidth(),minLines=if(mode=="Texto")3 else 1)}
        when(mode){
            "Calculadora"->item{Button(onClick={run{Toolbox.calculate(input).toString()}}){Text("Calcular")};Text("Funções em radianos: sin, cos, tan, sqrt, log, ln, abs. Operadores + − * / ^ %.",style=MaterialTheme.typography.bodySmall)}
            "Conversor"->item{val units=Toolbox.units.keys.toList()+listOf("°C","°F","K");Choice("De",units,from,{from=it});Choice("Para",units,to,{to=it});Button(onClick={run{Toolbox.convert(input.replace(',','.').toDouble(),from,to).toString()+" $to"}}){Text("Converter")}}
            "Juros compostos"->item{OutlinedTextField(monthly,{monthly=it},label={Text("Aporte mensal")},modifier=Modifier.fillMaxWidth());OutlinedTextField(rate,{rate=it},label={Text("Juros mensais • %")},modifier=Modifier.fillMaxWidth());OutlinedTextField(months,{months=it},label={Text("Meses")},modifier=Modifier.fillMaxWidth());Button(onClick={run{"%.2f".format(Toolbox.compound(input.replace(',','.').toDouble(),monthly.replace(',','.').toDouble(),rate.replace(',','.').toDouble(),months.toInt()))}}){Text("Simular")};Text("Simulação matemática com aporte no fim do mês; não representa promessa de rendimento.",style=MaterialTheme.typography.bodySmall)}
            "Texto"->item{Text("${input.length} caracteres • ${input.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.size} palavras");Choice("Ação",listOf("MAIÚSCULAS","minúsculas","Limpar espaços","Ordenar linhas","Remover duplicadas"),"",{action->result=when(action){"MAIÚSCULAS"->input.uppercase();"minúsculas"->input.lowercase();"Limpar espaços"->input.trim().replace(Regex("[ \\t]+")," ");"Ordenar linhas"->input.lines().sorted().joinToString("\n");else->input.lines().distinct().joinToString("\n")}})}
            "Senhas"->item{Text("${passwordLength.toInt()} caracteres");Slider(passwordLength,{passwordLength=it},valueRange=8f..64f,steps=55);Row{Checkbox(symbols,{symbols=it});Text("Incluir símbolos",Modifier.padding(top=12.dp))};Button(onClick={result=Toolbox.password(passwordLength.toInt(),symbols)}){Text("Gerar localmente")};Text("Não é enviada a nenhum servidor e não é salva no app.",style=MaterialTheme.typography.bodySmall)}
            "QR Code"->{item{Row{Button(onClick={run{val matrix=MultiFormatWriter().encode(input,BarcodeFormat.QR_CODE,600,600);val pixels=IntArray(600*600){n->if(matrix[n%600,n/600])android.graphics.Color.BLACK else android.graphics.Color.WHITE};bitmap=android.graphics.Bitmap.createBitmap(pixels,600,600,android.graphics.Bitmap.Config.ARGB_8888);"QR criado"}}){Text("Gerar QR")};TextButton(onClick={readQr.launch("image/*")}){Text("Ler imagem")}}};bitmap?.let{b->item{Image(b.asImageBitmap(),"QR Code gerado",Modifier.fillMaxWidth().height(260.dp))}}}
            "Cronômetro"->item{Text("%02d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60),style=MaterialTheme.typography.headlineLarge);Row{Button(onClick={if(running)elapsed+=android.os.SystemClock.elapsedRealtime()-started else started=android.os.SystemClock.elapsedRealtime();running=!running}){Text(if(running)"Pausar"else "Iniciar")};TextButton(onClick={laps.add(seconds)}){Text("Volta")};TextButton(onClick={running=false;seconds=0;elapsed=0;laps.clear()}){Text("Zerar")}};laps.forEachIndexed{n,s->Text("Volta ${n+1}: $s s")}}
            "Datas"->item{OutlinedTextField(monthly,{monthly=it},label={Text("Segunda data • AAAA-MM-DD")},modifier=Modifier.fillMaxWidth());Button(onClick={run{java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(input),java.time.LocalDate.parse(monthly)).toString()+" dias"}}){Text("Diferença entre datas")}}
        }
        if(result.isNotBlank())item{OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(result);TextButton(onClick={val clip=android.content.ClipData.newPlainText("Veyra",result);clip.description.extras=android.os.PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",mode=="Senhas")};context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)}){Text("Copiar resultado")}}}}
        item{Spacer(Modifier.height(100.dp))}
    }
}
