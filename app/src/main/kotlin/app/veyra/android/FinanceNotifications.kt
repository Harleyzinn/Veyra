package app.veyra.android

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.feature.finance.FinanceEngine
import app.veyra.feature.finance.FinancialAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate

/** One policy serves app values, notifications, and the home-screen widget. */
object FinancePrivacyPolicy {
    fun protected(preferences:Map<String,String>) = !preferences["lock"].isNullOrBlank() ||
        preferences["financeLock"]=="Sim" || preferences["financeHidden"]=="Sim" || preferences["financeHideValues"]=="Sim"
    fun widgetDetails(preferences:Map<String,String>) = preferences["widgetFinance"]=="Sim" && !protected(preferences)
    fun notificationDetails(preferences:Map<String,String>) = preferences["financeNotificationValues"]=="Sim" && !protected(preferences)
}

object FinanceNotifications {
    const val CHANNEL="veyra-finance-alerts"
    const val MAX_PER_DAY=3
    private val notificationFence=Any()
    fun seenKey(alertId:String):String="financeAlert:"+MessageDigest.getInstance("SHA-256")
        .digest(alertId.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun pending(alerts:List<FinancialAlert>,preferences:Map<String,String>,today:LocalDate):List<FinancialAlert> {
        if(preferences["financeNotifications"]!="Sim")return emptyList()
        val sent=if(preferences["financeAlertDate"]==today.toString())preferences["financeAlertCount"]?.toIntOrNull()?.coerceIn(0,MAX_PER_DAY) ?: 0 else 0
        return alerts.distinctBy{it.id}.filter{preferences[seenKey(it.id)]==null}.take(MAX_PER_DAY-sent)
    }
    internal fun buildNotification(context:Context,uid:String?,alert:FinancialAlert,preferences:Map<String,String>):Notification {
        val reveal=FinancePrivacyPolicy.notificationDetails(preferences)
        val open=PendingIntent.getActivity(context,(AndroidJobs.scopeKey(uid)+alert.id).hashCode(),
            AndroidJobs.scopedIntent(Intent(context,MainActivity::class.java).putExtra("finance",true).putExtra(AndroidJobs.SCOPE_UID,uid.orEmpty()).putExtra(AndroidJobs.SCOPE_BOUND,true),uid,"finance-alert",alert.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_capture).setContentTitle("Veyra").setContentText("Há um alerta para revisar.").setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        return NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_capture)
            .setContentTitle(if(reveal)alert.title else "Alerta financeiro")
            .setContentText(if(reveal)alert.description else "${alert.priority.label} · Abra o Veyra para revisar.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(if(reveal)alert.description else "Abra sua central financeira para revisar os registros."))
            .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
    }
    fun process(context:Context,store:WorkspaceStore,uid:String?,alerts:List<FinancialAlert>,today:LocalDate=LocalDate.now()):Int=synchronized(notificationFence){
        if(!AndroidJobs.isCurrentScope(context,uid) || !AndroidJobs.canNotify(context))return@synchronized 0
        var sent=0
        AndroidJobs.withCurrentScope(context,uid){
            val preferences=store.preferences()
            var count=if(preferences["financeAlertDate"]==today.toString())preferences["financeAlertCount"]?.toIntOrNull()?.coerceIn(0,MAX_PER_DAY) ?: 0 else 0
            pending(alerts,preferences,today).forEach{alert->
                if(!AndroidJobs.isCurrentScope(context,uid))return@forEach
                val notification=buildNotification(context,uid,alert,preferences)
                context.getSystemService(NotificationManager::class.java).notify(AndroidJobs.notificationTag(uid,"finance-alert:${alert.id}"),0,notification)
                count++;sent++
                store.preference(seenKey(alert.id),today.toString())
                store.preference("financeAlertDate",today.toString());store.preference("financeAlertCount",count.toString())
            }
        }
        sent
    }
}

/** Runs offline from indexed local data; no exact alarm, background network, or invented insights. */
class FinancialDailyWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        if(!AndroidJobs.isBoundCurrent(applicationContext,inputData))return@withContext Result.success()
        val uid=AndroidJobs.boundUid(inputData)
        try{
            WorkspaceStore(applicationContext,WorkspaceIdentity.databaseForUid(uid)).use{store->
                val preferences=store.preferences()
                if(preferences["financeNotifications"]!="Sim" || !AndroidJobs.isCurrentScope(applicationContext,uid))return@use
                val today=LocalDate.now()
                val items=store.financeWindow(today.minusMonths(3).withDayOfMonth(1),today.plusYears(1))
                val alerts=FinanceEngine.alerts(items,today,preferences["financeCurrency"] ?: "BRL",(preferences["financialDay"]?.toIntOrNull() ?: 1).coerceIn(1,31))
                FinanceNotifications.process(applicationContext,store,uid,alerts,today)
            };Result.success()
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){Result.retry()}
    }
}
