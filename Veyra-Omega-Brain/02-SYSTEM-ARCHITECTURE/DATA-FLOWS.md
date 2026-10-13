---
id: "e673ec69-78da-5e63-bbab-37f5d112ee79"
title: "Data Flows"
memory_type: "semantic"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py"]
---

# Fluxos

```mermaid
flowchart LR
 H[Humano / Obsidian] --> V[Vault Markdown]
 V --> I[Indexador local]
 I --> S[SQLite / FTS5]
 S --> C[Contexto com referências]
 S --> D[Dashboard gerado]
 V --> B[Backup versionado]
 S --> B
 G[GitHub / código] -. fases seguintes .-> I
```

Nada é enviado ao Firebase ou a um provedor de IA pelo Omega na fase 1. SQLite não substitui Markdown. Conteúdo recuperado não é executado.
