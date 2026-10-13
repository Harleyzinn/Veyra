---
id: "14c298a2-9f3d-5ef4-a157-61225aed3bce"
title: "Knowledge Schema"
memory_type: "core"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py"]
---

# Nota v1

Campos: id (UUID), title, memory_type, verification, created_at, updated_at (ISO UTC), sources (lista não vazia), expires_at opcional. Estado: verified, hypothesis, approved_decision, ai_suggestion, outdated, conflicting, test_result ou unconfirmed.

Classes: core, working, episodic, semantic, procedural, decision, failure, archived. Fonte presente não equivale a evidência verdadeira; não promover hipóteses automaticamente. Cada propriedade deve ter valor JSON compatível com YAML: `title: "Título"`, `sources: ["repo:README.md"]`. YAML multilinha e chaves duplicadas são rejeitados nesta fase.

Templates em 20-TEMPLATES não são indexados; copie um, substitua metadados e acrescente fontes reais. [[MEMORY-POLICIES]].
