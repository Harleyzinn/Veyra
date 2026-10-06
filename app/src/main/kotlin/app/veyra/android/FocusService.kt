package app.veyra.android
import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import app.veyra.data.WorkspaceStore
import app.veyra.model.Item

class FocusService:Service(){
    private val handler=Handler(Looper.getMainLooper());private val prefs by lazy{getSharedPreferences("focus",MODE_PRIVATE)}
    override fun onBind(intent:Intent?)=null
    private val tick=object:Runnable{override fun run(){
        val remaining=((prefs.getLong("deadline",0)-System.currentTimeMillis()+999)/1000).coerceAtLeast(0)
        prefs.edit().putLong("remaining",remaining).apply()
        if(remaining>0){notifyTimer(remaining);handler.postDelayed(this,1000);return}
        val cycle=prefs.getInt("cycle",1);val cycles=prefs.getInt("cycles",4);val minutes=prefs.getInt("minutes",25)
        if(prefs.getString("phase","Foco")=="Foco"){
            val parent=prefs.getString("parent","").orEmpty();val started=prefs.getLong("started",0)
            java.util.concurrent.Executors.newSingleThreadExecutor().also{executor->executor.execute{try{WorkspaceStore(applicationContext).use{it.save(Item(id="focus:$started:$cycle",type="focus",title="Foco • ciclo $cycle",parentId=parent,fields=mapOf("minutes" to minutes.toString())))}}finally{executor.shutdown()}}}
            prefs.edit().putString("phase","Pausa").putLong("deadline",System.currentTimeMillis()+(if(cycle>=cycles)prefs.getInt("longBreak",15)else prefs.getInt("shortBreak",5))*60000L).apply()
            handler.postDelayed(this,1000)
        }else if(cycle<cycles){prefs.edit().putString("phase","Foco").putInt("cycle",cycle+1).putLong("deadline",System.currentTimeMillis()+minutes*60000L).apply();handler.postDelayed(this,1000)}
        else finish()
    }}
    private fun finish(){prefs.edit().putBoolean("running",false).putBoolean("paused",false).putLong("remaining",0).apply();handler.removeCallbacks(tick);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        AndroidJobs.channels(this)
        when(intent?.action){
            "stop"->{finish();return START_NOT_STICKY}
            "pause"->{val remaining=((prefs.getLong("deadline",0)-System.currentTimeMillis()+999)/1000).coerceAtLeast(0);prefs.edit().putBoolean("running",false).putBoolean("paused",true).putLong("remaining",remaining).apply();handler.removeCallbacks(tick);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY}
            "resume"->prefs.edit().putBoolean("running",true).putBoolean("paused",false).putLong("deadline",System.currentTimeMillis()+prefs.getLong("remaining",0)*1000L).apply()
            "start"->{val minutes=intent.getIntExtra("minutes",25).coerceIn(1,180);prefs.edit().putInt("minutes",minutes).putInt("cycles",intent.getIntExtra("cycles",4).coerceIn(1,12)).putInt("shortBreak",intent.getIntExtra("shortBreak",5).coerceIn(1,60)).putInt("longBreak",intent.getIntExtra("longBreak",15).coerceIn(1,60)).putInt("cycle",1).putString("phase","Foco").putLong("started",System.currentTimeMillis()).putLong("deadline",System.currentTimeMillis()+minutes*60000L).putLong("remaining",minutes*60L).putString("parent",intent.getStringExtra("parent").orEmpty()).putBoolean("running",true).putBoolean("paused",false).apply()}
        }
        if(!prefs.getBoolean("running",false)){stopSelf();return START_NOT_STICKY}
        notifyTimer(prefs.getLong("remaining",0),true);handler.removeCallbacks(tick);handler.post(tick);return START_STICKY
    }
    private fun notifyTimer(seconds:Long,foreground:Boolean=false){
        fun action(name:String)=PendingIntent.getService(this,name.hashCode(),Intent(this,FocusService::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"veyra-focus").setSmallIcon(R.drawable.ic_capture).setContentTitle("${prefs.getString("phase","Foco")} • %02d:%02d".format(seconds/60,seconds%60)).setContentText("Ciclo ${prefs.getInt("cycle",1)} de ${prefs.getInt("cycles",4)}").setContentIntent(open).setOngoing(true).addAction(0,"Pausar",action("pause")).addAction(0,"Encerrar",action("stop")).build()
        if(foreground)startForeground(912,notification)else getSystemService(NotificationManager::class.java).notify(912,notification)
    }
    override fun onDestroy(){handler.removeCallbacks(tick);super.onDestroy()}
}
