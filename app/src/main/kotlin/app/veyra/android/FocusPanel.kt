package app.veyra.android
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.veyra.model.Item

@Composable fun FocusPanel(items:List<Item>,vm:VeyraViewModel){val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("focus",android.content.Context.MODE_PRIVATE)}
    var minutes by remember{mutableStateOf(vm.preferences["focusMinutes"] ?: "25")};var parent by remember{mutableStateOf("")};var remaining by remember{mutableLongStateOf(0L)};var running by remember{mutableStateOf(false)};var paused by remember{mutableStateOf(false)};var cycles by remember{mutableStateOf("4")};var pause by remember{mutableStateOf("5")};var longPause by remember{mutableStateOf("15")};var phase by remember{mutableStateOf("Foco")}
    LaunchedEffect(Unit){while(true){val active=prefs.getBoolean("running",false);if(running && !active)vm.operation{};running=active;paused=prefs.getBoolean("paused",false);phase=prefs.getString("phase","Foco").orEmpty();remaining=prefs.getLong("remaining",0);kotlinx.coroutines.delay(1000)}}
    OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Um espaço para focar",style=MaterialTheme.typography.titleLarge)
        if(running || paused)Text("$phase • %02d:%02d".format(remaining/60,remaining%60),style=MaterialTheme.typography.headlineLarge)
        OutlinedTextField(minutes,{minutes=it},label={Text("Duração • 1 a 180 minutos")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Choice("Vincular sessão",listOf("")+items.filter{it.type in setOf("task","subject","project")}.map{it.id},parent,{parent=it}){id->items.firstOrNull{it.id==id}?.title ?: "Nenhum"}
        if(!running && !paused){Row{OutlinedTextField(cycles,{cycles=it},label={Text("Ciclos")},modifier=Modifier.weight(1f));OutlinedTextField(pause,{pause=it},label={Text("Pausa min")},modifier=Modifier.weight(1f));OutlinedTextField(longPause,{longPause=it},label={Text("Longa min")},modifier=Modifier.weight(1f))}}
        Row{Button(onClick={val duration=minutes.toIntOrNull()?.takeIf{it in 1..180} ?: return@Button;vm.pref("focusMinutes",duration.toString());ContextCompat.startForegroundService(context,Intent(context,FocusService::class.java).setAction(if(paused)"resume"else "start").putExtra("minutes",duration).putExtra("cycles",cycles.toIntOrNull() ?: 4).putExtra("shortBreak",pause.toIntOrNull() ?: 5).putExtra("longBreak",longPause.toIntOrNull() ?: 15).putExtra("parent",parent))},enabled=!running){Text(if(paused)"Retomar"else "Iniciar foco")}
            TextButton(onClick={context.startService(Intent(context,FocusService::class.java).setAction("pause"))},enabled=running){Text("Pausar")}
            TextButton(onClick={context.startService(Intent(context,FocusService::class.java).setAction("stop"))},enabled=running || paused){Text("Encerrar")}}
        Text("Continua em segundo plano com notificação. Sessões completas são registradas no histórico.",style=MaterialTheme.typography.bodySmall)
    }}
}
