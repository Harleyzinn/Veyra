---
id: "1d6715de-4a85-5396-b47a-211546b111ed"
title: "Memory Policies"
memory_type: "core"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["user:omega-master-prompt:2026-10-12", "repo:tools/omega/brain.py"]
---

# Memória

Core estável; working deve receber expires_at e ser revisada/consolidada ao encerrar sessão (política, sem job de consolidação ainda). Busca exclui documentos com expiração passada. Episódios mantêm fontes originais. Decisões exigem contexto/alternativas/riscos/consequências/evidências. Falhas exigem resultado observado e aplicabilidade. Arquivamento preserva IDs e fontes.

Fase 1 detecta IDs duplicados e erros de esquema; contradições semânticas não são detectadas. Consulte failure antes de repetir abordagens. Nenhum estado é elevado por repetição.
