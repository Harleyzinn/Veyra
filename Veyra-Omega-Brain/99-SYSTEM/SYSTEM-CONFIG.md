---
id: "7992c072-6b61-55d6-8d16-9f37b369639f"
title: "System Config"
memory_type: "core"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py", "repo:scripts/omega.ps1"]
---

# Configuração

Vault: Veyra-Omega-Brain. Runtime: .omega/metadata.sqlite3 e .omega/backups, ignorados pelo Git. Python >=3.12; SQLite com FTS5. Sem dependências pip nesta fase. JSON compatível com YAML no frontmatter: strings/listas/números por propriedade, sem YAML arbitrário.

Não configurar um serviço escutando rede. Não cadastrar token no Vault. O init preserva arquivos existentes; dashboard/contexto só atualizam versões geradas cujo hash ainda corresponde ao banco.
