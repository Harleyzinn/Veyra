package app.veyra.android
import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import app.veyra.data.WorkspaceStore
import java.time.LocalDate
class TodayWidget:AppWidgetProvider(){override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){val pending=goAsync();java.util.concurrent.Executors.newSingleThreadExecutor().also{executor->executor.execute{try{
    WorkspaceStore(context).use{store->val items=store.all().filter{it.deletedAt==0L};val tasks=items.filter{it.type=="task" && !it.done && it.date<=LocalDate.now().toString()};val title=tasks.firstOrNull()?.title ?: "Seu dia, com clareza."
        ids.forEach{id->val view=RemoteViews(context.packageName,R.layout.widget_today);view.setTextViewText(R.id.widget_title,"VEYRA • ${tasks.size} tarefas");view.setTextViewText(R.id.widget_body,title)
            view.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,id,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE));view.setOnClickPendingIntent(R.id.widget_add,PendingIntent.getActivity(context,id+10000,Intent(context,MainActivity::class.java).putExtra("capture",true),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE));manager.updateAppWidget(id,view)}
    }
}finally{pending.finish();executor.shutdown()}}}}}
