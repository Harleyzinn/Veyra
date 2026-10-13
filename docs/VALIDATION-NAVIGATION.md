# Validação da navegação — 13/10/2026

Entrega local: Android 2.7.0 (270) e Windows 4.2.0. Sem publicação externa ou mudança
de regras Firebase. [Arquitetura, uso e arquivos](NAVIGATION.md) · [Auditoria](NAVIGATION-AUDIT.md).

## Executado

- **157 testes de núcleo Android aprovados:** 33 de modelo, 115 financeiros e 9 de
  nuvem. Cobertura de ordem/IDs obsoletos, histórico limitado, pesquisa sem acentos e
  preservação das 21 seções financeiras.
- **57 testes Windows aprovados**, incluindo persistência, sincronização local,
  validações financeiras, segurança do callback Google em localhost e novos testes
  do catálogo, personalização, histórico e famílias financeiras.
- TypeScript e ESLint sem erros. Builds Windows e instalador NSIS concluídos.
- **106 destinos Windows abertos pela API real**, aguardando conteúdo renderizado
  e sem fallback de erro. Dez seções financeiras abertas. Ctrl+K/Enter acessou módulo
  oculto; menu compacto e teclado funcionaram. Reordenação e restauração pela interface
  verificadas; preferências persistiram após recarga e entraram no registro da conta.
  Nota existente manteve corpo completo e favorito.
- Temas claro/escuro, janela tablet e preferência de movimento reduzido exercitados
  no Windows; capturas inspecionadas.
- Executável Windows empacotado abriu em perfil novo: versão 4.2.0, configuração
  Firebase embarcada e isolamento do renderer conferidos. O teste revelou identificação
  antiga 4.1.0, corrigida para derivar a versão do pacote.
- **22 testes de interface Android distintos aprovados**, após repetições direcionadas:
  navegação/personalização, tablet, finanças, pagamentos, simulador, privacidade,
  relatórios e gráficos, tarefas, calendário/Kanban, notas e clima. Só o emulador
  dedicado Veyra_QA foi usado.
- Android: busca encontrou Relógio mundial oculto mesmo em perfil Essencial; seleção
  de favorito e página inicial persistiram entre atividades. Registro com corpo completo
  preservado; preferências incluídas no backup. Organização sincronizável, histórico e
  contagens excluídos do conjunto de sincronização.
- Tablet Android em 1280×800/densidade 160 usou rail e tema claro; tamanho e densidade
  restaurados ao final. Celular em tema escuro inspecionado.
- Compilações Android debug/release e R8 concluídas. Lint: zero erros e 23 avisos
  existentes. APK final instalado como atualização no QA, versão 270/2.7.0 confirmada,
  processo aberto e sem erro AndroidRuntime no log de inicialização.
- Omega: estado, prioridades, handoff e lições persistidos; 36 notas indexadas sem
  erros. Backup `20261013T155913-383d012f` restaurado em diretório separado com 47
  arquivos verificados e SQLite íntegro; dashboard e baseline atualizados.

## Divergências dos testes corrigidas

A primeira regressão Android teve 20 aprovações e duas falhas. O teste de busca usava
Voltar sem verificar teclado e selecionava o campo com o mesmo texto do resultado;
foi corrigido com foco/ocultação de teclado e consulta sem acento. O simulador comparava
o backup incluindo histórico de páginas, agora mutável por navegação; a comparação
passou a excluir apenas navRecent/navUsage, mantendo registros e demais preferências.
Um deslocamento rápido tornava o clique de privacidade instável; a repetição aguardou
rolagem estável e confirmou o estado no banco. Ambos os cenários passaram individualmente.
Nenhuma regra financeira foi alterada para acomodar os testes.

## Artefatos

- APK: `dist/Veyra-2.7.0.apk`, Android 8+, pacote `app.veyra.life`.
  SHA-256: `7c55f715d6038d6b8ec103d64ac0a28ced6344cc316ca5bf033b76434a62ac61`.
- Certificado original: `8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21`.
- Windows: `apps/desktop/release/VeyraLife-Setup-4.2.0.exe`.
  SHA-256: `0d11853eb05f68fb02250ca36ea89a5f2c9b0fdbd6f6fa08033557599d618968`.

[Windows escuro](screenshots/navigation-desktop-dark.png) ·
[Windows claro](screenshots/navigation-desktop-light.png) ·
[Windows compacto](screenshots/navigation-desktop-tablet.png) ·
[Android celular](screenshots/navigation-android-phone.png) ·
[Android tablet](screenshots/navigation-android-tablet.png).

Reprodução: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`,
`npx tsx tests/navigation-ui.ts`, `npx tsx tests/packaged-smoke.ts` em apps/desktop.
Android: tarefas Gradle core:model:test, feature:finance:test, core:cloud:testDebugUnitTest,
app:lintDebug, assembleDebug/assembleRelease e testes instrumentados das classes
NavigationUiTest, FinanceUiTest e StudioFlowTest em perfil de QA visitante.

## Limites

Novos menus entram na fila de sincronização existente; transporte remoto entre dois
aparelhos com conta real não foi exercitado nesta rodada. Sem novo login Google físico,
certificação Play Protect/Play Integrity ou validação macOS/iOS. O instalador Windows
continua sem certificado Authenticode comercial; foi testado o executável empacotado,
sem executar o assistente de instalação na máquina pessoal. Estado interno de todos
os filtros de cada módulo entre navegações não é garantido; editores e persistência
existentes foram preservados. Omega continua textual/local, sem novas integrações.
