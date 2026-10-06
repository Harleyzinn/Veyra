package app.veyra.android

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.veyra.model.Item
import app.veyra.feature.weather.Weather
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun weatherIcon(condition:String)=when(condition){"Céu limpo"->Icons.Default.WbSunny;"Parcialmente nublado"->Icons.Default.WbCloudy;"Chuva leve","Pancadas de chuva"->Icons.Default.WaterDrop;"Tempestade"->Icons.Default.Thunderstorm;"Neve"->Icons.Default.AcUnit;"Névoa"->Icons.Default.BlurOn;else->Icons.Default.Cloud}
private fun selectedCity(items:List<Item>,vm:VeyraViewModel)=items.firstOrNull{it.type=="city" && it.id==vm.preferences["weatherCity"]} ?: items.firstOrNull{it.type=="city"}
@Composable fun WeatherHome(items:List<Item>,vm:VeyraViewModel,open:()->Unit){
    val manual=vm.preferences["weatherMode"]=="Manual";val city=selectedCity(items,vm);val current=runCatching{JSONObject(city?.value("cache").orEmpty()).optJSONObject("current")}.getOrNull()
    Card(onClick=open,modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            val condition=if(manual)vm.preferences["weatherCondition"] ?: "Céu limpo"else current?.let{Weather.condition(it.optInt("weather_code"))} ?: "Clima"
            IconBadge(weatherIcon(condition),Color(0xFFF3C58A),52)
            Column(Modifier.weight(1f)){Text(if(manual)"Meu clima · manual"else city?.title?.substringBefore(" •") ?: "Clima, onde você quiser",style=MaterialTheme.typography.titleSmall,maxLines=2);Text(if(manual)condition else if(current!=null)"$condition · última previsão salva"else "Escolha uma cidade ou personalize",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text(if(manual)"${vm.preferences["weatherTemperature"] ?: "24"}°"else current?.let{"${it.optDouble("temperature_2m").toInt()}°"} ?: "→",style=MaterialTheme.typography.headlineMedium)
        }
    }
}
@Composable fun WeatherScreen(items:List<Item>,vm:VeyraViewModel){
    var query by remember{mutableStateOf("")};var searched by remember{mutableStateOf(false)}
    val mode=vm.preferences["weatherMode"] ?: "Real";val city=selectedCity(items,vm)
    var temp by remember(vm.preferences["weatherTemperature"]){mutableStateOf((vm.preferences["weatherTemperature"]?.toFloatOrNull() ?: 24f).coerceIn(-20f,50f))}
    var condition by remember(vm.preferences["weatherCondition"]){mutableStateOf(vm.preferences["weatherCondition"] ?: "Céu limpo")}
    LazyColumn(contentPadding=PaddingValues(start=22.dp,end=22.dp,top=10.dp,bottom=110.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{PageHeading("SEU HORIZONTE","Olhe lá fora.","Previsão real ou um clima com a sua cara.")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("Real","Manual").forEach{FilterChip(selected=mode==it,onClick={vm.pref("weatherMode",it)},label={Text(if(it=="Real")"Previsão real"else "Meu clima")})}}}
        if(mode=="Manual"){
            item{WeatherHero("Meu clima","${temp.toInt()}°",condition,"MODO MANUAL · PERSONALIZADO")}
            item{StudioCard{SectionTitle("Você escolhe o clima");Text("Temperatura: ${temp.toInt()} °C");Slider(value=temp,onValueChange={temp=it},valueRange=-20f..50f,steps=69);Choice("Condição",listOf("Céu limpo","Parcialmente nublado","Nublado","Chuva leve","Tempestade","Neve","Névoa"),condition,{condition=it});Button(onClick={vm.pref("weatherTemperature",temp.toInt().toString());vm.pref("weatherCondition",condition)},modifier=Modifier.fillMaxWidth()){Text("Salvar meu clima")};Text("Uma personalização visual escolhida por você. Não representa a previsão meteorológica.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        }else{
            if(city!=null)item{WeatherCard(city,vm)}else item{EmptyCard("O mundo cabe aqui","Busque sua cidade para ver temperatura, chuva, vento e previsão dos próximos dias.")}
            val cities=items.filter{it.type=="city"}
            if(cities.isNotEmpty())item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){cities.forEach{saved->FilterChip(selected=saved.id==city?.id,onClick={vm.pref("weatherCity",saved.id)},label={Text(saved.title.substringBefore(','))})}}}
            item{StudioCard{SectionTitle("Trocar ou adicionar cidade");if(vm.preferences["weatherConsent"]!="Sim"){Text("A busca envia a cidade ao Open-Meteo. A previsão usa suas coordenadas. Não precisamos de GPS.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick={vm.pref("weatherConsent","Sim")}){Text("Ativar previsão online")}}else{
                OutlinedTextField(query,{query=it;searched=false},label={Text("Nome da cidade")},modifier=Modifier.fillMaxWidth(),singleLine=true,leadingIcon={Icon(Icons.Default.LocationOn,null)})
                Button(onClick={vm.searchCity(query);searched=true},enabled=query.trim().length>=2 && !vm.busy,modifier=Modifier.fillMaxWidth()){Text("Buscar cidade")}
                if(searched && !vm.busy && vm.cityResults.isEmpty())Text("Nenhuma cidade encontrada. Tente outro nome.",style=MaterialTheme.typography.bodySmall)
            }}}
            items(vm.cityResults,key={it.id}){result->OutlinedCard(onClick={vm.addCity(result);searched=false},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(result.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Icon(Icons.Default.Add,null)}}}
            item{Text("Previsões: Open-Meteo · cidades: GeoNames. A última consulta fica disponível offline; veja a data antes de planejar seu dia.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable private fun WeatherHero(city:String,temperature:String,condition:String,source:String){
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF302A52),Color(0xFF222B46))))){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(source,style=MaterialTheme.typography.labelSmall,color=Color(0xFFC9BBF3));Text(city,style=MaterialTheme.typography.titleLarge,color=Color.White);Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(temperature,Modifier.weight(1f),style=MaterialTheme.typography.displayLarge,color=Color.White);Icon(weatherIcon(condition),null,Modifier.size(70.dp),tint=Color(0xFFF3CF96))};Text(condition,style=MaterialTheme.typography.titleMedium,color=Color(0xFFE5DDF8))}}
}
@Composable fun WeatherCard(city:Item,vm:VeyraViewModel){
    val json=runCatching{JSONObject(city.value("cache"))}.getOrNull();val current=json?.optJSONObject("current")
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(current!=null){
            WeatherHero(city.title,"${current.optDouble("temperature_2m").toInt()}°",Weather.condition(current.optInt("weather_code")),"PREVISÃO SALVA · ${city.value("updated").toLongOrNull()?.let{DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))} ?: "—"}")
            StudioCard{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Sensação",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${current.optDouble("apparent_temperature").toInt()}°",style=MaterialTheme.typography.titleMedium)};Column{Text("Umidade",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${current.optInt("relative_humidity_2m")}%",style=MaterialTheme.typography.titleMedium)};Column{Text("Vento",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("${current.optDouble("wind_speed_10m").toInt()} km/h",style=MaterialTheme.typography.titleMedium)}}}
            val hourly=json.optJSONObject("hourly");val times=hourly?.optJSONArray("time");val now=LocalDateTime.ofInstant(Instant.now(),java.time.ZoneOffset.ofTotalSeconds(json.optInt("utc_offset_seconds",0)));val next=if(times!=null)(0 until times.length()).filter{runCatching{LocalDateTime.parse(times.getString(it)).isAfter(now)}.getOrDefault(false)}.take(12)else emptyList()
            if(next.isNotEmpty())StudioCard{SectionTitle("Próximas horas");Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(22.dp)){next.forEach{n->Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Text(times!!.getString(n).substringAfter('T'),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Icon(Icons.Default.WbCloudy,null,tint=MaterialTheme.colorScheme.tertiary);Text("${hourly!!.optJSONArray("temperature_2m")?.optDouble(n)?.toInt()}°",style=MaterialTheme.typography.titleMedium);Text("${hourly.optJSONArray("precipitation_probability")?.optInt(n)}%",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}}}}
            val daily=json.optJSONObject("daily");val dates=daily?.optJSONArray("time")
            if(dates!=null)StudioCard{SectionTitle("Os próximos 7 dias");for(n in 0 until dates.length()){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(dateLabel(dates.getString(n)),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall);Icon(weatherIcon(Weather.condition(daily.optJSONArray("weather_code")?.optInt(n) ?: 2)),null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.tertiary);Spacer(Modifier.width(15.dp));Text("${daily.optJSONArray("temperature_2m_min")?.optDouble(n)?.toInt()}° / ${daily.optJSONArray("temperature_2m_max")?.optDouble(n)?.toInt()}°",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.width(12.dp));Text("${daily.optJSONArray("precipitation_probability_max")?.optInt(n)}%",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}}}
        }else EmptyCard(city.title,"Cidade salva. Atualize para consultar a previsão.")
        Row{Button(onClick={vm.weather(city)},enabled=vm.preferences["weatherConsent"]=="Sim" && !vm.busy){Icon(Icons.Default.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Atualizar previsão")};TextButton(onClick={vm.delete(city)}){Text("Remover")}}
    }
}
