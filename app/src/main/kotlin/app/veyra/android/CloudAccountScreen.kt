package app.veyra.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.veyra.cloud.*
import app.veyra.data.SyncConflict
import app.veyra.model.Item
import java.text.DateFormat
import java.util.Date

/** Authentication and backup controls invoke the real controller, never UI-only state. */
@Composable
fun CloudAccountScreen(controller: CloudController, onBack: () -> Unit = {}) {
    val state by controller.state.collectAsState()
    val activity = LocalContext.current.cloudActivity()
    var creatingAccount by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Passwords are transient and are never put in the activity's saved Bundle or preferences.
    var password by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmail by rememberSaveable { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var showAccountChanges by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var conflicts by remember { mutableStateOf<List<SyncConflict>>(emptyList()) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { controller.launch { exportBackup(it, backupPassword) } }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { controller.launch { importBackup(it, backupPassword) } }
    }
    LaunchedEffect(state.conflictCount, state.user?.uid, state.lastSync) {
        try { conflicts = controller.conflicts() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { conflicts = emptyList() }
    }
    LaunchedEffect(state.user?.uid) { name = state.user?.name.orEmpty(); password = ""; currentPassword = ""; newPassword = "" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) {
                    Text("Sua conta e seus dados", style = MaterialTheme.typography.headlineSmall)
                    Text("Privacidade, sincronização e backup", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.busy || state.status == SyncStatus.SYNCING) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item { CloudCard("Não foi possível concluir") { Text(error, color = MaterialTheme.colorScheme.error) } } }
        if (state.message.isNotBlank()) item { Text(state.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
        if (state.configured && !state.cloudAttachmentsEnabled) item {
            CloudCard("Nuvem no plano gratuito") {
                Text("Login e registros são sincronizados. Fotos, PDFs e outros anexos ficam somente no aparelho onde foram adicionados. Exporte um backup completo antes de limpar o cache ou trocar de celular.")
            }
        }
        if (!state.configured) item {
            CloudCard("Firebase aguardando configuração") {
                Text("Este APK funciona localmente. Login e sincronização serão ativados após adicionar a configuração real do seu projeto Firebase e gerar o APK novamente.")
                Text("O guia docs/FIREBASE-SETUP.md no repositório explica o cadastro de app.veyra.life, Google, e-mail, Firestore, Storage, regras e App Check.", style = MaterialTheme.typography.bodySmall)
                Text("Não há conta ou conexão simulada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.configured && state.user == null) item {
            CloudCard(if (creatingAccount) "Crie sua conta" else "Entre no Veyra") {
                if (activity != null) Button(onClick = { controller.launch { google(activity) } }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Continuar com Google") }
                HorizontalDivider()
                if (creatingAccount) OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val secret = password; password = ""; controller.launch { if (creatingAccount) register(name, email, secret) else signIn(email, secret) } }, enabled = !state.busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (creatingAccount) "Criar conta" else "Entrar") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { creatingAccount = !creatingAccount }) { Text(if (creatingAccount) "Já tenho conta" else "Cadastrar") }
                    TextButton(onClick = { controller.launch { resetPassword(email) } }, enabled = email.isNotBlank() && !state.busy) { Text("Recuperar senha") }
                }
                Text("Seu espaço local continua preservado. Depois de entrar, você escolhe se deseja importá-lo.", style = MaterialTheme.typography.bodySmall)
            }
        }
        state.user?.let { user ->
            item {
                CloudCard(user.name.ifBlank { "Minha conta" }) {
                    Text(user.email)
                    Text("UID: ${user.uid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (user.createdAt > 0) Text("Conta criada em ${cloudDate(user.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    if (!user.verified) {
                        Text("Confirme seu e-mail para sincronizar seus dados.", color = MaterialTheme.colorScheme.tertiary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { controller.launch { sendVerification() } }, enabled = !state.busy) { Text("Enviar verificação") }
                            TextButton(onClick = { controller.launch { reloadUser() } }, enabled = !state.busy) { Text("Já verifiquei") }
                        }
                    }
                    OutlinedButton(onClick = { controller.launch { signOut(activity) } }, enabled = !state.busy) { Text("Sair da conta") }
                    Text("Ao sair, este espaço fica separado do aparelho e das outras contas. Alterações ainda não enviadas permanecem salvas para quando você voltar.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                CloudCard("Dados e backup") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (state.status == SyncStatus.READY) Icons.Default.CloudDone else Icons.Default.CloudOff, null)
                        Text(state.status.label, style = MaterialTheme.typography.titleSmall)
                    }
                    Text("Última sincronização: ${if (state.lastSync == 0L) "Ainda não concluída" else cloudDate(state.lastSync)}")
                    Text("${state.localCount} registros neste espaço · ${state.pending} alterações na fila · ${state.conflictCount} conflitos")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sincronização automática", Modifier.weight(1f))
                        Switch(state.syncEnabled, { controller.launch { setSyncEnabled(it) } }, enabled = !state.busy)
                    }
                    Button(onClick = { controller.launch { syncNow() } }, enabled = !state.busy && state.syncEnabled && user.verified) { Text("Sincronizar agora") }
                    Text("Sem internet, o app salva no banco local. A fila é enviada quando a conexão retorna. Versões concorrentes ficam para sua revisão.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmation = "cache" }, enabled = !state.busy && state.pending == 0 && state.conflictCount == 0 && state.lastSync > 0) { Text("Limpar cache local") }
                }
            }
            if (state.migrationAvailable > 0 && !state.migrationComplete) item {
                CloudCard("Importar o espaço deste aparelho") {
                    Text("Foram encontrados ${state.migrationAvailable} registros locais. Você pode trazê-los para ${user.email}. IDs são preservados, e o original continua no aparelho.")
                    Text("A migração só é marcada como concluída depois da confirmação do Firestore.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { confirmation = "migration" }, enabled = !state.busy && user.verified) { Text("Importar dados locais") }
                }
            }
            if (state.migrationComplete) item { Text("Importação local concluída e confirmada pela nuvem.", color = MaterialTheme.colorScheme.primary) }
            conflicts.forEach { conflict -> item(key = conflict.id) {
                CloudCard("Revisar: ${conflict.localItem.title}") {
                    Text("Este aparelho", style = MaterialTheme.typography.labelLarge)
                    Text(cloudVersion(conflict.localItem), style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Outro dispositivo · revisão ${conflict.serverRevision}", style = MaterialTheme.typography.labelLarge)
                    Text(cloudVersion(conflict.remoteItem), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { controller.launch { resolveConflict(conflict.id, false) } }, enabled = !state.busy) { Text("Manter este aparelho") }
                        Button(onClick = { controller.launch { resolveConflict(conflict.id, true) } }, enabled = !state.busy) { Text("Usar outra versão") }
                    }
                    Text("A versão substituída permanece no histórico local.", style = MaterialTheme.typography.bodySmall)
                }
            } }
            item {
                CloudCard("Perfil e segurança") {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { controller.launch { rename(name) } }, enabled = !state.busy && name.isNotBlank()) { Text("Atualizar nome") }
                    TextButton(onClick = { showAccountChanges = !showAccountChanges }) { Text(if (showAccountChanges) "Fechar alterações de acesso" else "Alterar e-mail ou senha") }
                    if (showAccountChanges) {
                        if (user.passwordProvider) OutlinedTextField(currentPassword, { currentPassword = it }, label = { Text("Senha atual para confirmar identidade") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(newEmail, { newEmail = it }, label = { Text("Novo e-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (activity != null) OutlinedButton(onClick = { val secret = currentPassword; currentPassword = ""; controller.launch { changeEmail(activity, secret, newEmail) } }, enabled = !state.busy && newEmail.isNotBlank() && (!user.passwordProvider || currentPassword.isNotBlank())) { Text("Confirmar novo e-mail") }
                        if (user.passwordProvider) {
                            OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Nova senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { val old = currentPassword; val replacement = newPassword; currentPassword = ""; newPassword = ""; controller.launch { changePassword(old, replacement) } }, enabled = !state.busy && currentPassword.isNotBlank() && newPassword.length >= 8) { Text("Atualizar senha") }
                        }
                    }
                }
            }
        }
        item {
            CloudCard("Exportar e importar") {
                Text("JSON preserva registros, IDs e preferências permitidas. Senhas, PIN, tokens e chaves privadas não são exportados.")
                OutlinedTextField(backupPassword, { backupPassword = it }, label = { Text("Senha do backup · opcional para exportar") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Com uma senha de 8 caracteres ou mais, a exportação usa AES-GCM. Para importar um backup protegido, digite a senha original.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { export.launch("Veyra-backup.json") }, enabled = !state.busy && (backupPassword.isBlank() || backupPassword.length >= 8)) { Text("Exportar meus dados") }
                    OutlinedButton(onClick = { confirmation = "import" }, enabled = !state.busy) { Text("Importar backup") }
                }
                Text("Anexos da nuvem são baixados sob demanda durante a exportação. Isso requer conexão; o backup não finge conter um arquivo indisponível.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            CloudCard("Privacidade e dados") {
                Text("São armazenados os registros que você cria, preferências permitidas, anexos e o perfil da conta. O histórico de alterações e conflitos fica no banco deste espaço.")
                OutlinedButton(onClick = { confirmation = "trash" }, enabled = !state.busy) { Text("Mover todos os dados para a lixeira") }
                if (state.user != null && activity != null) {
                    Text("Excluir a conta remove registros e arquivos da nuvem. A autenticação é confirmada novamente antes da exclusão.", style = MaterialTheme.typography.bodySmall)
                    if (state.user!!.passwordProvider) OutlinedTextField(currentPassword, { currentPassword = it }, label = { Text("Senha atual para excluir a conta") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { confirmation = "account" }, enabled = !state.busy && (!state.user!!.passwordProvider || currentPassword.isNotBlank()), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Excluir minha conta") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    confirmation?.let { action ->
        val details = when (action) {
            "cache" -> "Somente o cache desta conta será removido. Registros confirmados serão carregados da nuvem novamente."
            "migration" -> "Importar os dados preservados deste aparelho para ${state.user?.email}? A origem permanece salva."
            "trash" -> "Mover os registros do espaço atual para a lixeira? Na conta vinculada, a alteração será sincronizada com outros dispositivos."
            "account" -> "Excluir permanentemente ${state.user?.email}, seus registros e arquivos da nuvem? Exporte um backup antes se quiser guardá-los."
            else -> "O backup será mesclado neste espaço. IDs iguais atualizam o registro existente; a validação ocorre antes de gravar qualquer alteração."
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(if (action == "account") "Excluir conta permanentemente" else "Confirmar alteração de dados") }, text = { Text(details) },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                when (action) {
                    "cache" -> controller.launch { clearCache() }
                    "migration" -> controller.launch { importGuest() }
                    "trash" -> controller.launch { trashAllData() }
                    "account" -> activity?.let { selected -> val secret = currentPassword; currentPassword = ""; controller.launch { deleteAccount(selected, secret) } }
                    else -> importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }) { Text(if (action == "account") "Excluir permanentemente" else "Confirmar") } })
    }
}

@Composable private fun CloudCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    } }
}
private fun Context.cloudActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.cloudActivity()
    else -> null
}
private fun cloudDate(value: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
private fun cloudVersion(item: Item): String = buildString {
    append(item.title).append(" · ").append(item.date)
    if (item.value("amount").isNotBlank()) append("\n").append(item.value("currency").ifBlank { "BRL" }).append(" ").append(item.value("amount"))
    if (item.value("category").isNotBlank()) append(" · ").append(item.value("category"))
    if (item.value("status").isNotBlank()) append(" · ").append(item.value("status"))
    if (item.deletedAt != 0L) append("\nMovido para a lixeira")
    if (item.notes.isNotBlank()) append("\n").append(item.notes.take(300))
    val labels = Registry.spec(item.type).fields.associate { it.key to it.label } + mapOf(
        "name" to "Nome", "theme" to "Tema", "financeCurrency" to "Moeda", "financeHidden" to "Ocultar valores",
        "financialDay" to "Início do mês financeiro", "recentIncomeCategory" to "Última categoria de receita",
        "recentExpenseCategory" to "Última categoria de despesa", "recentFinanceAccount" to "Última conta",
        "account" to "Conta", "destination" to "Destino", "card" to "Cartão", "dueDate" to "Vencimento",
        "settledDate" to "Liquidação", "startDate" to "Início", "endDate" to "Fim", "currentMinor" to "Valor atual em unidades menores")
    val omitted = setOf("attachment", "cloudAttachmentPath", "cloudAttachmentSha256", "cloudAttachmentSize",
        "attachmentHash", "financialVersion", "amount", "currency", "category", "status")
    item.fields.toSortedMap().filterKeys { it !in omitted }.forEach { (key, value) ->
        if (value.isNotBlank()) append("\n").append(labels[key] ?: key).append(": ").append(value.take(180))
    }
    if (item.tags.isNotBlank()) append("\nEtiquetas: ").append(item.tags)
    if (item.value("cloudAttachmentPath").isNotBlank() || item.value("attachment").isNotBlank()) append("\nAnexo incluído")
}
