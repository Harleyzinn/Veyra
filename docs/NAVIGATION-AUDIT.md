# Navegação Veyra — auditoria de 13/10/2026

Fonte: WorkspaceUi.kt, OrganizerStudio.kt, Registry.kt, FinanceStudio.kt;
apps/desktop/shared/commands.ts, src/main.tsx, CommandCenter.tsx, FinanceView.tsx e Settings.tsx.
Inventário do código local em 5bce502, antes de modificar os componentes.

## Mapa atual

- Android: Hoje / Agenda / Finanças / Notas / Mais → grupos do Registry → módulo.
  Configurações e pesquisa são ações da barra superior. Favoritos de registros e linha
  do tempo ficam no hub. Rotina tem ação própria. Catálogo mistura registros e ferramentas.
- Windows: 19 destinos de commands.ts em lista lateral contínua (HOME, PLANEJAR,
  ORGANIZAR, FINANÇAS, ACESSO RÁPIDO, SISTEMA); configurações no rodapé.
  Sincronização pelo indicador superior; diagnóstico/backup/privacidade por caminhos
  secundários. Explorar contém os tipos do catálogo. Ctrl/Cmd+K já pesquisa registros
  e alguns comandos. As páginas principais já são carregadas sob demanda.
- Financeiro Android: quatro chips e seletor de 21 seções. Windows: dez abas,
  incluindo funções básicas e avançadas.

## Problemas verificados

1. Perfil e hidden no Android filtram o único catálogo; recursos podem desaparecer
   da descoberta. Favoritos de módulos alteram somente a ordem dentro do grupo.
2. Windows duplica rótulos/ícones entre navigation e commands; fallback do breadcrumb
   chama destinos de sistema de Central. Algumas rotas não estão na pesquisa de páginas.
3. Favoritos/recentes de registros não representam favoritos/recentes de páginas.
4. Sidebar longa sem modo compacto, recolhimento de grupos ou personalização.
5. O seletor financeiro longo não comunica famílias funcionais. Pouca indicação da
   localização no Android ao entrar por Mais.

## Arquitetura proposta

Principal → visão geral, meu dia, inbox/explorar.
Produtividade → tarefas, calendário, projetos, metas, notas, hábitos/foco/rotina.
Finanças → centro financeiro e famílias internas: dia a dia, contas, planejamento,
análise, administração. Organização → tipos documentais, histórico/modelos quando presentes.
Inteligência → assistente/automações/revisão já existentes, sem criar serviços de IA.
Vida pessoal → demais grupos reais (saúde, casa, estudos, lazer, viagens).
Sistema → configurações, sincronização, diagnóstico/backup/privacidade e personalização.

Definições declarativas por plataforma preservam IDs. Sidebar desktop: grupos com
estado persistente, modo compacto, favoritos, histórico local de destinos e catálogo
completo. Mobile: barra inferior e Mais com grupos e personalização; tablet usa rail.
Pesquisa de páginas não depende de atalhos ocultos. Ocultar significa retirar atalhos,
nunca apagar o módulo ou seus dados. Preferências de apresentação usam o armazenamento
existente; contagens locais não são sincronizadas como conteúdo pessoal.

## Caminhos e critérios

Antes: principais Android 1 toque, secundários 2 (Mais → módulo), ocultos inacessíveis
por catálogo; Windows principais 1 clique, tipos 2 (Explorar → tipo).
Depois: favoritos desktop 1 clique; demais grupos até 2 (expandir → destino); busca
universal atalho + selecionar. Android secundários Mais → grupo → módulo (até 3),
favoritos Mais → módulo (2); catálogo completo pesquisável mesmo com atalhos ocultos.

Aceitação: cobertura de todos os IDs existentes, atalhos ocultos recuperáveis por busca,
preferências persistentes e isoladas por conta, teclado, modo compacto/tablet, temas,
preservação de registros e controles financeiros. Nada é publicado nesta etapa.
