---
id: "a39a16aa-a9db-54bc-a4b5-2a1d64aa34be"
title: "Architecture Overview"
memory_type: "semantic"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py", "user:omega-master-prompt:2026-10-12"]
---

# Arquitetura em sete camadas

| Camada | Fase 1 | Evolução |
|---|---|---|
| Knowledge Core | Vault Markdown/YAML | Bases e mapas |
| Memory Engine | Classes, IDs, fontes, SQLite | Consolidação hierárquica |
| Intelligence | FTS5 e contexto limitado | Embeddings/contradições |
| Orchestration | Papéis documentados | Etapas autorizadas |
| Execution | CLI local | Ponte autenticada |
| Observability | Auditoria e métricas locais | Monitoramento incremental |
| Improvement | Templates de lições | Recomendações com evidências |

Vault é fonte humana; SQLite/FTS são derivados locais. GitHub permanece fonte do código. Runtime .omega fica fora do Vault e ignorado pelo Git. Sem servidor FastAPI, vetores ou processos de agentes nesta fase.

[[COMPONENT-MAP]] · [[SECURITY-MODEL]] · [[MASTER-ROADMAP]].
