package app.veyra.android
import android.net.Uri
import android.app.Application
import app.veyra.model.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

fun VeyraViewModel.scan(uri:Uri)=operation{
    val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val text=try{recognizer.process(InputImage.fromFilePath(getApplication(),uri)).await().text}finally{recognizer.close()}
    val amount=Regex("(?:total|valor)[^\n\r]*?([0-9]+[.,][0-9]{2})",RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(',','.') ?: ""
    ocrDraft=Item(type="expense",title=text.lines().firstOrNull{it.isNotBlank()}?.take(200) ?: "Recibo",notes=text,fields=mapOf("amount" to amount))
}
fun VeyraViewModel.localAssistant(question:String){val all=items.value.filter{it.deletedAt==0L};val q=question.lowercase();val month=LocalDate.now().toString().take(7)
    assistantReply=when{
        "gast" in q->"Despesas em $month: ${money(Workspace.expenses(Workspace.monthly(all,month)))}"
        "amanh" in q->all.filter{it.type=="task" && !it.done && it.date==LocalDate.now().plusDays(1).toString()}.joinToString("\n"){it.title}.ifBlank{"Nenhuma tarefa amanhã."}
        "assin" in q->all.filter{it.type=="subscription" && !it.done}.joinToString("\n"){"${it.title}: ${money(it.cents())}"}.ifBlank{"Nenhuma assinatura ativa."}
        "prova" in q->all.filter{it.type=="exam" && !it.done && it.date>=LocalDate.now().toString()}.minByOrNull{it.date}?.let{"${it.title} • ${it.date}"} ?: "Nenhuma prova próxima."
        "resum" in q->"${all.count{it.type=="task" && it.done}} tarefas concluídas; ${all.count{it.type=="note"}} notas; ${all.count{it.type=="focus"}} sessões de foco."
        else->"Comandos locais: quanto gastei este mês; tarefas amanhã; minhas assinaturas; próxima prova; resumo."
    }
}
fun VeyraViewModel.onlineAssistant(question:String)=operation{
    val endpoint=preferences["aiEndpoint"].orEmpty();require(endpoint.startsWith("https://")){"Configure uma URL HTTPS de chat completions"};require(sessionKey.isNotBlank()){ "Informe a chave nesta sessão" }
    val connection=URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=45000;connection.doOutput=true
    connection.setRequestProperty("Authorization","Bearer $sessionKey");connection.setRequestProperty("Content-Type","application/json")
    try{val body=JSONObject().put("model",preferences["aiModel"].orEmpty()).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",question)))
        connection.outputStream.use{it.write(body.toString().toByteArray())};require(connection.responseCode in 200..299){"Provedor retornou ${connection.responseCode}"}
        assistantReply=JSONObject(connection.inputStream.bufferedReader().use{it.readText()}).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }finally{connection.disconnect()}
}
fun VeyraViewModel.pdf(uri:Uri)=operation{
    val doc=android.graphics.pdf.PdfDocument();val paint=android.graphics.Paint().apply{ textSize=12f }
    val lines=mutableListOf("Veyra • relatório financeiro","")
    store.all().filter{it.deletedAt==0L && it.type in listOf("income","expense")}.forEach{lines.addAll("${it.date} • ${it.title} • ${money(it.cents())}".chunked(80))}
    try{lines.chunked(45).forEachIndexed{index,pageLines->val page=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,index+1).create());pageLines.forEachIndexed{n,line->page.canvas.drawText(line,32f,48f+n*17,paint)};doc.finishPage(page)}
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use{doc.writeTo(it)} ?: error("Arquivo indisponível")
    }finally{doc.close()}
}
