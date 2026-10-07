package app.veyra.android

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import app.veyra.data.WorkspaceIdentity
import app.veyra.data.WorkspaceStore
import app.veyra.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.util.concurrent.TimeUnit

fun evaluateRules(store:WorkspaceStore,save:(List<Item>)->Unit={store.saveAll(it)},canWrite:()->Boolean={true}){
    val items=store.overview().filter{it.deletedAt==0L};val today=LocalDate.now()
    items.filter{it.type=="rule" && it.value("enabled")!="Não"}.forEach{rule->
        val targets=when(rule.value("condition")){
            "Tarefa atrasada"->items.filter{it.type=="task" && !it.done && it.date.isNotBlank() && it.date<today.toString()}
            "Produto vence em 7 dias"->items.filter{it.type=="pantry" && it.value("expiry").isNotBlank() && it.value("expiry")<=today.plusDays(7).toString()}
            "Manutenção próxima"->items.filter{it.type=="maintenance" && !it.done && it.value("next").isNotBlank() && it.value("next")<=today.plusDays(7).toString()}
            "Orçamento em 80%"->items.filter{it.type=="budget" && it.cents()>0 && Workspace.expenses(items.filter{e->e.date.startsWith(today.toString().take(7)) && e.value("category").equals(it.value("category"),true)})>=it.cents()*.8}
            else->emptyList()
        }
        if(canWrite())save(targets.map{target->Item(id="rule:${rule.id}:${target.id}:$today",type="inbox",title="${rule.title.take(70)} • ${target.title.take(100)}",parentId=target.id,fields=mapOf("read" to "Não")+if(target.type in app.veyra.feature.finance.FinancialDomain.financialTypes)mapOf("financial" to "yes")else emptyMap())}.filter{store.find(it.id)==null})
    }
}

object AndroidJobs {
    const val CHANNEL="veyra-reminders"
    const val SCOPE_UID="scopeUid"
    const val SCOPE_BOUND="scopeBound"
    private val scopeFence=Any()
    fun scopeKey(uid:String?)=WorkspaceIdentity.databaseForUid(uid).removeSuffix(".db")
    fun workspaceTag(uid:String?)="veyra-workspace-${scopeKey(uid)}"
    fun isCurrentScope(context:Context,uid:String?)=WorkspaceIdentity.activeUid(context)==uid
    internal fun <T> withCurrentScope(context:Context,uid:String?,action:()->T):T?=synchronized(scopeFence){WorkspaceIdentity.withActiveUid(context,uid,action)}
    internal fun scopedIntent(intent:Intent,uid:String?,kind:String,id:String):Intent=intent.setData(
        Uri.Builder().scheme("veyra").authority("workspace").appendPath(scopeKey(uid)).appendPath(kind).appendPath(id).build())
    internal fun notificationTag(uid:String?,id:String)=workspaceTag(uid)+":"+java.security.MessageDigest.getInstance("SHA-256")
        .digest(id.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun boundUid(data:Data):String?=data.getString(SCOPE_UID)?.takeIf{it.isNotBlank()}
    fun scopeData(uid:String?,id:String?=null):Data=workDataOf(SCOPE_BOUND to true,SCOPE_UID to uid.orEmpty(),"id" to id.orEmpty())
    fun isBoundCurrent(context:Context,data:Data)=data.getBoolean(SCOPE_BOUND,false) && isCurrentScope(context,boundUid(data))
    fun reminderName(uid:String?,id:String)="reminder:${scopeKey(uid)}:$id"
    fun backupFile(context:Context,uid:String?=WorkspaceIdentity.activeUid(context))=File(context.filesDir,if(uid==null)"automatic-backup.json"else"automatic-backup-${scopeKey(uid)}.json")
    fun channels(context:Context){
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Lembretes Veyra",NotificationManager.IMPORTANCE_DEFAULT).apply{lockscreenVisibility=Notification.VISIBILITY_PRIVATE})
        manager.createNotificationChannel(NotificationChannel("veyra-focus","Sessões de foco",NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(FinanceNotifications.CHANNEL,"Alertas financeiros",NotificationManager.IMPORTANCE_DEFAULT).apply{lockscreenVisibility=Notification.VISIBILITY_PRIVATE})
    }
    fun install(context:Context){
        channels(context)
        val uid=WorkspaceIdentity.activeUid(context);val work=WorkManager.getInstance(context)
        // Previous releases did not bind the account. Those jobs must never read a new account.
        work.cancelUniqueWork("veyra-maintenance")
        work.enqueueUniquePeriodicWork("veyra-maintenance-${scopeKey(uid)}",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<MaintenanceWorker>(1,TimeUnit.HOURS).setInputData(scopeData(uid)).addTag(workspaceTag(uid)).build())
        work.enqueueUniquePeriodicWork("veyra-finance-${scopeKey(uid)}",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<FinancialDailyWorker>(24,TimeUnit.HOURS).setInputData(scopeData(uid)).addTag(workspaceTag(uid)).build())
        work.enqueueUniquePeriodicWork("veyra-updates",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<UpdateWorker>(24,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun cancelWorkspace(context:Context,uid:String?){synchronized(scopeFence){
        WorkManager.getInstance(context).cancelAllWorkByTag(workspaceTag(uid))
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter{it.tag==workspaceTag(uid) || it.tag?.startsWith(workspaceTag(uid)+":")==true}.forEach{manager.cancel(it.tag,it.id)}
    }}
    fun schedule(context:Context,item:Item,uid:String?=WorkspaceIdentity.activeUid(context)){
        withCurrentScope(context,uid){
            cancel(context,item.id,uid)
            if(item.done || item.deletedAt!=0L || item.value("reminder").isBlank())return@withCurrentScope
            val date=if(item.type in app.veyra.feature.finance.FinancialDomain.transactionTypes)item.value("dueDate").ifBlank{item.date}else item.date
            val at=runCatching{LocalDate.parse(date).atTime(LocalTime.parse(item.value("reminder"))).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull() ?: return@withCurrentScope
            if(at<=System.currentTimeMillis())return@withCurrentScope
            WorkManager.getInstance(context).enqueueUniqueWork(reminderName(uid,item.id),ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(scopeData(uid,item.id)).addTag(workspaceTag(uid)).setInitialDelay(at-System.currentTimeMillis(),TimeUnit.MILLISECONDS).build())
        }
    }
    fun cancel(context:Context,id:String,uid:String?=WorkspaceIdentity.activeUid(context)){
        WorkManager.getInstance(context).cancelUniqueWork(reminderName(uid,id))
        WorkManager.getInstance(context).cancelUniqueWork("reminder:$id")
        context.getSystemService(NotificationManager::class.java).cancel(workspaceTag(uid),id.hashCode())
        context.getSystemService(NotificationManager::class.java).cancel(notificationTag(uid,id),0)
    }
    fun financeChanged(context:Context){
        val uid=WorkspaceIdentity.activeUid(context)
        WorkManager.getInstance(context).enqueueUniqueWork("veyra-finance-check-${scopeKey(uid)}",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<FinancialDailyWorker>().setInputData(scopeData(uid)).addTag(workspaceTag(uid)).setInitialDelay(30,TimeUnit.SECONDS).build())
    }
    fun updateWidget(context:Context){val manager=AppWidgetManager.getInstance(context);val ids=manager.getAppWidgetIds(ComponentName(context,TodayWidget::class.java));if(ids.isNotEmpty())context.sendBroadcast(Intent(context,TodayWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))}
    fun canNotify(context:Context)=Build.VERSION.SDK_INT<33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
    fun notify(context:Context,item:Item,uid:String?=WorkspaceIdentity.activeUid(context)):Boolean=withCurrentScope(context,uid){
        if(!canNotify(context))return@withCurrentScope false
        val prefs=WorkspaceStore(context,WorkspaceIdentity.databaseForUid(uid)).use{it.preferences()}
        val financial=item.type in app.veyra.feature.finance.FinancialDomain.financialTypes || item.value("financial")=="yes"
        if(financial && prefs["financeNotifications"]!="Sim")return@withCurrentScope false
        val reveal=if(financial)FinancePrivacyPolicy.notificationDetails(prefs)else prefs["lock"].isNullOrBlank()
        val key="${scopeKey(uid)}:${item.id}"
        val intent=scopedIntent(Intent(context,MainActivity::class.java).putExtra("item",item.id).putExtra(SCOPE_UID,uid.orEmpty()).putExtra(SCOPE_BOUND,true),uid,"reminder",item.id)
        val open=PendingIntent.getActivity(context,key.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String)=PendingIntent.getBroadcast(context,(key+name).hashCode(),scopedIntent(Intent(context,ReminderAction::class.java).putExtra("id",item.id).putExtra(SCOPE_UID,uid.orEmpty()).putExtra(SCOPE_BOUND,true).setAction(name),uid,name,item.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_capture).setContentTitle("Veyra").setContentText("Você tem um lembrete.").setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        val builder=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_capture).setContentTitle(if(reveal)item.title else if(financial)"Lembrete financeiro"else"Lembrete Veyra").setContentText(if(reveal)"${Registry.spec(item.type).label} • ${item.date}"else"Abra o aplicativo para revisar.").setContentIntent(open).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).setAutoCancel(true)
        // Settling finances requires the full financial flow, never a lock-screen mutation.
        if(!financial)builder.addAction(0,"Concluir",action("done"))
        val notification=builder.addAction(0,"Adiar 15 min",action("snooze")).build()
        context.getSystemService(NotificationManager::class.java).notify(notificationTag(uid,item.id),0,notification)
        true
    } ?: false
    internal fun scopedAction(context:Context,intent:Intent):Boolean {
        if(!intent.getBooleanExtra(SCOPE_BOUND,false))return false
        val uid=intent.getStringExtra(SCOPE_UID)?.takeIf(String::isNotBlank)
        return withCurrentScope(context,uid){
        val id=intent.getStringExtra("id") ?: return@withCurrentScope false
        when(intent.action){
            "snooze"->WorkManager.getInstance(context).enqueueUniqueWork(reminderName(uid,id),ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(scopeData(uid,id)).addTag(workspaceTag(uid)).setInitialDelay(15,TimeUnit.MINUTES).build())
            "done"->WorkspaceStore(context,WorkspaceIdentity.databaseForUid(uid)).use{store->
                val item=store.find(id) ?: return@withCurrentScope false
                if(item.deletedAt!=0L || item.type in app.veyra.feature.finance.FinancialDomain.financialTypes || item.value("financial")=="yes")return@withCurrentScope false
                store.save(item.copy(done=true,fields=if(item.type=="task")item.fields+("status" to "Concluído")else item.fields))
            }
            else->return@withCurrentScope false
        }
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.cancel(workspaceTag(uid),id.hashCode());manager.cancel(notificationTag(uid,id),0);updateWidget(context)
        true
        } ?: false
    }
}

class ReminderWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        if(!AndroidJobs.isBoundCurrent(applicationContext,inputData))return@withContext Result.success()
        val uid=AndroidJobs.boundUid(inputData)
        WorkspaceStore(applicationContext,WorkspaceIdentity.databaseForUid(uid)).use{store->
            store.find(inputData.getString("id").orEmpty())?.takeIf{it.deletedAt==0L && !it.done}?.let{AndroidJobs.notify(applicationContext,it,uid)}
        };Result.success()
    }
}
class MaintenanceWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        if(!AndroidJobs.isBoundCurrent(applicationContext,inputData))return@withContext Result.success()
        val uid=AndroidJobs.boundUid(inputData)
        try{
            WorkspaceStore(applicationContext,WorkspaceIdentity.databaseForUid(uid)).use{store->
                if(!AndroidJobs.isCurrentScope(applicationContext,uid))return@use
                evaluateRules(store,save={changes->AndroidJobs.withCurrentScope(applicationContext,uid){store.saveAll(changes)}}){AndroidJobs.isCurrentScope(applicationContext,uid)}
                store.overview().filter{it.type=="inbox" && it.deletedAt==0L && it.value("notified")!="Sim" && !it.done}.forEach{item->
                    if(AndroidJobs.notify(applicationContext,item,uid))AndroidJobs.withCurrentScope(applicationContext,uid){store.save(item.copy(fields=item.fields+("notified" to "Sim")))}
                }
                if(store.preferences()["autoBackup"]=="Sim" && AndroidJobs.isCurrentScope(applicationContext,uid)){
                    val backup=store.exportJson()
                    AndroidJobs.withCurrentScope(applicationContext,uid){AndroidJobs.backupFile(applicationContext,uid).writeText(backup)}
                }
            };if(AndroidJobs.isCurrentScope(applicationContext,uid))AndroidJobs.updateWidget(applicationContext);Result.success()
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}catch(_:Exception){Result.retry()}
    }
}
class ReminderAction:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val pending=goAsync();java.util.concurrent.Executors.newSingleThreadExecutor().also{executor->executor.execute{try{AndroidJobs.scopedAction(context,intent)}finally{pending.finish();executor.shutdown()}}}}}
