# Android 2.8.0 — continuidade ao navegar

- Tarefas mantêm filtro, prioridade, ordenação e rascunho de captura quando o usuário
  sai da página e retorna durante a atividade.
- Notas mantêm pesquisa, pasta, favoritas/fixadas, ordenação e visualização do arquivo.
- Finanças mantêm seção, mês, horizonte e filtros. Estado de apresentação separado
  por espaço de usuário; históricos temporários não são enviados ao Firebase.
- Voltar percorre as páginas realmente visitadas, com histórico limitado a 20 entradas.
  A busca global continua consumindo Voltar antes de sair da página.
- Notas com filtros sem resultado oferecem Limpar filtros; a mensagem distingue
  ausência de resultados de um bloco de notas vazio.
- Personalização de menus deixa de mostrar o atalho irrelevante de lixeira.

Estado transitório utiliza o mecanismo nativo de estado salvo do Compose; não mantém
todas as telas executando em segundo plano. Isso não é um backup de rascunhos após
encerrar/forçar parada do aplicativo. Editores financeiros em modal não receberam
persistência nova nesta rodada. Windows permanece 4.2.0, sem código alterado.

Entrega local, sem publicação externa. [Validação](VALIDATION-2.8.0.md).
