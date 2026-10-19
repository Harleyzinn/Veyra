package app.veyra.android

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.*
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject

/** Local bundled UI only. Remote content never receives the bridge. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FinancePortal(vm:VeyraViewModel,legacy:()->Unit){
    var web by remember { mutableStateOf<WebView?>(null) }
    var pendingExport by remember { mutableStateOf<Triple<String,String,String>?>(null) }
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity ?: return
    BackHandler {web?.evaluateJavascript("window.veyraBack ? window.veyraBack() : false"){result->if(result!="true")activity.finish()}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val value=pendingExport;pendingExport=null
        if(uri!=null && value!=null)runCatching{activity.contentResolver.openOutputStream(uri)?.use{it.write(value.second.toByteArray(Charsets.UTF_8))}}.onFailure{vm.portalError("Não foi possível exportar o arquivo.")}
    }
    val filePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        FinanceFileChoice.callback?.onReceiveValue(uri?.let{arrayOf(it)});FinanceFileChoice.callback=null
    }
    DisposableEffect(Unit){onDispose{web?.removeJavascriptInterface("VeyraFinance");web?.destroy();FinanceFileChoice.callback?.onReceiveValue(null);FinanceFileChoice.callback=null}}
    AndroidView(modifier=Modifier.fillMaxSize(),factory={context->WebView(context).apply{
        web=this
        settings.javaScriptEnabled=true;settings.domStorageEnabled=true
        settings.allowFileAccess=false;settings.allowContentAccess=false
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                val url=request.url
                return url.scheme!="https" || url.host!="appassets.androidplatform.net" || !url.path.orEmpty().startsWith("/finance/")
            }
            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse?{
                val url=request.url
                if(url.scheme!="https" || url.host!="appassets.androidplatform.net")return WebResourceResponse("text/plain","UTF-8",403,"Forbidden",emptyMap(),"Forbidden".byteInputStream())
                val path=url.path.orEmpty().removePrefix("/")
                if(!path.startsWith("finance/") || path.contains(".."))return WebResourceResponse("text/plain","UTF-8",null)
                val mime=when(path.substringAfterLast('.')){"html"->"text/html";"js"->"application/javascript";"css"->"text/css";"svg"->"image/svg+xml";"ttf"->"font/ttf";"webmanifest"->"application/manifest+json";else->"application/octet-stream"}
                return runCatching{WebResourceResponse(mime,"UTF-8",200,"OK",mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff"),context.assets.open(path))}.getOrElse{WebResourceResponse("text/plain","UTF-8",404,"Not Found",emptyMap(),"Not found".byteInputStream())}
            }
        }
        webChromeClient=object:WebChromeClient(){
            override fun onShowFileChooser(view:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean{
                FinanceFileChoice.callback?.onReceiveValue(null);FinanceFileChoice.callback=callback
                filePicker.launch(arrayOf("application/json","text/plain","text/csv","text/comma-separated-values"));return true
            }
        }
        addJavascriptInterface(object{
            @JavascriptInterface fun request(id:String,method:String,payload:String){
                if(id.length>100 || payload.length>25_000_000)return
                activity.runOnUiThread{
                    if(vm.preferences["financeLock"]=="Sim" && !financeAccessUnlocked(activity)){
                        web?.evaluateJavascript("window.omegaFinanceReply(${JSONObject.quote(id)},${JSONObject.quote("{\"error\":\"Desbloqueie o financeiro antes de continuar.\"}")})",null);return@runOnUiThread
                    }
                    if(method=="legacy"){legacy();return@runOnUiThread}
                    if(method=="export"){
                        runCatching{val obj=JSONObject(payload);val name=obj.getString("name").replace(Regex("[^a-zA-Z0-9._-]"),"_");pendingExport=Triple(name,obj.getString("text"),obj.getString("mime"));export.launch(name)}
                        web?.evaluateJavascript("window.omegaFinanceReply(${JSONObject.quote(id)},${JSONObject.quote("{\"ok\":true}")})",null)
                    }else vm.financePortalCall(method,payload){result->web?.evaluateJavascript("window.omegaFinanceReply(${JSONObject.quote(id)},${JSONObject.quote(result)})",null)}
                }
            }
        },"VeyraFinance")
        loadUrl("https://appassets.androidplatform.net/finance/index.html")
    }})
}
private object FinanceFileChoice {var callback:ValueCallback<Array<Uri>>?=null}
