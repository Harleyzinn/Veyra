package app.veyra.android
import android.app.*
import android.content.*
import android.os.Build
import android.appwidget.AppWidgetManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import app.veyra.data.WorkspaceStore
import app.veyra.model.*
import java.time.*
import java.util.concurrent.TimeUnit

fun evaluateRules(store:WorkspaceStore){
    val items=store.all().filter{it.deletedAt==0L};val today=LocalDate.now();val existing=items.map{it.id}.toSet()
    items.filter{it.type=="rule" && it.value("enabled")!="Não"}.forEach{rule->
        val targets=when(rule.value("condition")){
            "Tarefa atrasada"->items.filter{it.type=="task" && !it.done && it.date.isNotBlank() && it.date<today.toString()}
            "Produto vence em 7 dias"->items.filter{it.type=="pantry" && it.value("expiry").isNotBlank() && it.value("expiry")<=today.plusDays(7).toString()}
            "Manutenção próxima"->items.filter{it.type=="maintenance" && !it.done && it.value("next").isNotBlank() && it.value("next")<=today.plusDays(7).toString()}
            "Orçamento em 80%"->items.filter{it.type=="budget" && it.cents()>0 && Workspace.expenses(items.filter{e->e.date.startsWith(today.toString().take(7)) && e.value("category").equals(it.value("category"),true)})>=it.cents()*.8}
            else->emptyList()
        }
        store.saveAll(targets.map{target->Item(id="rule:${rule.id}:${target.id}:$today",type="inbox",title="${rule.title.take(70)} • ${target.title.take(100)}",parentId=target.id,fields=mapOf("read" to "Não"))}.filter{it.id !in existing})
    }
}
object AndroidJobs {
    const val CHANNEL="veyra-reminders"
    fun channels(context:Context){val manager=context.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel(CHANNEL,"Lembretes Veyra",NotificationManager.IMPORTANCE_DEFAULT));manager.createNotificationChannel(NotificationChannel("veyra-focus","Sessões de foco",NotificationManager.IMPORTANCE_LOW))}
    fun install(context:Context){channels(context);WorkManager.getInstance(context).enqueueUniquePeriodicWork("veyra-maintenance",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<MaintenanceWorker>(1,TimeUnit.HOURS).build())}
    fun schedule(context:Context,item:Item){cancel(context,item.id);if(item.done || item.deletedAt!=0L || item.value("reminder").isBlank() || item.date.isBlank())return
        val at=LocalDate.parse(item.date).atTime(LocalTime.parse(item.value("reminder"))).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if(at<=System.currentTimeMillis())return
        WorkManager.getInstance(context).enqueueUniqueWork("reminder:${item.id}",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("id" to item.id)).setInitialDelay(at-System.currentTimeMillis(),TimeUnit.MILLISECONDS).build())
    }
    fun cancel(context:Context,id:String){WorkManager.getInstance(context).cancelUniqueWork("reminder:$id")}
    fun updateWidget(context:Context){val manager=AppWidgetManager.getInstance(context);val ids=manager.getAppWidgetIds(ComponentName(context,TodayWidget::class.java));if(ids.isNotEmpty())context.sendBroadcast(Intent(context,TodayWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))}
    fun notify(context:Context,item:Item){
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        val open=PendingIntent.getActivity(context,item.id.hashCode(),Intent(context,MainActivity::class.java).putExtra("item",item.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String)=PendingIntent.getBroadcast(context,(item.id+name).hashCode(),Intent(context,ReminderAction::class.java).putExtra("id",item.id).setAction(name),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_capture).setContentTitle(item.title).setContentText("${Registry.spec(item.type).label} • ${item.date}").setContentIntent(open).setAutoCancel(true)
        if(item.type!="bill")builder.addAction(0,"Concluir",action("done"))
        val notification=builder.addAction(0,"Adiar 15 min",action("snooze")).build()
        context.getSystemService(NotificationManager::class.java).notify(item.id.hashCode(),notification)
    }
}
class ReminderWorker(context:Context,params:WorkerParameters):androidx.work.CoroutineWorker(context,params){
    override suspend fun doWork():Result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){
        WorkspaceStore(applicationContext).use{store->store.all().firstOrNull{it.id==inputData.getString("id") && it.deletedAt==0L && !it.done}?.let{AndroidJobs.notify(applicationContext,it)}};Result.success()
    }
}
class MaintenanceWorker(context:Context,params:WorkerParameters):androidx.work.CoroutineWorker(context,params){
    override suspend fun doWork():Result=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){try{
        WorkspaceStore(applicationContext).use{store->evaluateRules(store)
            store.all().filter{it.type=="inbox" && it.deletedAt==0L && it.value("notified")!="Sim" && !it.done}.forEach{AndroidJobs.notify(applicationContext,it);store.save(it.copy(fields=it.fields+("notified" to "Sim")))}
            if(store.preferences()["autoBackup"]=="Sim")java.io.File(applicationContext.filesDir,"automatic-backup.json").writeText(store.exportJson())
        };AndroidJobs.updateWidget(applicationContext);Result.success()
    }catch(_:Exception){Result.retry()}}
}
class ReminderAction:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val pending=goAsync();java.util.concurrent.Executors.newSingleThreadExecutor().also{executor->executor.execute{try{
    val id=intent.getStringExtra("id") ?: return@execute
    if(intent.action=="snooze")WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("id" to id)).setInitialDelay(15,TimeUnit.MINUTES).build())
    else WorkspaceStore(context).use{store->store.all().firstOrNull{it.id==id}?.let{store.save(it.copy(done=true))}}
    context.getSystemService(NotificationManager::class.java).cancel(id.hashCode());AndroidJobs.updateWidget(context)
}finally{pending.finish();executor.shutdown()}}}}
}
