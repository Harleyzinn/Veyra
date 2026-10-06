package app.veyra.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.veyra.model.Item
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.*

@Composable fun ScannerStudio(vm:VeyraViewModel){
    val context=LocalContext.current;val scope=rememberCoroutineScope();var result by remember{mutableStateOf("")};var kind by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
        busy=true;error="";runCatching{withContext(Dispatchers.IO){
            val options=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
            context.contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,options)}
            require(options.outWidth>0 && options.outHeight>0){"Imagem inválida"};options.inJustDecodeBounds=false;options.inSampleSize=(maxOf(options.outWidth,options.outHeight)/1800).coerceAtLeast(1)
            val bitmap=context.contentResolver.openInputStream(uri)?.use{android.graphics.BitmapFactory.decodeStream(it,null,options)} ?: error("Imagem inválida")
            try{val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val image=BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels)))
                val reader=MultiFormatReader();val hints=mapOf(DecodeHintType.TRY_HARDER to true)
                runCatching{reader.decode(image,hints)}.getOrElse{reader.reset();reader.decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width,bitmap.height,pixels).invert())),hints)}
            }finally{bitmap.recycle()}
        }}.onSuccess{result=it.text;kind=it.barcodeFormat.toString()}.onFailure{error="Não encontrei um código legível. Tente uma imagem mais nítida."};busy=false
    }}
    LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("LEITOR DE CÓDIGOS","Da imagem ao texto.","Leia QR e códigos de barras em fotos ou capturas de tela.")}
        item{Button(onClick={pick.launch(arrayOf("image/*"))},enabled=!busy){Text("Escolher imagem")};if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)}
        if(result.isNotBlank())item{StudioCard{Text(kind,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(result);Row{TextButton(onClick={(context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Código lido",result))}){Text("Copiar")};Button(onClick={vm.save(Item(type="note",title="Código lido • $kind",notes=result))}){Text("Guardar nas notas")}}}}
        item{Text("A leitura acontece no aparelho. O conteúdo não é aberto automaticamente.",style=MaterialTheme.typography.bodySmall)}
        item{Spacer(Modifier.height(80.dp))}
    }
}
