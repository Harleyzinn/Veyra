---
id: "0b4b635e-33f3-485c-90e6-ece8a9878905"
title: "Veyra Finanças 3.0 — Android e PWA"
memory_type: "episodic"
verification: "test_result"
created_at: "2026-10-19T22:28:19.0915689+00:00"
updated_at: "2026-10-19T22:28:19.0915689+00:00"
sources: ["repo:docs/FINANCE-3.0.md", "repo:docs/VALIDATION-3.0.0.md", "repo:apps/finance/domain.ts", "repo:app/src/main/kotlin/app/veyra/android/FinancePortal.kt"]
---

# Continuidade

Reformulação solicitada para finanças pessoais, Android e web-app instalável no iPhone. Interface React/PWA em apps/finance reaproveita o domínio TypeScript anterior. Android carrega os mesmos assets locais com bridge validado, revisão otimista e transação no Room original; bloqueio financeiro, login e acesso legado preservados. Mesma assinatura/pacote, versão 3.0.0 (300).

Saldo confirmado, previsão separada, indicador de gasto explicável, salário por dia útil, feriados manuais configuráveis, faturas/parcelas/transferências, metas, orçamentos, relatórios e 24 temas. IndexedDB e cache offline na web. Backup/importação real com prévia. Receitas automáticas/alertas novos ao abrir, não serviços em segundo plano. Novas preferências por UID no banco/backup, excluídas da sincronização porque as regras atuais não autorizam financePortal. Não alterar regras remotas por inferência.

36 testes JavaScript aprovados; relatórios do núcleo Android totalizam 158 sem falhas (partes não afetadas em cache); seis testes instrumentados aprovados, incluindo Room/revisão/recriação, privacidade e regressões legadas. Edge e WebKit passaram 16 verificações cada, incluindo offline e duas abas. Lint zero erros/12 avisos. Release assinada instalada no emulador, versão conferida. Capturas Android ficaram pretas inclusive no launcher, portanto pixels nativos precisam de validação em outro ambiente/aparelho; árvore de acessibilidade confirmou a interface de produção. Sem iPhone físico validado.

APK e código prontos; publicação externa autorizada diretamente nesta sessão. Pages habilitado em modo workflow; push, execução do deploy e release ainda precisam de confirmação no registro de publicação. Não confundir preparação com publicação.

# Lições e próximos passos

Preservar a chave original inclusive nos APKs de teste evita reinstalar e perder dados de QA. Em Android release, esperar carga do WebView e conferir a hierarquia além da captura. Não enviar chave nova de preferências para o Firestore sem compatibilizar seu whitelist: isso repetiria o erro de acesso negado. Classificar salary_schedule como financeiro em todas as proteções, mesmo quando seu bucket de transporte é workspace.

Próximo: conferir publicação, validar em Android físico/iPhone, avaliar sincronização de preferências e PWA com contrato próprio e sem enfraquecer autenticação. Omega permanece local/textual; sem agentes executores ou busca semântica operacional.
