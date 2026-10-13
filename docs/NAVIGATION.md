# Navegação 2.7 Android / 4.2 Windows

[Auditoria e mapas anteriores](NAVIGATION-AUDIT.md). IDs legados permanecem válidos;
`note` no Android direciona para Notas. Nenhum banco de registros foi migrado.

| Área | Destinos reais |
|---|---|
| Principal | Hoje/Meu dia, visão geral, Inbox quando presente, Explorar |
| Produtividade | Tarefas, agenda/calendário, projetos, metas, notas, hábitos, foco, rotina |
| Finanças | Centro financeiro e módulos financeiros existentes |
| Organização | Favoritos de registros, histórico/Recentes, modelos e tipos documentais |
| Inteligência | Assistente existente, automações e revisão da semana quando presentes |
| Vida pessoal | Grupos reais de saúde, casa, estudos, viagens, lazer e ferramentas |
| Sistema | Configurações, personalização, notificações, sincronização, diagnóstico, backup e privacidade conforme a plataforma |

Não há novos serviços de IA, arquivos na nuvem ou rotas de funcionalidades inexistentes.

## Windows

- Definições de destinos em `shared/navigation.ts`; catálogo de 81 tipos e páginas
  existentes pesquisáveis. API de navegação valida contra esse mesmo registro.
- Sidebar com grupos recolhíveis, módulos da área em subgrupos, favoritos de páginas,
  recentes e mais utilizados. Ícones, indicador ativo, nomes acessíveis e tooltips.
- Menu compacto manual e automático em janelas de até 1000px; rolagem independente.
- Breadcrumb com área e destino, ação Fixar página e atalhos contextuais.
- Ctrl/Cmd+K pesquisa páginas, módulos e registros. Setas, Enter e Esc na palette;
  controles nativos permitem Tab/Enter na sidebar. Atalhos anteriores preservados.
- Personalizar menus: página inicial, favoritos, visibilidade de atalhos e ordem
  dentro das áreas. Explorar, Configurações e Personalizar menus permanecem acessíveis.
- Listas genéricas com nome igual ao editor principal recebem o sufixo “registros”,
  distinguindo os caminhos sem excluir telas ou mudar seus IDs.

## Android

- Quatro atalhos configuráveis mais Mais; por padrão Hoje, Agenda, Finanças, Notas.
  Em largura de pelo menos 600dp, usa NavigationRail; celular mantém barra inferior.
- Mais reúne favoritos de páginas, recentes, mais utilizados e grupos recolhíveis.
  Perfis existentes continuam orientando os atalhos; Catálogo completo e pesquisa
  encontram também módulos fora do perfil ou ocultos.
- Pesquisa global oferece destinos além dos registros. Busca de módulos ignora
  acentos; breadcrumbs indicam a área e o módulo secundário.
- Personalização permite escolher os atalhos principais, ordenar, favoritar,
  ocultar/reexibir, escolher início e restaurar padrões.
- Configurações em Conta, Personalização, Dados, Segurança e Avançado; importação,
  exportação, atualização, notificações e controles de segurança preservados.

## Financeiro

Android: Dia a dia / Contas / Planejamento / Análise / Administração, mantendo as 21
seções anteriores. Windows: Dia a dia / Contas / Planejamento / Análise, mantendo as
dez abas anteriores. A seleção vigente indica sua família e ações básicas continuam
diretas. Editores, filtros, cálculos, privacidade e contratos de dados permanecem.

## Persistência e sincronização

Organização por plataforma usa o mecanismo de preferências da conta existente:
`desktopNavigation` contém a configuração declarativa do Windows; `navHome`,
`navBottom`, `navOrder` e `navCollapsed` descrevem o Android. Novas preferências são
incluídas no modelo de sincronização e nos backups; o Windows mantém chaves Android
e o Android mantém a configuração Windows ao consolidar preferências.

Recentes e contagens de uso permanecem locais (`navigationHistory` no Windows;
`navRecent`/`navUsage` no Android). Não há envio desse histórico para Firebase.
Sem login, a organização continua funcionando localmente. O envio remoto reutiliza
a fila e a resolução de conflitos existentes; nova sincronização de menus entre dois
aparelhos ainda deve ser conferida em conta real. Nenhuma regra remota foi modificada.

Temas existentes e preferências de redução de movimento são respeitados; a nova UI
nativa Android não acrescenta animações próprias. O Windows usa transições curtas
desativadas com prefers-reduced-motion. Layouts específicos de módulos continuam
evoluindo; esta entrega centraliza navegação e organiza financeiro/configurações.

## Arquivos de implementação

| Plataforma | Arquivos principais |
|---|---|
| Núcleo Android | `core/model/.../NavigationPolicy.kt`, `core/data/.../WorkspaceStore.kt` |
| Interface Android | `AppNavigation.kt`, `WorkspaceUi.kt`, `OrganizerStudio.kt`, `SettingsScreen.kt`, `FinanceStudio.kt`, `VeyraViewModel.kt` |
| Núcleo Windows | `shared/navigation.ts`, `shared/commands.ts`, `shared/settings.ts`, `shared/model.ts`, `electron/index.ts` |
| Interface Windows | `src/Navigation.tsx`, `src/navigation.css`, `src/main.tsx`, `src/CommandCenter.tsx`, `src/Settings.tsx`, `src/FinanceView.tsx` |
| Validação | `NavigationPolicyTest.kt`, `NavigationUiTest.kt`, `FinanceUiTest.kt`, `StudioFlowTest.kt`, `tests/navigation.test.ts`, `tests/navigation-ui.ts`, `tests/packaged-smoke.ts` |
| Versões e operação | `app/build.gradle.kts`, `apps/desktop/package.json` e lockfile; guias em `docs/`; handoff e painel Omega |
