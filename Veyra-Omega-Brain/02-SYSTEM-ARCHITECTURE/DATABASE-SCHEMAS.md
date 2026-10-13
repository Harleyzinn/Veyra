---
id: "feec3ada-c2ca-5911-a64b-fbdee2512bc5"
title: "Database Schemas"
memory_type: "semantic"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py"]
---

# Metadados v1

- documents: ID único, caminho, classe, verificação, timestamps, expiração, fontes, hash e corpo.
- search: FTS5 de título e corpo.
- links: vínculos Obsidian RELATED_TO; resolução tipada/impacto aguardam fases posteriores.
- events: UUID, timestamp, origem, versão, tipo, dedupe, estado, payload. Eventos de indexação processados de forma idempotente; não há dispatcher externo.
- operations: auditoria de operações locais.
- generated: hashes para proteger edições humanas de arquivos gerados.
- settings: última indexação, erros e último backup verificado.

SQLite user_version=1. Índice reconstruível por index; auditoria histórica exige o backup do banco. [[KNOWLEDGE-SCHEMA]].
