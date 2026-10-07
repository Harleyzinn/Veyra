# Dados locais, conta e sincronização

## Fronteiras e isolamento

`core:cloud` é uma biblioteca Android usada pela tela Conta. `CloudController` publica `StateFlow<CloudState>` e integra as mudanças de conta com callbacks do ViewModel; `CloudAuthentication` chama Firebase Auth real; `CloudSyncEngine` implementa o protocolo; `CloudSyncWorker` o retoma fora da tela. `core:data` continua responsável pelo banco local e não depende do Firebase.

O espaço visitante permanece em `veyra.db`. Cada UID recebe um arquivo físico `veyra-user-{sha256(uid)}.db`, preferências, metadados, fila e conflitos próprios. Entrar não converte silenciosamente o banco visitante. A troca de conta cancela trabalhos antigos e limpa a apresentação antes de reabrir o banco correto. As operações suspensas capturam o UID e o conferem antes de aplicar resultados; uma resposta da conta anterior não atualiza o espaço atual.

O cache persistente compartilhado do SDK Firestore foi substituído por cache de memória. Room é o armazenamento offline durável, isolado por UID, e guarda anexos necessários ao trabalho offline. Limpar cache só é permitido na conta autenticada sem operações ou conflitos pendentes. O espaço visitante nunca é tratado como cache removível.

## Contrato remoto

As coleções abaixo ficam dentro de `users/{uid}`. O identificador remoto é SHA-256 do ID local; o ID original permanece no documento e é preservado em backups.

| Coleção | Conteúdo |
| --- | --- |
| `transactions` | Receitas, despesas, transferências, contas a pagar/receber e pagamentos de fatura |
| `accounts`, `creditCards` | Contas, carteiras e cartões |
| `recurringTransactions` | Regras recorrentes e suas exceções |
| `budgets`, `goals`, `subscriptions`, `debts` | Planejamento financeiro e obrigações |
| `automationRules`, `categories`, `templates` | Regras, categorias e modelos |
| `assets` | Investimentos, `financial_asset`, snapshots, fechamentos e registros de transporte de saldo |
| `notes`, `tasks` | Notas e tarefas |
| `settings` | Preferências permitidas, sem segredos ou permissões de outro aparelho |
| `workspace` | Demais tipos conhecidos pelo aplicativo |

O perfil `users/{uid}` mantém nome, e-mail, foto proveniente do Auth, estado de verificação, criação, último acesso, atualização e marcador de alteração. Não armazena senha, PIN ou token de login. O aplicativo não precisa de Analytics, anúncios, service accounts ou Cloud Functions para esse protocolo.

Os documentos levam `ownerUid`, conteúdo do Item, `revision`, `operationId`, `updatedAt` do servidor e campos tipados de consulta, como `amountMinor`, moeda, categoria, conta, cartão, status e recorrência. Valores financeiros persistentes são inteiros em unidades menores; o limite aceito é ±900.000.000.000.000. Receitas/despesas não usam montantes negativos para inverter a direção. Snapshots podem ter patrimônio/economia negativos. Datas civis usam `YYYY-MM-DD` e são verificadas como datas reais.

## Fila, confirmação e conflitos

Uma alteração local grava Item, auditoria, revisão local e outbox na mesma transação Room. O usuário pode continuar trabalhando offline. A outbox consolida a versão mais recente de cada registro, mantendo a revisão conhecida no servidor como base; uma edição durante o envio não é perdida quando a versão anterior é confirmada.

O envio faz uma transação Firestore: lê o perfil e a revisão remota, confere o `baseServerRevision`, grava a revisão seguinte e atualiza o marcador de alteração do perfil na mesma transação. O `operationId` permite reconhecer um envio confirmado antes de uma interrupção de processo. As regras rejeitam uma revisão antiga, substituição de ID/tipo/criação, horário do cliente e gravação sem o marcador atômico.

Se a revisão mudou, a versão remota fica registrada como conflito local. O registro sai da fila executável até revisão explícita: manter a alteração local ou usar a remota. A versão substituída é preservada no histórico local. Não há resolução automática que silenciosamente descarta a edição de outro dispositivo. Transferências usam um único documento com origem e destino; as duas contas devem pertencer ao mesmo UID e ter a mesma moeda. Contas/cartões pendentes são enviados antes dos lançamentos que os referenciam.

## Recebimento e limites de consulta

O primeiro acesso baixa a nuvem antes de capturar preferências padrão do aparelho. Depois, o marcador `latestChangeAt` permite pular consultas das coleções quando nada mudou. Cada coleção usa cursor persistente `updatedAt + documentId`, guardando segundos e nanossegundos do Timestamp para não perder registros com o mesmo milissegundo. A aplicação do lote ocorre antes do avanço do cursor.

As consultas usam páginas de 100 documentos e até três páginas por coleção em cada execução. Trabalhos seguintes continuam do cursor quando necessário. A fila envia até 100 registros por rodada e o WorkManager usa restrição de rede e backoff. Um mutex por UID impede envio simultâneo do worker e da tela. As regras limitam consultas a 300 documentos; a exclusão de conta usa lotes de 200.

`firestore.indexes.json` inclui consultas financeiras por período, categoria, conta, cartão, tipo, status, recorrência e moeda. Campos grandes de notas, mapa `fields` e tags não recebem índices automáticos. A interface financeira consulta índices/FTS e resumos Room; não depende de baixar a nuvem inteira a cada composição de tela.

## Anexos e backups

Comprovantes ficam em `users/{uid}/receipts/{sha256}`, demais anexos em `attachments` e o caminho controlado para imagens de perfil em `profile`. O upload usa caminho por hash, tamanho máximo de 10 MB, MIME permitido e metadados de proprietário/integridade. O documento contém a referência privada e o hash; nenhum Base64 é enviado ao Firestore. O download verifica o UID do caminho e o SHA-256 dos bytes. Não são usados links públicos de download.

O backup JSON preserva IDs, conteúdo e preferências permitidas. Ele baixa e incorpora os anexos remotos antes de exportar um arquivo portátil, remove referências vinculadas ao UID anterior e permite proteção opcional com senha por AES-GCM. A exportação falha com erro real se um anexo não puder ser obtido; não anuncia sucesso de um arquivo incompleto. O tamanho máximo de exportação é 30 MB. O importador limita a entrada a 40 MB, valida o esquema e aplica o lote em uma transação local. Senhas de Firebase, tokens, PIN e chaves de API não fazem parte do backup.

Preferências como `financeHidden`, moeda, início do período financeiro e últimas categorias/conta podem sincronizar. Exibição de valores em notificações/widgets, consentimento de clima, agendamento de backup e configurações de atualização permanecem locais. O backup pode preservar preferências do aparelho; a sincronização não transporta a autorização para expor valores em outro dispositivo.

## Migração e exclusão

A importação do espaço visitante é uma ação explícita disponível após a primeira sincronização. IDs existentes com conteúdo diferente são rejeitados para evitar substituição silenciosa. O visitante fica preservado, e a migração só é marcada concluída depois que a fila e os conflitos foram resolvidos, os IDs conferem com o manifesto por hash e todos têm uma revisão confirmada no servidor. Falhas de rede não apagam a origem.

Excluir normalmente produz tombstones `deletedAt`, que sincronizam e permitem restauração na lixeira. A exclusão definitiva de um registro limpa seu conteúdo sensível e mantém o marcador de exclusão, impedindo reaparecimento em aparelhos antigos. A auditoria local segue as políticas do espaço local e não é apresentada como eliminação total de conta.

Excluir a conta exige reautenticação real. O perfil é bloqueado contra novas gravações, arquivos privados e subcoleções são removidos, fica um marcador mínimo de UID excluído e, então, Firebase Auth exclui o usuário. O cache local dessa conta é removido; o visitante e outras contas não são removidos. Uma falha intermediária informa erro e permite retomar a operação. A política TTL opcional remove o marcador mínimo depois do intervalo de proteção. Essa operação depende de um Storage configurado e acessível, como detalhado em [FIREBASE-SETUP.md](FIREBASE-SETUP.md).

## Validação e limites desta entrega

O repositório inclui testes de integração com os emuladores oficiais de Auth, Firestore e Storage: verificação/recuperação de e-mail, reautenticação, troca de senha/e-mail, exclusão, isolamento A/B, revisões concorrentes, propriedade de contas/cartões, datas, regras recorrentes, patrimônio, preferências e anexos. Use `scripts/test-firebase-security.ps1` para reproduzir a suíte sem credenciais de produção.

O projeto real, provedor Google, e-mails efetivamente entregues, orçamento, métricas/enforcement App Check e atestação Play Integrity no aparelho precisam ser configurados pelo proprietário. O modo sem configuração informa essa condição e preserva o uso local. App Check e regras não representam criptografia de ponta a ponta: os dados remotos estão sujeitos aos controles de acesso e infraestrutura Firebase.
