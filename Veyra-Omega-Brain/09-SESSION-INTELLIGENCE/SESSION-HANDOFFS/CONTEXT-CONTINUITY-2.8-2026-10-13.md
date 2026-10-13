---
id: "9209158e-f4b3-50cb-95d0-7186f98f39a7"
created_at: "2026-10-13T18:55:53+00:00"
title: "Context Continuity 2.8 2026-10-13"
memory_type: "episodic"
verification: "test_result"
updated_at: "2026-10-13T18:55:53+00:00"
sources: ["repo:docs/VALIDATION-2.8.0.md", "repo:docs/RELEASE-2.8.0.md", "repo:app/src/main/kotlin/app/veyra/android/WorkspaceUi.kt", "repo:app/src/androidTest/kotlin/app/veyra/android/NavigationUiTest.kt"]
---

# Continuar de onde estava

Memory consultada e comparada ao código. SaveableStateHolder por espaço/destino preserva estado nativo sem manter todas as páginas executando. Tarefas e notas: campos/filtros/ordem com rememberSaveable. Finanças: seção/mês/horizonte/filtros, YearMonth com Saver ISO. Voltar segue visitas reais (máximo 20; cliques consecutivos não duplicam). Notas vazias por filtro têm mensagem e Limpar filtros; personalização não mostra lixeira irrelevante.

158 testes de núcleo, seis testes Android distintos aprovados. Novo fluxo verificou draft de tarefa não gravado, filtro Amanhã, query/favoritas de notas, Limpar filtros, Simulador/mês seguinte, retornos com Voltar e ActivityScenario.recreate. Cinco regressões passaram na primeira execução; novo teste só passou após tratamento nativo de teclado, que encobria o filtro. Captura e logs reais documentados em docs/VALIDATION-2.8.0.md.

APK 280/2.8.0 instalado em QA, assinatura original, hash registrado. Sem publicação externa. Windows, esquemas, Firebase remoto e domínio financeiro sem mudanças. Não prometer rascunhos após encerramento ou persistência nova nos editores financeiros em modal.

Lição: buscar pacote de IME via UiAutomator não identifica sempre teclado visível, especialmente com foco recém-aberto. Capturar tela/árvore em falha; ocultar pelo InputMethodManager na atividade de teste e depois testar Voltar de navegação real. Não atribuir falha de localização a perda de estado sem observar a tela.
