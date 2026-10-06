package app.veyra.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable fun Onboarding(vm:VeyraViewModel){
    var name by remember{mutableStateOf("")};var theme by remember{mutableStateOf("Escuro")};var profile by remember{mutableStateOf("Completo")}
    Dialog(onDismissRequest={},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
                Spacer(Modifier.height(10.dp))
                Box(Modifier.size(82.dp).background(Brush.linearGradient(listOf(Color(0xFFCCBBFF),Color(0xFF9274DC))),RoundedCornerShape(26.dp)),contentAlignment=Alignment.Center){Text("v",style=MaterialTheme.typography.displayLarge,color=Color(0xFF251B3B))}
                PageHeading("BEM-VINDO AO VEYRA","Sua vida.\nUm só lugar.","Menos apps abertos. Mais espaço para viver.")
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
                    listOf("task" to "Tarefas, planos e conquistas","account" to "Seu dinheiro sob controle","note" to "Ideias que não se perdem","city" to "Clima e ferramentas de bolso").forEach{(type,label)->Row(verticalAlignment=Alignment.CenterVertically){IconBadge(moduleIcon(type),size=34);Spacer(Modifier.width(12.dp));Text(label,style=MaterialTheme.typography.bodyMedium)}}
                }
                OutlinedTextField(name,{name=it.take(30)},label={Text("Como podemos chamar você?")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row{Choice("Tema",listOf("Escuro","Claro","AMOLED","Sistema","Dinâmico"),theme,{theme=it});Choice("Perfil",listOf("Completo","Essencial","Produtividade","Estudos","Finanças"),profile,{profile=it})}
                Button(onClick={vm.pref("theme",theme);vm.pref("profile",profile);vm.pref("name",name.trim())},enabled=name.isNotBlank() && !vm.busy,modifier=Modifier.fillMaxWidth().height(54.dp)){Text("Criar meu espaço");Spacer(Modifier.width(12.dp));Icon(Icons.Default.ArrowForward,null,Modifier.size(18.dp))}
                Text("Seus registros ficam neste aparelho. Sem conta obrigatória, anúncios ou rastreadores. Conexões online são opcionais.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
