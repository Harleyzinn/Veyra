package app.veyra.model

import java.net.URI

object UpdatePolicy {
    fun version(value:String):List<Int>? = Regex("^v?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})$").matchEntire(value)?.groupValues?.drop(1)?.map(String::toInt)
    fun newer(remote:String,local:String):Boolean {
        val a=version(remote) ?: return false;val b=version(local) ?: return false
        a.indices.forEach{if(a[it]!=b[it])return a[it]>b[it]};return false
    }
    fun assetUrl(tag:String,url:String):Boolean {
        val v=version(tag) ?: return false
        val canonical=v.joinToString(".");return url=="https://github.com/Harleyzinn/Veyra/releases/download/v$canonical/Veyra-$canonical.apk"
    }
    fun trustedDownload(url:String):Boolean=runCatching{
        val uri=URI(url);uri.scheme=="https" && uri.userInfo==null && uri.port in listOf(-1,443) && uri.host in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    }.getOrDefault(false)
    fun digest(value:String)=value.removePrefix("sha256:").lowercase().takeIf{it.matches(Regex("[0-9a-f]{64}"))}
}
