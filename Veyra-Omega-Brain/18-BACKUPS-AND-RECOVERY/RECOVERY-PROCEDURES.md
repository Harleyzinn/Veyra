---
id: "c68dc73b-cb68-5e62-be50-93141c0051de"
title: "Recovery Procedures"
memory_type: "procedural"
verification: "verified"
created_at: "2026-10-13T00:29:21+00:00"
updated_at: "2026-10-13T00:29:21+00:00"
sources: ["repo:tools/omega/brain.py", "https://www.sqlite.org/backup.html"]
---

# Recuperação

1. Executar backup; verificar manifest SHA-256 e SQLite integrity_check.
2. Restore recebe o ZIP e um destino NOVO dentro de .omega. Nunca substitui o Vault vivo.
3. Comparar os hashes dos arquivos restaurados; abrir cópia isolada para revisão.
4. Para perda apenas do índice, guardar o banco antigo e executar init/index numa cópia do repositório.
5. Substituição do Vault vivo é operação manual com aprovação e backup prévio.

Backup inclui notas, configurações compatíveis e snapshot SQLite via API backup. Não é backup do código Veyra nem dos dados pessoais dos aplicativos. SHA detecta corrupção, não autentica um arquivo de origem desconhecida. Retenção automática/exclusão não implementada.
