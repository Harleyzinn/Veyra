package app.veyra.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.*
import app.veyra.data.WorkspaceStore
import app.veyra.model.UpdatePolicy
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppRelease(val tag:String,val version:String,val title:String,val notes:String,val url:String,val digest:String,val size:Long)

object GitHubUpdater {
    private const val API="https://api.github.com/repos/Harleyzinn/Veyra/releases/latest"
    fun currentVersion(context:Context)=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "0.0.0"
    fun unmetered(context:Context)=!(context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
    @Synchronized fun cached(context:Context,release:AppRelease):File? {
        val file=File(context.cacheDir,"updates/veyra-update.apk")
        if(!file.exists())return null
        return runCatching{
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use{input->val buffer=ByteArray(64*1024);var count=input.read(buffer);while(count>=0){digest.update(buffer,0,count);count=input.read(buffer)}}
            require(file.length()==release.size && digest.digest().joinToString(""){"%02x".format(it)}==release.digest)
            verify(context,file,release.version);file
        }.getOrElse{file.delete();null}
    }
    fun latest(context:Context):AppRelease? {
        val connection=URL(API).openConnection() as HttpURLConnection
        connection.apply{connectTimeout=15000;readTimeout=20000;setRequestProperty("Accept","application/vnd.github+json");setRequestProperty("User-Agent","Veyra-Android")}
        try{
            if(connection.responseCode==404)return null
            require(connection.responseCode==200){"Não foi possível consultar atualizações (HTTP ${connection.responseCode})"}
            val text=connection.inputStream.bufferedReader().use{it.readText().also{value->require(value.length<1_000_000)}}
            val json=JSONObject(text);val tag=json.getString("tag_name")
            if(json.optBoolean("draft") || json.optBoolean("prerelease") || !UpdatePolicy.newer(tag,currentVersion(context)))return null
            val assets=json.getJSONArray("assets")
            for(index in 0 until assets.length()){
                val asset=assets.getJSONObject(index);val url=asset.getString("browser_download_url")
                if(!UpdatePolicy.assetUrl(tag,url))continue
                val hash=UpdatePolicy.digest(asset.optString("digest")) ?: error("A release não possui SHA-256 válido")
                val size=asset.getLong("size");require(size in 1..150_000_000){"Tamanho de atualização inválido"}
                return AppRelease(tag,tag.removePrefix("v"),json.optString("name",tag),json.optString("body").take(5000),url,hash,size)
            }
            return null
        }finally{connection.disconnect()}
    }
    @Synchronized fun download(context:Context,release:AppRelease,progress:(Float)->Unit):File {
        require(UpdatePolicy.assetUrl(release.tag,release.url) && UpdatePolicy.digest(release.digest)!=null)
        cached(context,release)?.let{progress(1f);return it}
        val directory=File(context.cacheDir,"updates").apply{mkdirs()}
        val part=File(directory,"update-download.apk");val target=File(directory,"veyra-update.apk")
        part.delete();target.delete()
        var url=release.url
        var connection:HttpURLConnection?=null
        try{
            for(attempt in 0 until 6){
                require(UpdatePolicy.trustedDownload(url)){"Endereço de download não autorizado"}
                val next=URL(url).openConnection() as HttpURLConnection
                next.apply{instanceFollowRedirects=false;connectTimeout=15000;readTimeout=30000;setRequestProperty("User-Agent","Veyra-Android")}
                if(next.responseCode in listOf(301,302,303,307,308)){
                    val location=next.getHeaderField("Location") ?: error("Redirecionamento inválido");next.disconnect();url=URL(URL(url),location).toString()
                }else {if(next.responseCode!=200){val status=next.responseCode;next.disconnect();error("Download indisponível (HTTP $status)")};connection=next;break}
            }
            val active=connection ?: error("Muitos redirecionamentos")
            val hash=MessageDigest.getInstance("SHA-256");var total=0L
            active.inputStream.use{input->part.outputStream().use{output->
                val bytes=ByteArray(64*1024);var count=input.read(bytes)
                while(count>=0){total+=count;require(total<=release.size && total<=150_000_000){"Tamanho inesperado no download"};hash.update(bytes,0,count);output.write(bytes,0,count);progress(total.toFloat()/release.size);count=input.read(bytes)}
            }}
            require(total==release.size && hash.digest().joinToString(""){"%02x".format(it)}==release.digest){"O APK baixado não passou na verificação SHA-256"}
            verify(context,part,release.version)
            require(part.renameTo(target)){"Não foi possível preparar a atualização"};return target
        }catch(e:Exception){part.delete();target.delete();throw e}finally{connection?.disconnect()}
    }
    @Suppress("DEPRECATION")
    fun verify(context:Context,file:File,expectedVersion:String) {
        val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed=context.packageManager.getPackageInfo(context.packageName,flags)
        val archive=context.packageManager.getPackageArchiveInfo(file.absolutePath,flags) ?: error("APK inválido")
        require(archive.packageName==context.packageName && archive.versionName==expectedVersion){"O APK não corresponde ao Veyra esperado"}
        val code=if(Build.VERSION.SDK_INT>=28)archive.longVersionCode else archive.versionCode.toLong()
        val local=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
        require(code>local){"Esta versão não é mais recente"}
        fun signatures(info:android.content.pm.PackageInfo)=if(Build.VERSION.SDK_INT>=28)info.signingInfo?.apkContentsSigners else info.signatures
        val a=signatures(installed)?.map{it.toCharsString()}?.toSet().orEmpty()
        val b=signatures(archive)?.map{it.toCharsString()}?.toSet().orEmpty()
        require(a.isNotEmpty() && a==b){"A assinatura do APK difere da versão instalada"}
    }
    fun install(context:Context,file:File,version:String){
        verify(context,file,version)
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()){
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class UpdateWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
        try{WorkspaceStore(applicationContext).use{store->
            if(store.preferences()["autoUpdateCheck"]!="Não"){
                val release=GitHubUpdater.latest(applicationContext)
                store.preference("updateCheckedAt",if(release!=null)"0"else System.currentTimeMillis().toString())
                val ready=release?.let{GitHubUpdater.cached(applicationContext,it) ?: if(store.preferences()["autoUpdateDownload"]!="Não" && GitHubUpdater.unmetered(applicationContext))GitHubUpdater.download(applicationContext,it){}else null}
                if(release!=null && store.preferences()["updateNotified"]!=release.tag){
                    val open=android.app.PendingIntent.getActivity(applicationContext,902,Intent(applicationContext,MainActivity::class.java).putExtra("updates",true),android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                    val notification=androidx.core.app.NotificationCompat.Builder(applicationContext,AndroidJobs.CHANNEL).setSmallIcon(R.drawable.ic_capture).setContentTitle("Veyra ${release.version} disponível").setContentText(if(ready!=null)"Download conferido. Toque para instalar."else "Toque para ver novidades e atualizar.").setContentIntent(open).setAutoCancel(true).build()
                    if(androidx.core.app.NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()){androidx.core.app.NotificationManagerCompat.from(applicationContext).notify(902,notification);store.preference("updateNotified",release.tag)}
                }
            }
        };Result.success()}catch(_:Exception){Result.retry()}
    }
}
