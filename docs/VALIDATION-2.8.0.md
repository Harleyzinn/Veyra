# Android 2.8.0 — 13/10/2026

- **158 testes de núcleo aprovados:** 34 de modelo, 115 financeiros e 9 de nuvem.
  Novo teste verifica limite de 20 visitas, destinos inválidos e cliques repetidos.
- **Seis testes de interface distintos aprovados:** contexto entre páginas/recriação
  de atividade, descoberta/personalização, tablet, captura de tarefa, gasto exato e
  arquivar/restaurar nota completa. O teste novo passou após ocultar o teclado pelo
  mecanismo nativo; a detecção por pacote no UiAutomator não enxergava o teclado que
  cobria os filtros. A primeira execução dos seis teve cinco aprovações e uma falha
  de localização de elemento; repetição direcionada aprovada.
- Contexto real conferido: tarefa ainda não salva conserva texto e filtro Amanhã;
  Voltar retorna à página anterior; consulta/favoritas de notas persistem e Limpar
  filtros restaura a lista; financeiro conserva Simulador e mês seguinte após sair,
  retornar e ActivityScenario.recreate(). Registro original preservado e rascunho
  inexistente no banco, evitando confundir estado de formulário com salvamento.
- Compilações debug/release, R8 e assinatura concluídas. Lint: zero erros e 23 avisos
  existentes. Nenhuma dependência, esquema de banco ou regra Firebase alterada.
- APK final instalado como atualização em Veyra_QA, versão 280/2.8.0 confirmada,
  processo aberto e sem erro AndroidRuntime no log de inicialização.
- [Captura do rascunho recuperado](screenshots/context-2.8.png) inspecionada. Fixtures
  são exclusivas do emulador e não acompanham o APK.
- Omega: estado, prioridades e handoff atualizados; 37 notas indexadas sem erros.
  Backup `20261013T185555-89aa2d92` restaurado em diretório separado, com 48 arquivos
  conferidos e SQLite íntegro. Dashboard e baseline atualizados.

APK: `dist/Veyra-2.8.0.apk`, pacote `app.veyra.life`, Android 8+.

SHA-256: `d1d30abf7b7f124da875ab9eb13fd3c52d6d9303ab55b93b513f7b34924cb5f9`.

Certificado original: `8595f5acf3676e236dd32198a08e71bf5d04d875697d395f5bccd6b7a4498f21`.

Estado de interface separado pela mesma identidade física do banco e da proteção
financeira; SaveableStateHolder mantém somente estado salvo, sem executar todas as
telas em segundo plano. Filtros, ordem e campos simples usam rememberSaveable;
YearMonth usa representação ISO com Saver explícito.

Limites: retenção de contexto durante atividade e sua recriação, sem promessa de
rascunhos após força de parada/encerramento. Editores financeiros em modal e todos
os módulos secundários não receberam preservação nova. Login/switch entre contas
reais e transporte remoto não foram exercitados. Emulador não certifica Play Protect
ou Play Integrity. Windows 4.2.0 sem alterações, testes Windows não repetidos.
Entrega local, sem publicação externa.
