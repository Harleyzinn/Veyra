# Veyra Life Desktop 3.0

O Veyra agora tem um aplicativo Windows próprio, mantendo o Android nativo e o projeto Firebase existente. Instale o EXE da [release desktop](https://github.com/Harleyzinn/Veyra/releases/tag/desktop-v3.0.1) e o APK 2.2.0 da [release Android](https://github.com/Harleyzinn/Veyra/releases/tag/v2.2.0). O instalador oferece pasta de destino, menu Iniciar e atalho. Não exige ferramentas de desenvolvimento. Faça um backup antes de atualizar o celular.

Na primeira execução, escolha **Continuar com Google** e use a mesma conta do celular. O login abre o navegador padrão do Windows; não informe sua senha em uma janela incorporada do Veyra. O espaço visitante é separado e continua disponível offline. Os registros de visitante não são enviados para uma conta automaticamente: use a exportação e a prévia de importação se quiser transferi-los.

## No computador

- Central com widgets reorganizáveis por arraste, larguras ajustáveis e seleção de conteúdo. Temas claro, escuro e sistema, quatro destaques, densidade e escala.
- Meu Dia com agenda, tarefas, hábitos, clima, metas e visão financeira. Calendário em dia, semana, mês e agenda, com reagendamento por arraste.
- Finanças com captura simples/avançada, contas, cartões, fechamento e faturas, pagamentos parciais, parcelas, recorrências pausáveis, orçamentos, metas, categorias, projeções até 365 dias, simulador com reserva e conferência de duplicidades. Os valores usam unidades monetárias exatas. Transferências não viram despesas e pagamento de fatura não duplica a compra no cartão.
- Tarefas com filtros, ordenação, prioridades, projetos, vínculos, subtarefas, recorrências e Kanban com colunas personalizadas.
- Notas com Markdown, tabelas, código, pastas, tags, favoritos, anexos locais, autosave e histórico restaurável. Uma segunda janela permite escrever em outro monitor. Imagens remotas em Markdown não são carregadas automaticamente.
- Hábitos com calendário e sequências que respeitam dias úteis/fins de semana. Metas e projetos mostram progresso e registros relacionados.
- Pomodoro, pausas, sessão personalizada e cronômetro, com pausa, histórico e overlay acima das janelas. O cronômetro tem limite de segurança de 24 horas por sessão.
- Mini dashboard e widgets independentes de finanças, tarefas, calendário, clima, hábitos e foco. As janelas lembram posição e tamanho e retornam à área visível quando um monitor é removido.
- Captura rápida com tarefa, gasto, entrada, nota, evento e lembrete; busca universal e comandos em Ctrl+K; atalhos globais configuráveis com detecção de conflitos.
- Bandeja Windows, notificações opcionais, início com Windows e modo minimizado. Clipboard opt-in com leitura somente por comando, prévia e confirmação. Arraste arquivos para criar registros com anexos locais.
- Explorador com os 81 tipos de registro identificados nos catálogos Kotlin. Cada tipo tem formulário compatível e operações de criação/edição; as ferramentas especializadas do Android permanecem no celular.

## Sincronização e privacidade

O desktop usa Authentication e Firestore do projeto `veyra-life-harleyzinn`. A configuração pública do aplicativo Web existente é embarcada no instalador; nenhuma conta administrativa, senha ou chave privada vai junto. A identidade é o UID Firebase, não o e-mail digitado.

Alterações locais são gravadas antes de qualquer tentativa de rede. Cada conta possui arquivo SQLite criptografado próprio, incluindo fila de operações e histórico. O servidor aplica as mesmas regras do Android. O desktop consulta um pequeno marcador de alteração a cada 30 segundos; só lê os registros incrementais quando necessário. O Android 2.2 observa o mesmo marcador enquanto o controlador da aplicação está ativo. O trabalho de fundo do Android continua sujeito ao agendamento e às restrições de bateria do sistema.

Conflitos preservam as duas versões. Em **Configurações → Dados**, escolha qual versão manter; a versão substituída continua no histórico. Exclusões são lógicas e chegam à lixeira. Arquivos não são enviados para Firebase Storage: o plano gratuito permanece sem cobrança e os anexos ficam no dispositivo onde foram adicionados.

O registro de dispositivos mostra presença recente, não comprova uma sessão ativa. A revogação remota não é oferecida pelo cliente sem um backend administrativo adequado. Espaços compartilhados estão previstos na arquitetura, mas as regras atuais mantêm tudo privado.

## Backup e recuperação

Em **Configurações → Dados**, exporte um backup compatível com o Android. Preferências e aparência podem ser restauradas; permissões locais de clipboard, notificações e inicialização não são copiadas de outro dispositivo. Uma senha de pelo menos oito caracteres protege o arquivo com AES-GCM/PBKDF2. Use essa mesma senha ao importar. Sem senha, o JSON exportado contém dados privados em texto legível. A importação mostra os novos registros e as diferenças; não substitui registros existentes por padrão.

O cache fica em `%APPDATA%/Veyra Life/vault`. A chave local é protegida pelo Windows DPAPI da conta atual. Cada gravação usa arquivo temporário, sincronização em disco e troca atômica, mantendo uma cópia `.previous`. Não apague `vault.key`, `workspace-*.safe` ou `session.safe` para resolver um erro. Copie a pasta inteira antes de tentar recuperação; uma chave protegida por DPAPI não pode ser transportada livremente para outra conta Windows. Se houver corrupção, preserve os arquivos e restaure um backup pela interface. A desinstalação preserva o diretório de dados.

## Atualizações

**Configurações → Atualizações** consulta somente releases `desktop-vX.Y.Z` do repositório oficial. O manifesto usa assinatura Ed25519, com chave pública fixada no aplicativo, e o instalador usa SHA-256. O conteúdo é verificado novamente antes de executá-lo. O reinício para instalar exige ação do usuário.

Essa assinatura de atualização é diferente de Authenticode. Esta distribuição não tem certificado comercial de editor Windows; SmartScreen pode mostrar um aviso de aplicativo sem reputação. Também não se pode garantir aprovação automática do Play Protect para um APK distribuído fora da Play Store.

## Desenvolvimento e build

Windows x64, Node 24 e npm. Na raiz Android, JDK 17/SDK 35 continuam sendo usados. Nenhuma pasta Android foi movida.

```powershell
cd apps/desktop
npm ci
# Obtenha a configuração WEB do projeto próprio pelo Firebase CLI.
# Salve o objeto sdkConfig em resources/firebase.json, sem credenciais Admin.
node scripts/prepare-config.mjs
npm run typecheck
npm run lint
npm test
npm run build
npm run test:ui
npm run dev
npm run dist
```

`prepare-config` gera uma chave privada de atualização apenas quando ainda não existe; nunca a publique. Para manter a cadeia de confiança desta distribuição, o mantenedor precisa preservar a chave original em `.signing`. Builds de terceiros devem usar sua própria chave pública e seu próprio canal de distribuição.

Da raiz do repositório, execute `./scripts/test-firebase-security.ps1 -Desktop -JavaHome '<JDK 21 ou superior>'`. Os testes usam exclusivamente `demo-veyra`, com Authentication, Firestore e Storage locais. O JDK dos emuladores é independente do JDK 17 do Android. `VEYRA_QA=1` e `VEYRA_QA_DATA` são aceitos somente em execução de desenvolvimento e restringem o cache a `apps/desktop/.qa`; `VEYRA_EMULATOR=1` seleciona os servidores locais nesse modo. Não existem segredos administrativos em variáveis do renderer.

Depois de validar, faça commit/push e execute `./scripts/publish-desktop-release.ps1`. O script publica a release com o instalador, manifesto assinado, código e hashes; não altera a release mais recente do APK. Use `./scripts/publish-release.ps1 -Version 2.2.0` para o Android.

## Solução de problemas

- Login Google: use o navegador padrão e permita o popup aberto pelo botão Google. `localhost` precisa estar entre os domínios autorizados do projeto Firebase; ele foi configurado no projeto existente.
- E-mail sem confirmação: confirme antes de sincronizar e use **Já confirmei o e-mail**. O visitante continua funcionando.
- Conexão pendente: consulte a mensagem em Conta; o cache e a fila continuam salvos. O aplicativo tenta novamente automaticamente.
- Atalho em uso: altere-o nas configurações; os atalhos anteriores são restaurados se a nova configuração falhar.
- Anexo ausente no PC: exporte-o no aparelho onde foi adicionado. Nenhum download de anexo na nuvem é prometido no plano gratuito.
- App Check: a proteção Play Integrity do Android permanece registrada. Antes de habilitar enforcement global, é necessário planejar uma forma de atestar o desktop; não desative regras de UID nem embarque chaves Admin como contorno.

O login Google real depende da interação do titular da conta. Os testes automatizados cobrem Auth por e-mail em emulador, regras e sincronização entre clientes; não substituem uma validação manual com o celular e a conta Google reais.

Veja os [resultados de validação desta versão](VALIDATION-DESKTOP-3.0.1.md).


