---
id: "75618f37-7e2f-569d-aa1f-e53b69b46672"
title: "API Contracts"
memory_type: "semantic"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py"]
---

# Interface implementada

CLI imprime JSON e retorna código 0 em sucesso/1 em falha. Comandos init, index, search, store, context, dashboard, health, backup, restore. Search limita resultados a 1..50 e aceita filtro de classe. Store exige frontmatter válido; atualização exige SHA-256 atual. Restore só escreve em destino novo dentro de .omega.

Não existe API HTTP/MCP conectada. Contratos memory.consolidate, tasks.*, sessions.*, github.sync, decisões e vetores são planejamento, não endpoints disponíveis.
