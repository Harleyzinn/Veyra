package app.veyra.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable fun UpdateStudio(vm:VeyraViewModel){
    val context=LocalContext.current
    StudioCard{
        Text("Atualizações",style=MaterialTheme.typography.titleLarge)
        Text("Veyra ${GitHubUpdater.currentVersion(context)}")
        Row{Switch(vm.preferences["autoUpdateCheck"]!="Não",{vm.pref("autoUpdateCheck",if(it)"Sim"else "Não")});Text("Buscar novas versões automaticamente",Modifier.padding(start=12.dp,top=12.dp))}
        Row{Switch(vm.preferences["autoUpdateDownload"]!="Não",{vm.pref("autoUpdateDownload",if(it)"Sim"else "Não")});Text("Baixar automaticamente em Wi-Fi",Modifier.padding(start=12.dp,top=12.dp))}
        Text("O app consulta a release oficial do GitHub diariamente e pode baixar em redes sem cobrança por uso. O Android pede confirmação para instalar.",style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={vm.checkUpdates()},enabled=!vm.updateBusy){Text("Buscar atualização agora")}
        if(vm.updateBusy){if(vm.updateStatus.startsWith("Baixando"))LinearProgressIndicator(progress={vm.updateProgress},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(vm.updateStatus.isNotBlank())Text(vm.updateStatus)
        vm.updateRelease?.let{release->
            var expanded by remember(release.tag){mutableStateOf(false)}
            Text(release.title,style=MaterialTheme.typography.titleMedium)
            Text(release.notes,style=MaterialTheme.typography.bodySmall,maxLines=if(expanded)Int.MAX_VALUE else 6,overflow=TextOverflow.Ellipsis)
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Recolher novidades"else "Ler novidades")}
            if(vm.updateFile==null)Button(onClick={vm.downloadUpdate()},enabled=!vm.updateBusy){Text("Baixar ${release.version} • ${release.size/1_000_000} MB")}
            else {Button(onClick={vm.installUpdate()},enabled=!vm.updateBusy){Text("Instalar atualização")};Text("Se o Android abrir a permissão de instalação, autorize para o Veyra, volte e toque em Instalar atualização.",style=MaterialTheme.typography.bodySmall)}
        }
    }
}
