# Arquitetura do ecossistema

## Decisão de stack

A base existente é Kotlin/Jetpack Compose, Room, WorkManager e Firebase Android; não contém um frontend web reutilizável. Os módulos Android, seus bancos, IDs e catálogo foram mantidos.

| Opção | Adequação à base atual | Decisão |
| --- | --- | --- |
| Tauri | Binário potencialmente menor; exigiria frontend novo, Rust, toolchain Windows e integração própria dos serviços | Considerado, sem vantagem de reutilização web neste repositório |
| Electron | Frontend novo, mas integração direta de tray, shortcuts, múltiplas janelas, DPAPI e instalador; runtime Chromium embarcado | Escolhido para a primeira versão desktop; custo maior de distribuição/RAM reconhecido |
| Flutter Desktop | Exigiria outra linguagem e reescrita da apresentação; não reutiliza Compose diretamente | Sem benefício suficiente para esta base |

Veja os [pré-requisitos Tauri](https://tauri.app/start/prerequisites/), as [recomendações de segurança Electron](https://www.electronjs.org/docs/latest/tutorial/security) e a [proteção safeStorage](https://www.electronjs.org/docs/latest/api/safe-storage).

## Fronteiras

`apps/desktop/shared` contém os tipos compatíveis, operações financeiras e produtividade. O catálogo de formulários é extraído dos `ItemSpec` Kotlin a cada build. `src` contém a apresentação React e o Veyra Design System. `electron` contém armazenamento, autenticação, sincronização e integrações Windows. O preload só expõe operações enumeradas; não expõe filesystem, SQL, tokens, `require` ou acesso Node ao renderer.

As janelas usam sandbox, isolamento de contexto, Node desabilitado, permissões negadas, protocolo local e CSP sem acesso de rede. A IPC verifica origem, frame principal, operação permitida e UID do snapshot. Ações que aguardam diálogos revalidam o workspace. Saves de editores usam também o UID original. O navegador Google é externo, com retorno por localhost, nonce aleatório e token de uso único.

## Persistência e protocolo

A entidade preserva `id`, `type`, `title`, `notes`, `date`, `done`, `favorite`, `tags`, `parentId`, `fields`, `createdAt` e `deletedAt`. Valores desconhecidos em `fields` sobrevivem à edição. Nenhuma migração destrutiva é aplicada ao banco Android.

Desktop: SQLite/sql.js com tabelas de itens, outbox, conflitos, auditoria e metadados. Todo o arquivo é protegido por AES-256-GCM; a chave aleatória é protegida pelo DPAPI. Cada transação exporta e confirma o arquivo criptografado antes de informar sucesso. A gravação em disco é atômica; uma falha reverte também a transação em memória. Arquivos são separados pelo hash do UID e há um workspace visitante independente.

Cloud: os caminhos continuam `users/{uid}/{collection}/{sha256(item.id)}`. As mesmas coleções Android recebem envelope com `ownerUid`, `revision`, `operationId`, `updatedAt` do servidor, valores financeiros tipados e hashes das referências. Anexos binários são removidos do envelope; permanecem locais. As [chamadas REST do Firestore usam o ID token Firebase e respeitam Security Rules](https://firebase.google.com/docs/firestore/use-rest-api).

Cada save atualiza item, auditoria e operação pendente na mesma transação local. Edições sucessivas coalescem preservando a revisão base. Push usa leitura autenticada e commit atômico CAS com precondição de existência/updateTime (sem BeginTransaction, que recusa tokens de usuário no Firebase real) e envia contas/cartões antes dos dependentes; uma revisão divergente gera conflito. Um ACK de uma operação antiga não remove uma edição posterior. `operationId` torna a retomada idempotente. Os pulls são paginados por timestamp de servidor e ID, preservando nanossegundos e cursors por coleção. O marcador `latestChangeAt` do perfil é atualizado atomicamente com cada documento, conforme as regras existentes.

Desktop consulta o marcador a cada 30 segundos; Android observa um único documento de perfil e realiza pull incremental com debounce. Nenhum listener de todas as coleções foi adicionado. Backoff e lote limitado protegem a conectividade; o workspace continua útil sem rede.

## Contas e compartilhamento futuro

O Firebase UID define a fronteira física e remota. Sair preserva a fila e o cache da conta e abre o visitante. As regras rejeitam acesso cruzado e campos de privilégio. Dispositivos são registros privados de presença diária; não equivalem a tokens de sessão revogáveis.

Compartilhamento futuro deve usar uma coleção de espaços e membros com papéis e autorização explícita no servidor. Não deve reutilizar `parentId`, copiar dados privados automaticamente nem tratar o e-mail como autorização. Nenhum caminho compartilhado foi liberado nesta versão.

## Atualização e release

Desktop e Android possuem canais separados. O atualizador desktop aceita apenas tags estáveis `desktop-v*`, URL do repositório fixado, versão maior, manifesto Ed25519 e hash do binário. A chave privada fica fora do Git. O script de release verifica o commit publicado e os hashes dos uploads antes de tornar a release pública. A release desktop usa `make_latest=false` para preservar o atualizador APK existente.

## Limites técnicos

O cache criptografado de sql.js é exportado inteiro por transação; os testes desta versão validam recuperação e integridade, não garantem latência constante com dezenas de milhares de anexos. Busca e movimentos financeiros são paginados; listas de widgets são limitadas e o snapshot preserva texto completo para evitar truncar notas ao concluir/favoritar um registro. Escalabilidade maior pode exigir SQLite nativo criptografado e assinatura comercial Windows, sem alterar o protocolo remoto.

