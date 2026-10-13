---
id: "55aeb6a9-b1a1-53b6-96fb-8685a4b710eb"
created_at: "2026-10-13T01:13:22+00:00"
title: "Test Clock Dependence"
memory_type: "failure"
verification: "test_result"
updated_at: "2026-10-13T01:13:22+00:00"
sources: ["repo:feature/finance/src/test/kotlin/app/veyra/feature/finance/FinanceReportsTest.kt", "repo:feature/finance/src/test/kotlin/app/veyra/feature/finance/FinancialAnalyticsTest.kt", "repo:docs/VALIDATION-2.5.0.md"]
---

# Relógio real misturado com fixtures fixos

FinanceReportsTest e FinancialAnalyticsTest passaram em 08/10 e falharam em 12/10. O cenário usava today fixo, mas select/pause consultavam LocalDate.now como parâmetro padrão. O suposto futuro de 09/10 virou passado; a pausa de 12/10 mantinha cobrança de 10/10.

Solução: passar today explicitamente aos dois pontos temporais. 115 testes financeiros passaram. Nenhuma mudança no domínio para acomodar expectativas erradas. Antes de repetir testes de data, conferir referência temporal de todas as operações, não apenas a fixture.
