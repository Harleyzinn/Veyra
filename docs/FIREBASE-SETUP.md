# Configurar a nuvem do Veyra

O aplicativo está preparado para Firebase Authentication, Cloud Firestore, Cloud Storage e App Check. Nenhum projeto de produção foi criado ou implantado nesta entrega: o proprietário informou que ainda não tem um projeto e pediu a preparação. Sem `app/google-services.json`, o APK funciona localmente e a tela Conta informa que a configuração está pendente. Login e sincronização não são simulados.

## 1. Registrar o aplicativo real

No [Console Firebase](https://console.firebase.google.com/), crie seu projeto ou selecione um projeto que você controla. Escolha o ID, a região dos serviços e as opções de cobrança antes de prosseguir; não há um ID de produção predefinido neste repositório. Analytics é opcional e não é usado pelo Veyra.

Em Configurações do projeto → Seus aplicativos → Adicionar aplicativo Android, informe:

- Pacote Android: **`app.veyra.life`**. O namespace de código `app.veyra.android` não substitui o applicationId.
- Apelido: uma identificação de sua escolha.
- SHA-1 do APK assinado atualmente: **`E2:E6:26:FE:15:10:B8:55:F4:B6:B9:C1:29:BE:FB:AC:B5:29:B8:36`**.
- SHA-256: **`85:95:F5:AC:F3:67:6E:23:6D:D3:21:98:A0:8E:71:BF:5D:04:D8:75:69:7D:39:5F:5B:CC:D6:B7:A4:49:8F:21`**.

Essas impressões foram verificadas no certificado público do APK Veyra 1.2.0. As atualizações devem manter a mesma chave de assinatura. Se você assinar com outra chave, confira o certificado do APK resultante e registre suas impressões. Para builds debug, registre também o certificado debug mostrado por `./gradlew.bat :app:signingReport`. Se publicar posteriormente na Play Store com Play App Signing, cadastre o certificado de assinatura usado pela loja.

Não envie a chave privada `.jks` nem a senha ao Firebase ou ao GitHub. O cadastro usa apenas as impressões públicas. Veja [Adicionar Firebase ao Android](https://firebase.google.com/docs/android/setup).

## 2. Habilitar os provedores de login

Em Authentication → Começar → Método de login:

1. Habilite **E-mail/senha**. O aplicativo pede pelo menos oito caracteres; uma política mais forte configurada no Firebase também será aplicada pelo serviço.
2. Habilite **Google**, escolha o e-mail de suporte e salve.
3. Confira os modelos de verificação de e-mail e recuperação de senha em Authentication → Modelos. A confirmação acontece pelo link enviado pelo Firebase; não existe confirmação local fictícia.
4. Após habilitar Google e cadastrar os certificados, baixe novamente `google-services.json` em Configurações do projeto → Seus aplicativos. Salve em **`app/google-services.json`**.

O login Google usa Credential Manager e o cliente OAuth de tipo **Web application** do mesmo projeto, por meio do recurso gerado `default_web_client_id`. Não coloque o ID do cliente Android no lugar dele. Se esse recurso estiver ausente, revise o provedor Google, o cliente Web em Google Cloud → APIs e serviços → Credenciais e baixe a configuração atualizada. O arquivo é ignorado pelo Git; o build aplica o plugin Google Services somente quando ele existe. [Configuração oficial do login Google](https://firebase.google.com/docs/auth/android/google-signin), [E-mail/senha](https://firebase.google.com/docs/auth/android/password-auth).

O Veyra bloqueia a sincronização e o acesso aos arquivos até a verificação do e-mail. Depois de confirmar o link, toque em **Já verifiquei** para renovar o usuário e o token.

## 3. Criar Firestore e Storage

Crie o banco **Cloud Firestore Standard/Native**, banco padrão `(default)`, e escolha a região que atende seus aparelhos. Use regras de produção: publique as regras deste repositório antes de testar dados reais. Não deixe regras públicas de modo de teste. Os caminhos privados são `users/{uid}/{coleção}/{idHash}`.

Para sincronizar comprovantes e anexos, habilite **Cloud Storage** no mesmo projeto. O nome do bucket vem da configuração real; buckets novos normalmente terminam em `.firebasestorage.app`. Baixe novamente `google-services.json` após provisionar o bucket.

Desde fevereiro de 2026, Cloud Storage for Firebase exige o plano **Blaze** com conta de cobrança vinculada. A configuração de cobrança e os alertas de orçamento precisam ser escolhidos pelo proprietário. O código não habilita cobrança. Sem um bucket disponível, login e documentos sem anexos podem funcionar, mas os anexos e a exclusão completa dos arquivos não podem ser concluídos. [Requisitos oficiais atuais de Storage](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024).

## 4. Publicar regras e índices

Instale Node.js compatível com a Firebase CLI e a CLI oficial. Na raiz do repositório, faça login pelo navegador e selecione explicitamente o seu projeto:

```powershell
firebase login
Set-Location firebase
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes,storage
```

O comando `use --add` deve selecionar o projeto real do proprietário. Não use `demo-veyra` para produção: ele é reservado aos emuladores. Confira o projeto selecionado antes de executar o deploy. Os arquivos publicados são `firestore.rules`, `firestore.indexes.json` e `storage.rules`; `firebase.json` já os referencia. Aguarde os índices ficarem prontos no Console. [CLI Firebase](https://firebase.google.com/docs/cli), [Gerenciar índices](https://firebase.google.com/docs/firestore/query-data/indexing), [Testar regras](https://firebase.google.com/docs/rules/unit-tests).

Recomendado para limpeza de contas excluídas: em Firestore → Time-to-live, configure uma política TTL no grupo de coleções `users`, campo **`expiresAt`**. O aplicativo mantém por pelo menos um dia um marcador mínimo `{ownerUid, deleting, updatedAt, expiresAt}` depois de remover os dados e antes de excluir o login; ele impede a recriação da conta por um token antigo ainda válido. A remoção por TTL é assíncrona e pode ter custo. Sem essa política, permanece apenas o marcador mínimo; os dados pessoais, subcoleções e arquivos já foram removidos. [TTL no Firestore](https://firebase.google.com/docs/firestore/ttl).

Não há Cloud Functions obrigatórias, service account no aplicativo, Admin SDK no APK, endpoint de administração ou credencial de servidor para preencher.

## 5. App Check para APK do GitHub

O APK utiliza o provedor **Play Integrity** em release. Para distribuição exclusivamente fora da Play Store:

1. Habilite a **Play Integrity API** no projeto Google Cloud associado ao Firebase. Se usar a Play Console, vincule o mesmo projeto em Integridade do aplicativo → Play Integrity. [Habilitar a API](https://developer.android.com/google/play/integrity/setup).
2. Em Firebase → App Check → Aplicativos, registre `app.veyra.life` com o SHA-256 do certificado release acima.
3. Nas configurações avançadas, **não exija `PLAY_RECOGNIZED` nem `LICENSED`** e selecione **Device integrity** como integridade mínima para o canal exclusivamente externo.
4. Distribua o APK configurado, valide num aparelho físico compatível e observe as métricas. Habilite enforcement dos serviços usados depois de confirmar solicitações legítimas; confira a disponibilidade e o provedor exigido para cada produto, especialmente Authentication.

As opções são as publicadas na [documentação oficial do App Check](https://firebase.google.com/docs/app-check/android/play-integrity-provider). Mudanças de canal, aparelhos modificados ou ausência dos serviços Google exigem nova avaliação. App Check protege o acesso ao backend; **não concede aprovação no Play Protect** nem remove automaticamente o aviso de APK não verificado. Veja [PLAY-PROTECT.md](PLAY-PROTECT.md).

Para desenvolvimento em emulador, o provedor debug só é habilitado em um build debug com a propriedade explícita:

```powershell
./gradlew.bat :app:assembleDebug -PveyraAppCheckDebug=true
```

Cadastre o token debug no Console App Check de forma privada, como explica a [documentação do provedor debug](https://firebase.google.com/docs/app-check/android/debug-provider). Não o publique em logs compartilhados, commits ou tickets. O pacote release não inclui o provedor debug e ignora essa opção. Não publique uma versão debug como release.

## 6. Gerar o APK configurado e conferir em dois aparelhos

Depois de colocar o arquivo real em `app/google-services.json` e publicar as regras, **aumente a versão em `app/build.gradle.kts`**. O APK desta entrega é 2.0.0, código 200, sem projeto Firebase: a próxima versão configurada deve usar no mínimo **`versionCode = 201` e `versionName = "2.0.1"`**, ou valores superiores à versão já publicada. O atualizador só aceita um código maior. Publique uma nova tag/release; não substitua o APK da tag 2.0.0 por um binário de configuração diferente.

Com a versão incrementada, rode na raiz:

```powershell
./scripts/build-apk.ps1
```

O script compila, verifica e assina com a chave local privada existente. Preserve essa chave para que o Android aceite atualizações sobre a instalação atual. O projeto usa JDK 17 para Android e Firebase BoM **33.16.0**, compatível com o compilador Kotlin 2.1.20 da base; não contorna incompatibilidade de metadados do Kotlin por flags.

Antes de distribuir uma versão com nuvem ativa, confira com o seu projeto:

- Cadastro, confirmação real de e-mail, recuperação de senha, Google e reautenticação para alterações sensíveis.
- Usuários A e B: notas, finanças e anexos separados; sair volta ao espaço local anterior.
- Dois aparelhos no mesmo UID: criação e edição aparecem no outro; edição offline sobre uma versão alterada gera conflito para revisão.
- Modo avião, encerramento/reabertura e retorno da rede: os dados e a fila continuam no banco local e o WorkManager retoma o envio.
- Comprovante privado, backup com anexo e importação explícita do espaço visitante; a origem permanece preservada.
- Exclusão de conta: reautenticação, arquivos/subcoleções removidos e bloqueio de novas gravações no UID excluído.
- Métricas App Check do APK baixado do GitHub antes de ativar enforcement.

O login Google e a atestação Play Integrity de produção dependem desse projeto e de aparelho/conta reais; não foram declarados aprovados por testes de emulador.

## 7. Rodar os testes locais de segurança

Na raiz:

```powershell
npm ci --prefix firebase
./scripts/test-firebase-security.ps1
```

Se necessário, informe `-JavaHome 'C:\caminho\do\jdk-21-ou-superior'`. O runtime de emuladores da Firebase CLI exige Java 21 ou superior; isso não muda o JDK 17 do build Android. Os testes usam exclusivamente o projeto local **`demo-veyra`**, portas Auth 9095, Firestore 8085 e Storage 9195. O script isola a configuração CLI em `.tools/firebase-cli-isolated`, não usa tokens/credenciais de servidor do ambiente e não implanta recursos de produção.

Os testes cobrem propriedade por UID, usuários diferentes, acesso anônimo, verificação de e-mail, revisão concorrente, transferências, unidades monetárias inteiras, datas e recorrências, preferências reais e privacidade do aparelho, anexos privados, exclusão e fluxos reais do Auth Emulator. Veja [CLOUD-ARCHITECTURE.md](CLOUD-ARCHITECTURE.md).
