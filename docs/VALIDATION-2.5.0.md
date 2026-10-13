# Android 2.5.0 e continuidade Omega — 12/10/2026

Entrega local; última release pública continua 2.4.0. Nenhuma publicação foi executada.

- **151 testes unitários aprovados:** 27 de modelo, 115 financeiros e 9 de nuvem.
- **12 testes Omega aprovados**, incluindo exclusão de contexto/dashboard derivados
  para que resumos não sejam recuperados como evidência de si mesmos.
- **Quatro fluxos Android aprovados** no emulador dedicado Veyra_QA: tarefa para hoje
  e amanhã com prioridade, callback recusado em salvamento inválido, fixar/desafixar e
  favoritar com corpo completo preservado, captura de gasto exato como regressão.
- Compilação debug/release e R8 concluídos. Lint: zero erros, 23 avisos existentes e
  uma informação. Não houve dependência nova ou mudança de esquema Firebase/SQLite do app.
- Testes financeiros inicialmente revelaram dois fixtures dependentes do relógio real;
  corrigidos com data explícita. Domínio financeiro não foi alterado nesta rodada.
- Captura de tarefas repetida após acrescentar verificação de limpeza dos dois rascunhos:
  um teste direcionado passou; captura final registra o estado estável.
- APK release instalado sobre o app existente no emulador, versão 250/2.5.0 confirmada,
  processo aberto e nenhum erro fatal no log de inicialização.
- Handoff, bug corrigido, lição de relógio e prioridades persistidos no Omega: 33 notas
  indexadas sem erros. Backup `20261013T011323-03173c66` restaurado com 44 arquivos
  conferidos e SQLite íntegro, sem substituir o Vault ativo.

APK local: `dist/Veyra-2.5.0.apk`, pacote `app.veyra.life`, código 250, Android 8+.

SHA-256: `e6604ace5c5921928fb9e632b542f4a8efa88e3da4130eafa2d2996815360920`.

Assinatura original: `8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21`.

[Tarefas](screenshots/tasks-2.5.png) · [Notas](screenshots/notes-2.5.png) ·
[Auditoria e pendências](AUDIT-2026-10-12.md).

Limites: QA em emulador; não certifica Play Protect/Play Integrity, login Google físico
ou todos os módulos do produto. Desktop não recebeu código novo nesta rodada. Memória
Omega é textual; embeddings, consolidação semântica, GitHub incremental e executores
de agentes continuam pendentes. Abertura do Vault na interface Obsidian permanece manual.
