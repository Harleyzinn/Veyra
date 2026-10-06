package app.veyra.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.veyra.model.Item
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val Brazilian:Locale=Locale.forLanguageTag("pt-BR")
fun dateLabel(date:String)=runCatching{LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM",Brazilian))}.getOrDefault("Sem data")
fun moduleIcon(type:String):ImageVector=when(type){
    "sketch"->Icons.Default.Brush;"scanner"->Icons.Default.QrCodeScanner;"decisions"->Icons.Default.Casino;"playroom"->Icons.Default.SportsEsports;"worldclock"->Icons.Default.Public
    "routine"->Icons.Default.DashboardCustomize;"checklist","chore"->Icons.Default.Checklist;"meal"->Icons.Default.Restaurant;"pet"->Icons.Default.Pets;"medicine"->Icons.Default.Medication;"appointment"->Icons.Default.LocalHospital;"mood"->Icons.Default.Mood;"measurement"->Icons.Default.MonitorWeight;"exercise"->Icons.Default.FitnessCenter;"countdown"->Icons.Default.HourglassBottom;"savings_goal"->Icons.Default.Savings;"debt","bill"->Icons.Default.ReceiptLong;"wishlist","gift"->Icons.Default.CardGiftcard;"course"->Icons.Default.School;"job"->Icons.Default.WorkOutline;"subscription_audit"->Icons.Default.ManageSearch
    "task"->Icons.Default.CheckCircleOutline;"event","plan"->Icons.Default.CalendarMonth;"project"->Icons.Default.FolderOpen;"goal"->Icons.Default.Flag
    "income"->Icons.Default.SouthWest;"expense"->Icons.Default.NorthEast;"account"->Icons.Default.AccountBalanceWallet;"card"->Icons.Default.CreditCard
    "budget"->Icons.Default.PieChartOutline;"subscription"->Icons.Default.Autorenew;"investment"->Icons.Default.TrendingUp;"transfer"->Icons.Default.SwapHoriz
    "note"->Icons.AutoMirrored.Filled.Notes;"journal"->Icons.Default.AutoStories;"link"->Icons.Default.Link;"habit"->Icons.Default.LocalFireDepartment
    "water"->Icons.Default.WaterDrop;"sleep"->Icons.Default.Bedtime;"health"->Icons.Default.FavoriteBorder;"workout"->Icons.Default.FitnessCenter
    "city"->Icons.Default.WbSunny;"focus"->Icons.Default.Timer;"subject","grade","exam","flashcard"->Icons.Default.School
    "shopping"->Icons.Default.ShoppingBasket;"trip","reservation","packing"->Icons.Default.FlightTakeoff;"vehicle","fuel","maintenance"->Icons.Default.DirectionsCar
    "contact","birthday"->Icons.Default.PeopleOutline;"book"->Icons.Default.MenuBook;"movie"->Icons.Default.Movie;"game"->Icons.Default.SportsEsports
    "tools"->Icons.Default.Handyman;"assistant"->Icons.Default.AutoAwesome;"rule","template"->Icons.Default.Bolt;"inbox"->Icons.Default.Inbox
    else->Icons.Default.Widgets
}
@Composable fun IconBadge(icon:ImageVector,tint:Color=MaterialTheme.colorScheme.primary,size:Int=44){
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size/3).dp)).background(tint.copy(alpha=.12f)),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size((size/2).dp),tint=tint)}
}
@Composable fun SectionTitle(title:String,action:String="",onAction:()->Unit={}){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);if(action.isNotBlank())TextButton(onClick=onAction,contentPadding=PaddingValues(horizontal=0.dp)){Text(action)}}
}
@Composable fun PageHeading(eyebrow:String,title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(5.dp)){Text(eyebrow.uppercase(),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.headlineLarge);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun StudioCard(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Card(modifier.fillMaxWidth().border(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f),RoundedCornerShape(24.dp)),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
@Composable fun MiniMetric(label:String,value:String,icon:ImageVector,tint:Color,modifier:Modifier=Modifier){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,Modifier.size(21.dp),tint=tint);Text(value,style=MaterialTheme.typography.titleLarge,maxLines=1);Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
}
@Composable fun CompactTask(item:Item,vm:VeyraViewModel,open:(Item)->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable{open(item)}.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        if(item.type=="task")IconButton(onClick={vm.complete(item)},modifier=Modifier.size(32.dp)){Icon(if(item.done)Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,"Concluir ${item.title}",tint=if(item.done)MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)}else IconBadge(moduleIcon(item.type),size=32)
        Column(Modifier.weight(1f)){Text(item.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${dateLabel(item.date)}${item.value("time").takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        if(item.value("priority") in listOf("Alta","Urgente"))Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary,CircleShape))
        Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun ItemCard(item:Item,open:(Item)->Unit,edit:(Item)->Unit,delete:(Item)->Unit,vm:VeyraViewModel){
    var menu by remember{mutableStateOf(false)}
    Card(onClick={open(item)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(item.type),size=36);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(Registry.spec(item.type).label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(dateLabel(item.date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick={vm.save(item.copy(favorite=!item.favorite))},modifier=Modifier.size(32.dp)){Icon(if(item.favorite)Icons.Default.Star else Icons.Default.StarBorder,"Favoritar",tint=MaterialTheme.colorScheme.tertiary)};Box{IconButton(onClick={menu=true},modifier=Modifier.size(32.dp)){Icon(Icons.Default.MoreVert,"Opções")};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){DropdownMenuItem(text={Text("Editar")},onClick={menu=false;edit(item)});DropdownMenuItem(text={Text("Mover à lixeira")},onClick={menu=false;delete(item)})}}
            }
            Text(item.title,style=MaterialTheme.typography.titleMedium)
            if(item.notes.isNotBlank())Text(item.notes,maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(item.value("amount").isNotBlank())Text(money(item.cents()),style=MaterialTheme.typography.titleLarge)
            if(item.tags.isNotBlank())Text(item.tags,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            if(item.type!="bill" && (Registry.spec(item.type).checkable || item.type in setOf("expense","income") && item.value("planned")=="Sim"))TextButton(onClick={vm.complete(item)}){Text(if(item.done)"✓ Concluído"else if(item.type in setOf("income","expense"))"Marcar como realizado"else "Concluir")}
            if(item.type=="habit")TextButton(onClick={vm.checkin(item)}){Text("Registrar hoje")}
        }
    }
}
