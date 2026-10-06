package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable fun WorldClockStudio(vm:VeyraViewModel){
    var tick by remember{mutableLongStateOf(System.currentTimeMillis())};var query by remember{mutableStateOf("")};var selected by remember{mutableStateOf("America/Sao_Paulo")}
    val zones=vm.preferences["clockZones"].orEmpty().split('|').filter{it in ZoneId.getAvailableZoneIds()}.ifEmpty{listOf("America/Sao_Paulo","Europe/London","Asia/Tokyo")}
    LaunchedEffect(Unit){while(true){tick=System.currentTimeMillis();delay(1000)}}
    LazyColumn(contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{PageHeading("RELÓGIO MUNDIAL","O mundo tem seus horários.","Compare fusos e encontre uma boa hora para conversar.")}
        item{OutlinedTextField(query,{query=it},label={Text("Buscar cidade ou região")},modifier=Modifier.fillMaxWidth(),singleLine=true);Choice("Fuso",ZoneId.getAvailableZoneIds().filter{it.contains(query,true)}.sorted().take(80),selected,{selected=it});Button(onClick={vm.pref("clockZones",(zones+selected).distinct().take(12).joinToString("|"))},enabled=zones.size<12 && selected !in zones){Text("Adicionar relógio")}}
        zones.forEach{zone->item{val date=ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(tick),ZoneId.of(zone));StudioCard{Text(zone.replace('_',' '),style=MaterialTheme.typography.titleMedium);Text(date.format(DateTimeFormatter.ofPattern("HH:mm:ss")),style=MaterialTheme.typography.displaySmall);Text(date.format(DateTimeFormatter.ofPattern("EEEE, dd MMM • 'UTC' XXX",Brazilian)));TextButton(onClick={vm.pref("clockZones",zones.filter{it!=zone}.joinToString("|"))},enabled=zones.size>1){Text("Remover")}}}}
        item{Spacer(Modifier.height(80.dp))}
    }
}
