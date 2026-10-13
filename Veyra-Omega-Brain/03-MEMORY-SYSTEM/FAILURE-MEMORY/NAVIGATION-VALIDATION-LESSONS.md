---
id: "7b7c73e7-568e-531c-babe-bf5486237c0a"
created_at: "2026-10-13T15:59:12+00:00"
title: "Navigation Validation Lessons"
memory_type: "failure"
verification: "test_result"
updated_at: "2026-10-13T15:59:12+00:00"
sources: ["repo:docs/VALIDATION-NAVIGATION.md", "repo:apps/desktop/tests/packaged-smoke.ts", "repo:app/src/androidTest/kotlin/app/veyra/android/FinanceUiTest.kt", "repo:app/src/androidTest/kotlin/app/veyra/android/NavigationUiTest.kt"]
---

# Lições verificadas

1. Windows empacotado informava versão antiga por constante em electron/index.ts. Derivar de app.getVersion e testar contra package.json. Instalador recompilado e smoke aprovado.
2. UiAutomator: Voltar pode sair da busca se teclado não estiver aberto. Focar campo e ocultar apenas quando IME presente. Consulta idêntica ao título selecionava o próprio campo; usar consulta parcial e verificar destino aberto.
3. Histórico local de páginas agora muda por navegação. No teste do simulador, excluir somente navRecent/navUsage da comparação do backup; manter registros e todas as outras preferências.
4. Deslocamentos rápidos antes de clicar em privacidade tornavam o toque instável. Usar busca de elemento com rolagem lenta, estabilização e confirmar financeHidden no banco. Repetição passou sem mudança no domínio.

A regressão inicial teve duas falhas de 22 testes. Ambos os casos passaram individualmente após essas correções; não alterar regras financeiras para satisfazer fixtures. Evidências, hashes e limites em docs/VALIDATION-NAVIGATION.md.
