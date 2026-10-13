---
id: "65a978fb-2418-40d0-a3b1-85f58c20a2e9"
title: "ADR 001 — Fundação local"
memory_type: "decision"
verification: "verified"
created_at: "2026-10-13T00:33:54+00:00"
updated_at: "2026-10-13T00:33:54+00:00"
sources: ["user:omega-master-prompt:2026-10-12", "repo:tools/omega/brain.py", "repo:docs/omega/DISCOVERY.md", "repo:docs/omega/VALIDATION.md"]
---

# ADR 001 · Fundação local

## Contexto

Fase 1 autorizada para criar a infraestrutura real. Obsidian instalado, Python 3.12/SQLite FTS5 disponíveis, projeto no Windows/OneDrive. Não há necessidade imediata de serviço de rede ou LLM para indexar Markdown e restaurar conhecimento.

## Decisão implementada

Vault legível por humanos, SQLite/FTS5 derivado, CLI Python com biblioteca padrão, runtime fora do Vault. Frontmatter YAML com valores JSON por linha. Código e testes reais prevalecem sobre memória.

Esta nota verifica a escolha implementada; não afirma uma aprovação humana específica de cada detalhe arquitetural. Novas políticas críticas exigem autorização.

## Alternativas e consequências

- FastAPI/Pydantic: adiar até haver ponte e autenticação necessárias; evita servidor e instalação extra na fundação.
- Chroma/Qdrant: avaliar apenas com corpus/benchmarks; não acrescentar serviço vetorial agora.
- YAML arbitrário: adiado; subconjunto estrito melhora previsibilidade, mas edições multilinha precisam de normalização/revisão.
- Banco dentro do Vault: rejeitado nesta implementação; evita tratar índice como fonte humana e expor runtime pelo Git.

## Riscos e evidências

OneDrive não é um protocolo de concorrência SQLite. Operar um escritor por computador. Backups não são criptografados. O modo local usa permissões Windows, sem RBAC multiusuário. Verificações de recuperação, FTS e proteção contra sobrescrita estão no relatório de validação.

[[ARCHITECTURE-OVERVIEW]] · [[SECURITY-MODEL]] · [[RECOVERY-PROCEDURES]]
