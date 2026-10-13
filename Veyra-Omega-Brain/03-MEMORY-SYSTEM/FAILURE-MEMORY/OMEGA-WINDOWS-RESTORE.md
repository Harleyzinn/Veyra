---
id: "e3e5ef0d-ce61-4bc8-8cee-ecdfba9985cb"
title: "Omega Windows Restore Lesson"
memory_type: "failure"
verification: "test_result"
created_at: "2026-10-13T00:33:54+00:00"
updated_at: "2026-10-13T00:33:54+00:00"
sources: ["repo:tools/omega/brain.py", "repo:tools/omega/verify_foundation.py", "repo:docs/omega/VALIDATION.md"]
---

# Falha de restauração por renomeação

## Problema e tentativas

O primeiro teste de restauração completo no workspace Windows/OneDrive recebeu Access denied ao renomear o diretório de staging. O mesmo cenário em fixtures temporárias havia passado. Isso não prova corrupção do SQLite ou uma falha em todas as instalações OneDrive.

## Solução observada

Validar o ZIP e banco no staging; criar um destino novo; copiar arquivos com criação exclusiva; comparar SHA-256; gravar RESTORE-VERIFIED.json somente ao concluir. Nunca alterar o Vault ativo. Cópia interrompida permanece sem marca de conclusão e deve ser revisada, não tratada como recuperação válida.

## Resultado e limites

Teste completo posterior restaurou 38 arquivos e conferiu todos os hashes, com integridade SQLite ok. A defesa evita depender do rename de diretório, mas não garante sucesso se faltar espaço/permissão. Antes de repetir uma estratégia de movimentação, conferir esta evidência e o ambiente real.

[[RECOVERY-PROCEDURES]] · [[ADR-001-LOCAL-FOUNDATION]]
